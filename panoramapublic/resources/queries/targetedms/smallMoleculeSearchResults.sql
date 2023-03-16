PARAMETERS
(
    moleculeName VARCHAR,
    exactMatch BIT DEFAULT FALSE
)

SELECT trn.FileName,
       ex.Instrument,
       m.Id, -- Need this ID to successfully render the formatted peptide in ModifiedPeptideHtmlMarker //TODO is it required for small molecule
       m.PeptideGroupId.Label,
       ex.created,
       m.MoleculeName,
       m.NoteAnnotations,
       m.MassMonoisotopic,
       m.MassAverage,
       m.IonFormula,
       ex.title,
       ex.organism,
       ex.citation,
       ex.pxid,
       ex.Container @hidden,
       m.PeptideGroupId @hidden -- required for ModifiedSequenceDisplayColumn$PeptideDisplayColumnFactory to display peptide icon
FROM panoramapublic.experimentannotations ex
         INNER JOIN exp.Runs rn ON ex.experimentid.lsid = rn.rungroups.lsid
         INNER JOIN targetedms.runs trn ON trn.ExperimentRunLSID = rn.lsid
         INNER JOIN targetedms.molecule m ON m.peptidegroupid.runid = trn.id

WHERE (exactMatch = TRUE AND LOWER(m.MoleculeName) = LOWER(moleculeName)) OR
      (exactMatch = FALSE AND LOWER(m.MoleculeName) LIKE '%' || LOWER(moleculeName) || '%')