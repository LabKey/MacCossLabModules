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
       container @hidden
from proteinSearchResults
group by created, title, organism, citation, pxid, container