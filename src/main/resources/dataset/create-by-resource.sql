# byResource
# Requires :packageGuids, :sectionGuids
# Frontend expects camelcase properties

select
  # Resource info
	resources.resource,
	resources.title,
  # Student enrollments
  sum(enrolled_students.distinct_students) as distinct_students,
  sum(enrolled_students.distinct_registrations) as distinct_registrations,
  # Student performance
  sum(resources.opportunities) as opportunities,
  sum(student_performance.practice) as practice,
  sum(student_performance.hints) as hints,
  sum(student_performance.errors) as errors,
  sum(student_performance.eventually_correct) as correct,
  sum(student_performance.first_response_correct) as first_response_correct

from 

  # Resource info
  (select 
    resource.id as resource,
    resource.title,
    count(part.id) as opportunities
  from (
    select guid, id, type, title
    from content.resource
    where package_guid in (:packageGuids)
      and type in ('x-oli-inline-assessment', 'x-oli-assessment2', 'x-oli-assessment2-pool')
  ) as resource,
    assessment2.question_resource,
    assessment2.part
  where resource.guid = question_resource.resource_guid
    and part.question_id = question_resource.question_id
  group by resource.guid
  ) as resources,

  # Student enrollment by section
  (select
    count(distinct user_guid) as distinct_students,
    count(distinct user_guid, section_guid) as distinct_registrations,
    section_guid
  from course.registration 
  where section_guid in (:sectionGuids)
    and role = 'student'
  group by section_guid
  ) as enrolled_students,

# Student performance by resource and section
 (select
    count(id) as practice,
    sum(hints) as hints,
    sum(errors) as errors,
    sum(correct) as eventually_correct,
    sum(case correct and errors = 0 when true then 1 else 0 end) as first_response_correct,
    count(distinct userGuid, sectionGuid) as distinct_doers,
    sectionGuid,
    resourceGuid
  from assessment2.PerformanceSummary
  where sectionGuid in (:sectionGuids)
  group by resourceGuid, sectionGuid
  ) as student_performance

where enrolled_students.section_guid = student_performance.sectionGuid
  and student_performance.resourceGuid = resources.resource

# Collapse sections from student_performance and aggregate data by resource
group by resources.resource;
