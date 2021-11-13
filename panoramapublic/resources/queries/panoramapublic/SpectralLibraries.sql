SELECT lib.*,
       lib.SpecLibIds AS SkylineDocLibraries,
       lib.SpecLibId AS Details,
       libinfo.id AS specLibInfoId
FROM
(SELECT librarytype,
        name,
        filenamehint,
        skylinelibraryid,
        revision,
        GROUP_CONCAT(DISTINCT Id, ',') AS SpecLibIds, -- Other libraries, in other Skyline documents, with the same values
        MAX(Id) AS SpecLibId -- One spectral library that we can use as the example
 FROM targetedms.spectrumlibrary
 GROUP BY librarytype, name, filenamehint, skylinelibraryid, revision
) lib
LEFT OUTER JOIN panoramapublic.speclibinfo libInfo ON
libInfo.librarytype = lib.librarytype
AND libInfo.name = lib.name
AND ((libInfo.filenamehint IS NULL AND lib.filenamehint IS NULL) OR (libInfo.filenamehint = lib.filenamehint))
AND ((libinfo.skylinelibraryid IS NULL AND lib.skylinelibraryid IS NULL) OR libinfo.skylinelibraryid = lib.skylinelibraryid)
AND ((libinfo.revision IS NULL AND lib.revision IS NULL) OR libinfo.revision = lib.revision)