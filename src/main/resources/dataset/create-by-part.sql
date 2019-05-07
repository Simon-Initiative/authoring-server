# byPart
# Requires :packageGuids, :sectionGuids
# Frontend expects camelcase properties

select
  # Resource / part info
	resources_and_parts.part_uid,
  resources_and_parts.part_id,
  resources_and_parts.resource_id,
  resources_and_parts.resource_title,
  resources_and_parts.revision,
  resources_and_parts.submit_and_compare,
  # Student enrollment
  sum(enrolled_students.distinct_students) as distinct_students,
  sum(enrolled_students.distinct_registrations) as distinct_registrations,
  # Student performance
  count(resources_and_parts.part_uid) as opportunities,
  sum(student_performance.practice) as practice,
  sum(student_performance.hints) as hints,
  sum(student_performance.errors) as errors,
  sum(student_performance.eventually_correct) as correct,
  sum(student_performance.first_response_correct) as first_response_correct

from

   # Assessment and part info, no grouping
  (select part.id as part_uid,
    resource.id as resource_id,
    resource.title as resource_title,
    question.question_id as question_id,
    question_resource.version as revision,
    part.part_id as part_id,
    if (question.submit_and_compare, 'true', 'false') as submit_and_compare
  from (
    select guid, id, title
    from content.resource
    where package_guid in (:packageGuids)
      and type in ('x-oli-inline-assessment', 'x-oli-assessment2', 'x-oli-assessment2-pool')
  ) as resource,
    assessment2.question_resource,
    assessment2.part,
    assessment2.question
  where resource.guid = question_resource.resource_guid
    and part.question_id = question_resource.question_id
    and part.question_id = question.id
  ) as resources_and_parts,

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

  # Student performance by part and section
  (select
    count(id) as practice,
    sum(hints) as hints,
    sum(errors) as errors,
    sum(correct) as eventually_correct,
    sum(case correct and errors = 0 when true then 1 else 0 end) as first_response_correct,
    sectionGuid,
    part_id
  from assessment2.PerformanceSummary
  where sectionGuid in (:sectionGuids)
  group by part_id, sectionGuid
  ) as student_performance

where enrolled_students.section_guid = student_performance.sectionGuid
  and student_performance.part_id = resources_and_parts.part_uid

# Collapse sections from student_performance and aggregate data by part
group by resources_and_parts.resource_id, resources_and_parts.question_id