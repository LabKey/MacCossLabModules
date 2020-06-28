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

ALTER TABLE panoramapublic.journalexperiment ADD journalExperimentId INT;
ALTER TABLE panoramapublic.journalexperiment ADD CONSTRAINT FK_JournalExperiment_journalExperimentId FOREIGN KEY
    (journalExperimentId) REFERENCES panoramapublic.ExperimentAnnotations(Id);
CREATE INDEX IX_JournalExperiment_journalExperimentId ON panoramapublic.JournalExperiment(journalExperimentId);

UPDATE panoramapublic.journalexperiment SET journalExperimentId = (SELECT id FROM panoramapublic.experimentannotations ea
    WHERE ea.journalcopy = true AND ea.shorturl = shortaccessurl);





