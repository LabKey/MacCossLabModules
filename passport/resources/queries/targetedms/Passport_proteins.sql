SELECT
  peptidegroup.sequenceid.description AS name,
  accession,
  (SELECT count(*) FROM peptide where peptide.peptidegroupid = peptidegroup.id) AS peptides,
  peptidegroup.label,
  peptidegroup.preferredname,
  peptidegroup.gene,
  peptidegroup.species,
  peptidegroup.sequenceid.length AS length,
  runs.created,
  runs.id as runid
FROM peptidegroup, runs
WHERE runs.id = peptidegroup.runid
      AND peptidegroup.sequenceid IS NOT NULL
      and (select min(peptide.id) from peptide where peptide.peptidegroupid = peptidegroup.id and peptide.standardtype is not null) is null
      AND peptidegroup.sequenceid.description NOT LIKE 'Isoform%'
      AND peptidegroup.gene != 'APOA1'