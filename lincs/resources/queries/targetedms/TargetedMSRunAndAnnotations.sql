/*
 * Copyright (c) 2017 LabKey Corporation
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
SELECT
runs.*,
(SELECT GROUP_CONCAT(DISTINCT(Value), ',') AS Values FROM replicateAnnotation WHERE Name='cell_id' AND replicateId IN (SELECT Id FROM replicate where runId = runs.Id)) AS CellLine,
-- Added DetPlate column to enable sorting on plate
(SELECT GROUP_CONCAT(DISTINCT(Value), ',') AS Values FROM replicateAnnotation WHERE Name='det_plate' AND replicateId IN (SELECT Id FROM replicate where runId = runs.Id)) AS DetPlate,
metadata.Label,
metadata.Token
FROM runs
LEFT OUTER JOIN lincs.LincsMetadata AS metadata ON runs.FileName LIKE '%_' || metadata.Token || '_%';
WHERE runs.Status IS NULL

