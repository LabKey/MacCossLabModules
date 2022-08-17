PARAMETERS
(
    proteinLabel VARCHAR,
    exactMatch BIT default false
)

SELECT ex.created, count(p.SequenceId) as matches, ex.title, ex.organism, ex.citation, ex.pxid
from experimentannotations ex
inner join exp.Runs rn on ex.experimentid.lsid = rn.rungroups.lsid
inner join targetedms.runs trn on trn.ExperimentRunLSID = rn.lsid
inner join targetedms.peptidegroup pg on trn.id = pg.runid
inner join targetedms.protein p on p.peptidegroupid = pg.id
inner join protein.sequences seq on p.SequenceId = seq.SeqId
where (((LOWER (p.Label) LIKE LOWER(proteinLabel)) and exactMatch = true)
OR ((LOWER (p.Label) LIKE LOWER(proteinLabel || '%' )) and exactMatch = false)
OR (seq.SeqId IN
   (SELECT SeqId FROM protein.sequences s WHERE LOWER(s.BestName) LIKE LOWER(proteinLabel)
    UNION
    SELECT SeqId FROM protein.Annotations a WHERE LOWER(a.AnnotVal) LIKE LOWER(proteinLabel)
    UNION
    SELECT SeqId FROM protein.FastaSequences fs WHERE LOWER(fs.lookupstring) LIKE LOWER(proteinLabel)
    UNION
    SELECT SeqId FROM protein.Identifiers i WHERE LOWER(i.Identifier) LIKE LOWER(proteinLabel)
    )
    AND exactMatch = true
    )
OR (seq.SeqId IN
    (SELECT SeqId FROM protein.sequences s WHERE LOWER(s.BestName) LIKE LOWER(proteinLabel || '%' )
     UNION
     SELECT SeqId FROM protein.Annotations a WHERE LOWER(a.AnnotVal) LIKE LOWER(proteinLabel || '%' )
     UNION
     SELECT SeqId FROM protein.FastaSequences fs WHERE LOWER(fs.lookupstring) LIKE LOWER(proteinLabel || '%' )
     UNION
     SELECT SeqId FROM protein.Identifiers i WHERE LOWER(i.Identifier) LIKE LOWER(proteinLabel || '%' )
    )
    AND exactMatch = false
       ))
group by ex.created, ex.title, ex.organism, ex.citation, ex.pxid