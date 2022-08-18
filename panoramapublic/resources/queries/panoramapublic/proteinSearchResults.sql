PARAMETERS
(
    proteinId BIGINT
)
SELECT p.Label, p.Description, p.Accession, p.PreferredName, p.Gene
FROM targetedms.Protein p
WHERE p.Id = proteinId