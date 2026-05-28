/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
PARAMETERS
(
    peptideSequence VARCHAR,
    exactMatch BIT default false
)

SELECT created,
       count(Sequence) as matches,
       title,
       organism,
       citation,
       pxid,
       peptideSequence AS peptideSequence,
       exactMatch,
       container
from peptideSearchResults
group by created, title, organism, citation, pxid, container