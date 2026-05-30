/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT mod.modId,
       mod.unimodId AS skylineUnimodId,
       mod.formula as normalizedFormula,
       mod.runIds,
       modinfo.Id as modInfoId,
       COALESCE(mod.unimodId, modInfo.unimodId) as unimodMatch
FROM
    (SELECT
         smod.Id AS modId,
         smod.unimodId AS unimodId,
         smod.formula,
         GROUP_CONCAT(DISTINCT pmod.PeptideId.PeptideGroupId.runId, ',') AS runIds
     FROM targetedms.PeptideStructuralModification pmod
              INNER JOIN targetedms.StructuralModification smod ON smod.id = pmod.structuralModId
     GROUP BY smod.Id, smod.unimodId, smod.formula
    ) mod
LEFT OUTER JOIN panoramapublic.ExperimentStructuralModInfo modinfo
ON mod.modId = modinfo.modId
