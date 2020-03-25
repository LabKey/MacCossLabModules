SELECT
COUNT(*) AS Num,
CAST(MONTH(ProjectAdmins.Created) AS INTEGER) AS Month,
CAST(YEAR(ProjectAdmins.Created) AS INTEGER) AS Year
FROM ProjectAdmins
WHERE ProjectAdmins.Name NOT IN (SELECT Name FROM IgnoreProjects)
GROUP BY MONTH(ProjectAdmins.Created), YEAR(ProjectAdmins.Created)
ORDER BY Year, Month