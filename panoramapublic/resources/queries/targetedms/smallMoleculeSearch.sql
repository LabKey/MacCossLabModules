/*
 * Copyright (c) 2023-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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