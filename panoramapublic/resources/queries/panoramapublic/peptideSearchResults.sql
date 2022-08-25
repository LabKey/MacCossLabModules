PARAMETERS
(
    peptideSequence VARCHAR,
    exactMatch BIT default false,
    container VARCHAR
)

SELECT trn.FileName, ex.Instrument, p.ModifiedPeptideDisplayColumn, p.CalcNeutralMass, p.PeptideGroupId.Label, ex.Container @hidden
from experimentannotations ex
         inner join exp.Runs rn on ex.experimentid.lsid = rn.rungroups.lsid
         inner join targetedms.runs trn on trn.ExperimentRunLSID = rn.lsid
         inner join targetedms.peptidegroup pg on trn.id = pg.runid
         inner join targetedms.peptide p on p.peptidegroupid = pg.id
         inner join targetedms.protein pr on pr.peptidegroupid = pg.id
         inner join protein.sequences seq on pr.SequenceId = seq.SeqId
WHERE (
        (LOWER (p.Sequence) LIKE LOWER(peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) ))
        OR (
                seq.SeqId IN
                (
                    SELECT SeqId FROM protein.sequences s WHERE LOWER(s.BestName) LIKE LOWER(peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )
                    UNION
                    SELECT SeqId FROM protein.Annotations a WHERE LOWER(a.AnnotVal) LIKE LOWER(peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )
                    UNION
                    SELECT SeqId FROM protein.FastaSequences fs WHERE LOWER(fs.lookupstring) LIKE LOWER(peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )
                    UNION
                    SELECT SeqId FROM protein.Identifiers i WHERE LOWER(i.Identifier) LIKE LOWER(peptideSequence || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )
                )
            )
    )
  AND ex.container = container