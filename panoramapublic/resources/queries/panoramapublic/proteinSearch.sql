PARAMETERS
(
    proteinLabel VARCHAR
)

SELECT ex.created, count(rungroups.lsid) as matches, ex.title, ex.organism, ex.citation, ex.pxid
from experimentannotations ex
inner join exp.Runs rn on ex.experimentid.lsid = rn.rungroups.lsid
inner join targetedms.runs trn on trn.ExperimentRunLSID = rn.lsid
inner join targetedms.peptidegroup pg on trn.id = pg.runid
inner join targetedms.protein p on p.peptidegroupid = pg.id
where p.label like '%' || proteinLabel || '%'
group by ex.created, ex.title, ex.organism, ex.citation, ex.pxid