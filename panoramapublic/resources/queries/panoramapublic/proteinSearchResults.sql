PARAMETERS
(
    proteinLabel VARCHAR,
    exactMatch BIT default false
)

SELECT trn.FileName,
       ex.Instrument,
       p.Label,
       p.Description,
       p.Accession,
       p.PreferredName,
       p.Gene,
       ex.created,
       ex.title,
       ex.organism,
       ex.citation,
       ex.pxid,
       ex.Container @hidden
from experimentannotations ex
 inner join exp.Runs rn on ex.experimentid.lsid = rn.rungroups.lsid
 inner join targetedms.runs trn on trn.ExperimentRunLSID = rn.lsid
 inner join targetedms.peptidegroup pg on trn.id = pg.runid
 inner join targetedms.protein p on p.peptidegroupid = pg.id
 inner join protein.sequences seq on p.SequenceId = seq.SeqId
WHERE (
        (LOWER (p.Label) LIKE LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) ))
        OR (
                seq.SeqId IN
                (
                    SELECT SeqId
                    FROM protein.sequences s
                    WHERE LOWER(s.BestName) LIKE
                    LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )

                    UNION

                    SELECT SeqId
                    FROM protein.Annotations a
                    WHERE LOWER(a.AnnotVal) LIKE
                    LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )

                    UNION

                    SELECT SeqId
                    FROM protein.FastaSequences fs
                    WHERE LOWER(fs.lookupstring) LIKE
                    LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )

                    UNION
                    SELECT SeqId
                    FROM protein.Identifiers i
                    WHERE LOWER(i.Identifier)  LIKE
                    LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) )
                )
            )
    )