# Requires: 
# :packageGuids

# Finds section guids and total student enrollment for a given packageGuid
select syllabus.section_guid, count(registration.guid) as students
from (
	select distinct organization.guid 
  from (
    select guid 
    from content.resource 
    where package_guid = :packageGuid
	) as resource_guids,
    content.item,
    content.organization
  where item.resource_guid = resource_guids.guid
    and organization.guid = item.organization_guid
) as organization_guids, 
  syllabus.syllabus, 
  course.section,
  course.registration
where syllabus.organization_guid = organization_guids.guid
  and section.guid = syllabus.section_guid
    -- Filter out 'open and free' courses
  and section.guest_section = 0
  and section.guid = registration.section_guid
  and registration.role = 'student'

group by syllabus.section_guid
-- Filter out test courses with low student numbers
having students > 10
order by students desc