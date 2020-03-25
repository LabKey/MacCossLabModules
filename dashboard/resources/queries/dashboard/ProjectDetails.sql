SELECT FolderDocs.Project,
SUM(FolderDocs.Documents) AS TotalDocs,
SUM((CASE WHEN FolderDocs.FolderType='Experiment' OR FolderDocs.FolderType IS NULL THEN FolderDocs.Documents ELSE 0 END)) AS ExpDocs,
SUM((CASE WHEN FolderDocs.FolderType='Library' OR FolderDocs.FolderType='LibraryProtein' THEN FolderDocs.Documents ELSE 0 END)) AS LibDocs,
SUM((CASE WHEN FolderDocs.FolderType='QC' THEN FolderDocs.Documents ELSE 0 END)) AS QcDocs,
MAX(FolderDocs.LastUpload) AS LastUpload
FROM FolderDocs GROUP BY FolderDocs.Project