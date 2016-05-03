/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
PARAMETERS
(
    isotope VARCHAR DEFAULT 'light'
)

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
 AND peptidearearatio.IsotopeLabelId.Name = isotope
 AND peptidearearatio.IsotopeLabelStdId.Name = 'heavy')