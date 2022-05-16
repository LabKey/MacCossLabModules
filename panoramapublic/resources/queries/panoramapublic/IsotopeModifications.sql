SELECT mod.modId,
       mod.unimodId AS skylineUnimodId,
       mod.runIds,
       modinfo.Id as modInfoId,
       COALESCE(mod.unimodId, modInfo.unimodId) as unimodMatch
FROM
    (SELECT
         imod.Id AS modId,
         imod.unimodId AS unimodId,
         GROUP_CONCAT(DISTINCT pmod.PeptideId.PeptideGroupId.runId, ',') AS runIds
     FROM targetedms.PeptideIsotopeModification pmod
              INNER JOIN targetedms.IsotopeModification imod ON imod.id = pmod.isotopeModId
     GROUP BY imod.Id, imod.unimodId
    ) mod
LEFT OUTER JOIN panoramapublic.ExperimentIsotopeModInfo modinfo
ON mod.modId = modinfo.modId