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
                    SELECT s2.SeqId
                    FROM (
                             SELECT s1.SeqId, s1.seqName
                             FROM (
                                      SELECT SeqId, s.BestName as seqName
                                      FROM protein.sequences s
                                      WHERE s.BestName LIKE ('%' || proteinLabel || '%')

                                      UNION

                                      SELECT SeqId, a.AnnotVal as seqName
                                      FROM protein.Annotations a
                                      WHERE a.AnnotVal LIKE ('%' || proteinLabel || '%')

                                      UNION

                                      SELECT SeqId, fs.lookupstring as seqName
                                      FROM protein.FastaSequences fs
                                      WHERE fs.lookupstring LIKE ('%' || proteinLabel || '%')

                                      UNION

                                      SELECT SeqId, i.Identifier as seqName
                                      FROM protein.Identifiers i
                                      WHERE i.Identifier LIKE ('%' || proteinLabel || '%')
                                  ) s1
                             WHERE (exactMatch = FALSE) OR (exactMatch = TRUE AND LOWER(s1.seqName) = LOWER(proteinLabel))
                         ) s2
                )
            )
    )