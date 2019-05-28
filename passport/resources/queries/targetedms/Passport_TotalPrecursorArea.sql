SELECT precursorchrominfo.PrecursorId.Id,
  precursorchrominfo.SampleFileId.ReplicateId.Name AS Replicate,
  precursorchrominfo.PrecursorId.PeptideId.PeptideGroupId.Label AS ProteinName,
  precursorchrominfo.PrecursorId.PeptideId.PeptideGroupId.SequenceId.SeqId AS seq,
  precursorchrominfo.PrecursorId.PeptideId.Sequence AS PeptideSequence,
  precursorchrominfo.PrecursorId.PeptideId.Id AS PeptideId,
  precursorchrominfo.PrecursorId.Id AS PrecursorId,
  precursorchrominfo.Id AS PanoramaPrecursorId,
  precursorchrominfo.PrecursorId.PeptideId.StandardType,
  precursorchrominfo.PrecursorId.PeptideId.StartIndex,
  precursorchrominfo.PrecursorId.PeptideId.EndIndex,
  --precursorchrominfo.PrecursorId.Charge AS PrecursorCharge,
  --precursorchrominfo.PrecursorId.PeptideId.StartIndex AS BeginPos,
  --precursorchrominfo.PrecursorId.PeptideId.EndIndex AS EndPos,
  precursorchrominfo.TotalArea,
  precursorchrominfo.SampleFileId,
  precursorchrominfo.PrecursorId.PeptideId.PeptideGroupId.id as PepGroupId,
  (SELECT SUM(pci.TotalArea) AS SumArea
   FROM precursorchrominfo AS pci
   WHERE pci.PrecursorId.PeptideId.StandardType='Normalization'
         AND pci.SampleFileId = precursorchrominfo.SampleFileId) AS SumArea
FROM precursorchrominfo
