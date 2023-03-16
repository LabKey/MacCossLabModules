PARAMETERS
(
    moleculeName VARCHAR,
    exactMatch BIT DEFAULT FALSE
)

SELECT created,
       count(MoleculeName) AS matches,
       title,
       organism,
       citation,
       pxid,
       MoleculeName,
       exactMatch,
       container
FROM smallMoleculeSearchResults
GROUP BY created, title, organism, citation, pxid, container