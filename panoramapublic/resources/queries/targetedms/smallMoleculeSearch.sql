PARAMETERS
(
    smallMolecule VARCHAR,
    exactMatch BIT DEFAULT FALSE
)

SELECT created,
       count(smallMolecule) AS matches,
       title,
       organism,
       citation,
       pxid,
       smallMolecule,
       exactMatch,
       container
FROM smallMoleculeSearchResults
GROUP BY created, title, organism, citation, pxid, container