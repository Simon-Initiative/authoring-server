# Requires: 
# :packageId
# :packageGuid

# Query finds package guids for all previous versions related to a package "family",
# identified by the package id
select distinct guid, id, version
from content.package 
where id = :packageId 
order by date_created desc;