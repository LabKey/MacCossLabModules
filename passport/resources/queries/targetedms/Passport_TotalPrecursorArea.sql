SELECT precursorchrominfo.PrecursorId.Id,
  precursorchrominfo.SampleFileId.ReplicateId.Name AS Replicate,
  precursorchrominfo.PrecursorId.PeptideId.PeptideGroupId.Label AS ProteinName,
  precursorchrominfo.PrecursorId.PeptideId.Sequence AS PeptideSequence,
  precursorchrominfo.PrecursorId.PeptideId.Id AS PeptideId,
  precursorchrominfo.PrecursorId.Id AS PrecursorId,
  precursorchrominfo.PrecursorId.PeptideId.StandardType,
  --precursorchrominfo.PrecursorId.Charge AS PrecursorCharge,
  --precursorchrominfo.PrecursorId.PeptideId.StartIndex AS BeginPos,
  --precursorchrominfo.PrecursorId.PeptideId.EndIndex AS EndPos,
  precursorchrominfo.TotalArea,
  precursorchrominfo.SampleFileId

FROM precursorchrominfo