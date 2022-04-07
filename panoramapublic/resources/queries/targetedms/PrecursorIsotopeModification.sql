SELECT
    pre.Id,
    pre.peptideId.peptideGroupId.runId,
    pre.peptideId,
    pre.peptideId.sequence,
    pre.ModifiedPrecursorDisplayColumn,
    pre.isotopeLabelId,
    rim.isotopemodId,
    pre.peptideId.decoy,
    pre.peptideId.standardType
FROM targetedms.precursor AS pre
INNER JOIN targetedms.RunIsotopeModification rim ON pre.isotopeLabelId = rim.isotopeLabelId