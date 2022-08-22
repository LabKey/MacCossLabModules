PARAMETERS
(
    proteinLabel VARCHAR
)
SELECT p.Label, p.Description, p.Accession, p.PreferredName, p.Gene
FROM targetedms.Protein p
WHERE p.Label = proteinLabel