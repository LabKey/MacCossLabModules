SELECT
peptidechrominfo.samplefileid.replicateid.runid.id AS RunId,
peptidechrominfo.samplefileid.replicateid.runid AS File,
peptidechrominfo.samplefileid.replicateid.id AS ReplicateId,
peptidechrominfo.PeptideId AS PeptideId,
pepAnnot.Value AS ProbeId,
peptidearearatio.AreaRatio AS AreaRatio,
peptidearearatio.IsotopeLabelId.Name AS IsotopeLabel,
peptidearearatio.IsotopeLabelStdId.Name AS IsotopeLabelStd
FROM
peptidechrominfo
LEFT JOIN PeptideAnnotation AS pepAnnot ON (peptidechrominfo.PeptideId = pepAnnot.PeptideId AND pepAnnot.Name='pr_id')
LEFT JOIN PeptideAreaRatio AS peptidearearatio
 ON (peptidechrominfo.Id = peptidearearatio.PeptideChrominfoId
 AND peptidearearatio.IsotopeLabelId.Name = 'light'
 AND peptidearearatio.IsotopeLabelStdId.Name = 'heavy')