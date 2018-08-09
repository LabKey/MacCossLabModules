SELECT
  min(peptidegroup.sequenceid.description) AS name,
  min(accession) as accession,
  (SELECT count(*) FROM peptide where peptide.peptidegroupid = min(peptidegroup.id)) AS peptides,
  min(peptidegroup.label) as label,
  min(peptidegroup.preferredname) as PreferredName,
  min(peptidegroup.gene) as gene,
  min(peptidegroup.species) as species,
  min(peptidegroup.sequenceid.length) AS length,
  min(runs.created) as created,
  min(runs.id) as runid

FROM peptidegroup, runs
WHERE runs.id = peptidegroup.runid
      AND peptidegroup.sequenceid IS NOT NULL
      and (select min(peptide.id) from peptide where peptide.peptidegroupid = peptidegroup.id and peptide.standardtype is not null) is null
      AND peptidegroup.sequenceid.description NOT LIKE 'Isoform%'
      AND peptidegroup.gene != 'APOA1'
GROUP BY peptidegroup.sequenceid.seqid;