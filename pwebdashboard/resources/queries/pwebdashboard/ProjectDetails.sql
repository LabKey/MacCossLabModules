SELECT FolderDocs.Project,
SUM(FolderDocs.Documents) AS TotalDocs,
SUM((CASE WHEN FolderDocs.TargetedMSFolderType='Experiment' OR FolderDocs.TargetedMSFolderType IS NULL THEN FolderDocs.Documents ELSE 0 END)) AS ExpDocs,
SUM((CASE WHEN FolderDocs.TargetedMSFolderType='Library' OR FolderDocs.TargetedMSFolderType='LibraryProtein' THEN FolderDocs.Documents ELSE 0 END)) AS LibDocs,
SUM((CASE WHEN FolderDocs.TargetedMSFolderType='QC' THEN FolderDocs.Documents ELSE 0 END)) AS QcDocs,
MAX(FolderDocs.LastUpload) AS LastUpload
FROM FolderDocs GROUP BY FolderDocs.Project