# bySkills
# Requires :modelId, :packageGuids, :sectionGuids
# Frontend expects camelcase properties

# bySkills
# Requires :modelId, :packageGuids, :sectionGuids
# Frontend expects camelcase properties

select
  # Skill info
	resources_and_skills.skill,
	resources_and_skills.title,
  # Student registrations
  sum(distinct enrolled_students.distinct_students) as distinct_students,
  sum(distinct enrolled_students.distinct_registrations) as distinct_registrations,
  # Student performance
  count(resources_and_skills.part_uid) as opportunities,
  sum(student_performance.practice) as practice,
  sum(student_performance.hints) as hints,
  sum(student_performance.errors) as errors,
  sum(student_performance.eventually_correct) as correct,
  sum(student_performance.first_response_correct) as first_response_correct

from 
  # Resources and skills. Tables are joined here rather than at the end for efficiency 
  (select * 
  from
    # Skills
    (select
      skill.id as skill,
      skill.title,
      skill_tag.resourceGuid,
      skill_tag.problemId,
      skill_tag.stepId
    from
      dashboard.SkillModel model,
      dashboard.Skill skill,
      dashboard.SkillTag skill_tag
    where
      model.id = (:modelId)
      and model.id = skill.model_id
      and skill.uid = skill_tag.skill_uid
    ) as skills,

    # Resources
    (select part.id as part_uid,
      part.part_id,
      resource.guid as resource_guid,
      resource.resource_title,
      question.question_id
    from (
      select guid, id, title as resource_title
      from content.resource
      where package_guid in (:packageGuids)
        and type in ('x-oli-inline-assessment', 'x-oli-assessment2', 'x-oli-assessment2-pool')
    ) as resource, 
      assessment2.question_resource, 
      assessment2.part, 
      assessment2.question
    where question_resource.resource_guid = resource.guid
      and part.question_id = question_resource.question_id
      and question.id = part.question_id
    ) as resources_and_parts

  where resources_and_parts.resource_guid = skills.resourceGuid
    and (skills.problemId is null or skills.problemId = resources_and_parts.question_id) 
    and (skills.stepId is null or skills.stepId = resources_and_parts.part_id)
  ) as resources_and_skills,

  (select
    count(distinct user_guid) as distinct_students,
    count(distinct user_guid, section_guid) as distinct_registrations,
    section_guid
	from course.registration 
	where section_guid in (:sectionGuids)
		and role = 'student'
  group by section_guid
  ) as enrolled_students,

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

where student_performance.part_id = resources_and_skills.part_uid
	and enrolled_students.section_guid = student_performance.sectionGuid

group by resources_and_skills.skill