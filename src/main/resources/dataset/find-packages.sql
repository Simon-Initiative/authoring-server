# Query finds package guids for all previous versions of a package
# identified by the package id and version
select distinct guid, id, version
from content.package 
where id = :packageId and version <= :packageVersion
order by date_created desc;