/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
PARAMETERS
(
    proteinLabel VARCHAR,
    exactMatch BIT default false
)

SELECT created,
       count(proteinLabel) as matches,
       title,
       organism,
       citation,
       pxid,
       proteinLabel AS proteinLabel,
       exactMatch,
       container
from proteinSearchResults
group by created, title, organism, citation, pxid, container