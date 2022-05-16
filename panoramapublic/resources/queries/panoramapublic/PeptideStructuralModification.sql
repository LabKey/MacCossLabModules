-- Query for viewing the modified peptide sequence along with the structural modification name, modified index and mass diff
SELECT
       pep.*,
       pepMod.structuralModId,
       pepMod.indexAA,
       pepMod.massDiff,
       pep.peptideGroupId,
       pep.peptideGroupId.runId,
FROM targetedms.peptide AS pep
INNER JOIN targetedms.PeptideStructuralModification pepMod ON pepMod.peptideId = pep.Id


