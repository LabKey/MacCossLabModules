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
         inner join targetedms.protein p on p.peptidegroupid.runid = trn.id
         inner join protein.sequences seq on p.SequenceId = seq.SeqId
WHERE (
        (LOWER (p.Label) LIKE LOWER((CASE WHEN exactMatch = true THEN '' ELSE '%' END) || proteinLabel || (CASE WHEN exactMatch = true THEN '' ELSE '%' END) ))
        OR (
                p.SequenceId IN
                (
                    SELECT s2.SeqId
                    FROM (
                             SELECT s1.SeqId, s1.seqName
                             FROM (

                                     -- Note: there are special indexes on s.BestName, a.AnnotVal, fs.lookupstring, i.Identifier.
                                     -- These indexes were creating with 'varchar_pattern_ops' like so, ex: CREATE INDEX IX_ProtSequences_Identifier_VarcharPatternOps ON prot.Identifiers (lower(Identifier) varchar_pattern_ops);
                                     -- Adding these indexes were useful in allowing the query plan to use Index scan instead of Sequential scan which was time consuming
                                     -- These indexes are supposed to work when using pattern expressions using LIKE, however, they only work when using prefix like below, which is acceptable here.

                                      SELECT SeqId, s.BestName as seqName
                                      FROM protein.sequences s
                                      WHERE LOWER(s.BestName) LIKE (LOWER(proteinLabel) || '%')

                                      UNION

                                      SELECT SeqId, a.AnnotVal as seqName
                                      FROM protein.Annotations a
                                      WHERE LOWER(a.AnnotVal) LIKE (LOWER(proteinLabel) || '%')

                                      UNION

                                      SELECT SeqId, fs.lookupstring as seqName
                                      FROM protein.FastaSequences fs
                                      WHERE LOWER(fs.lookupstring) LIKE (LOWER(proteinLabel) || '%')

                                      UNION

                                      SELECT SeqId, i.Identifier as seqName
                                      FROM protein.Identifiers i
                                      WHERE LOWER(i.Identifier) LIKE (LOWER(proteinLabel) || '%')
                                  ) s1
                             WHERE (exactMatch = FALSE) OR (exactMatch = TRUE AND LOWER(s1.seqName) = LOWER(proteinLabel))
                         ) s2
                )
            )
    )