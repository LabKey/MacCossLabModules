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
       container @hidden
from peptideSearchResults
group by created, title, organism, citation, pxid, container