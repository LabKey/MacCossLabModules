PARAMETERS
(
    peptideSequence VARCHAR,
    exactMatch BIT default false
)

SELECT trn.FileName,
       ex.Instrument,
       p.ModifiedPeptideDisplayColumn,
       p.CalcNeutralMass,
       p.Id, -- Need this ID to successfully render the formatted peptide in ModifiedPeptideHtmlMarker
       p.PeptideGroupId.Label,
       ex.created,
       p.Sequence,
       ex.title,
       ex.organism,
       ex.citation,
       ex.pxid,
       ex.Container @hidden,
       p.PeptideGroupId @hidden -- required for ModifiedSequenceDisplayColumn$PeptideDisplayColumnFactory to display peptide icon
from experimentannotations ex
         inner join exp.Runs rn on ex.experimentid.lsid = rn.rungroups.lsid
         inner join targetedms.runs trn on trn.ExperimentRunLSID = rn.lsid
         inner join targetedms.peptidegroup pg on trn.id = pg.runid
         inner join targetedms.peptide p on p.peptidegroupid = pg.id
WHERE (LOWER (p.Sequence) LIKE LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) ))