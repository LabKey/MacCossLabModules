SELECT
CAST(MONTH(r.Created) AS INTEGER) AS Month,
CAST(YEAR(r.Created) AS INTEGER) AS Year,
COUNT(*) AS Documents,
SUM((CASE WHEN fd.FolderType='Experiment' OR fd.FolderType IS NULL THEN 1 ELSE 0 END)) AS ExpDocs,
SUM((CASE WHEN fd.FolderType='Library' OR fd.FolderType='LibraryProtein' THEN 1 ELSE 0 END)) AS LibDocs,
SUM(CASE WHEN fd.FolderType='QC' THEN 1 ELSE 0 END) AS QcDocs
FROM folderdocs fd
INNER JOIN targetedms.runs r ON r.container = fd.entityId
WHERE r.StatusId=1 AND r.Deleted=FALSE
AND fd.Project NOT IN (SELECT entityId FROM IgnoreProjects)
GROUP BY MONTH(r.Created), YEAR(r.Created)