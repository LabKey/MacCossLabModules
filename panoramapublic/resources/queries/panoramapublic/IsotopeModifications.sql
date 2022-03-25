SELECT mod.modId,
       mod.unimodId AS skylineUnimodId,
       mod.runIds,
       modinfo.Id as modInfoId,
       COALESCE(mod.unimodId, modInfo.unimodId) as unimodMatch
FROM
    (SELECT
         imod.Id AS modId,
         imod.unimodId AS unimodId,
         GROUP_CONCAT(DISTINCT run.id, ',') AS runIds,
     FROM targetedms.PeptideIsotopeModification pmod
              INNER JOIN targetedms.IsotopeModification imod ON imod.id = pmod.isotopeModId
              INNER JOIN targetedms.Peptide pep ON pep.id = pmod.peptideId
              INNER JOIN targetedms.PeptideGroup pg ON pg.id = pep.peptideGroupId
              INNER JOIN targetedms.Runs run ON run.id = pg.runId
     GROUP BY imod.Id, imod.unimodId
    ) mod
LEFT OUTER JOIN panoramapublic.ExperimentIsotopeModInfo modinfo
ON mod.modId = modinfo.modId