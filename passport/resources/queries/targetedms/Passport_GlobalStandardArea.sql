SELECT SUM(pci.TotalArea) AS SumArea,
  pci.SampleFileId
FROM precursorchrominfo AS pci
WHERE pci.PrecursorId.PeptideId.StandardType='Normalization'
GROUP BY pci.SampleFileId