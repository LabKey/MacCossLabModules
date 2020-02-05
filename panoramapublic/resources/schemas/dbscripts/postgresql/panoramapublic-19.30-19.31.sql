/*
 * Copyright (c) 2019 LabKey Corporation
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

ALTER TABLE panoramapublic.journalexperiment ADD labHeadName VARCHAR(100);
ALTER TABLE panoramapublic.journalexperiment ADD labHeadEmail VARCHAR(100);
ALTER TABLE panoramapublic.journalexperiment ADD labHeadAffiliation VARCHAR(200);
ALTER TABLE panoramapublic.journalexperiment ADD dataLicense VARCHAR (10);

-- Assume that everything submitted on or after 9/1/19 is already CC BY as we had started displaying the license text
-- below the submission form.
UPDATE panoramapublic.journalexperiment set datalicense='CC_BY_4.0' WHERE datalicense IS NULL AND created >= '2019-09-01';

