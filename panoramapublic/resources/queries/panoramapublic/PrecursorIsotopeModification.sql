-- Query for viewing modified precursor sequence and the associated isotope modification name, modified index and mass diff
-- Precursors have an isotopeLabelId.  This is the database Id of the isotope label type (e.g. "heavy", "medium", etc.)
-- Each isotope label type can be associated with one or more isotope modification. This information is saved in the
-- RunIsotopeModification table.
-- The PeptideIsotopeModification table stores the modification Id and the massdiff for each modified index in the peptide.
-- We need to combine the information from these three tables.
 SELECT pre.*,
        pre.peptideId.sequence,
        pre.peptideId.decoy,
        pre.peptideId.standardType,
        pre.peptideId.peptideGroupId.runId,
        pim.isotopemodId,
        pim.indexAA,
        pim.massDiff
FROM targetedms.precursor AS pre
-- Join on RunIsotopeModification to get the ids of the modifications (isotopemodId) associated with the isotope label (isotopeLabelId).
-- There can be more than one isotope modification associated with an isotope label.
INNER JOIN targetedms.RunIsotopeModification rim ON pre.isotopeLabelId = rim.isotopeLabelId
INNER JOIN targetedms.PeptideIsotopeModification pim ON pim.peptideId = pre.peptideId AND pim.isotopeModId = rim.isotopeModId


