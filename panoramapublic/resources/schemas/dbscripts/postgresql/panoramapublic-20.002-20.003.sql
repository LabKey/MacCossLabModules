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

-- JournalExperiment table has a row for each experiment submitted to Panorama Public.
-- Add a journalExperimentId column.  This will be blank when an experiment is just submmitted to Panorama Public
-- and will have a value after the experiment has been copied to Panorama Public.
ALTER TABLE panoramapublic.journalexperiment ADD journalExperimentId INT;
ALTER TABLE panoramapublic.journalexperiment ADD CONSTRAINT FK_JournalExperiment_journalExperimentId FOREIGN KEY
    (journalExperimentId) REFERENCES panoramapublic.ExperimentAnnotations(Id);
ALTER TABLE panoramapublic.journalexperiment ADD CONSTRAINT UQ_JournalExperiment_journalExperimentId UNIQUE(journalExperimentId);

-- Back fill the journalExperimentId column. The shortAccessUrl in the JournalExperiment row will be the same as the shortUrl
-- for an experiment in the ExperimentAnnotations table. Check that journalCopy is set to true for the experiment. This means
-- that the experiment is in a Panorama Public folder.
UPDATE panoramapublic.journalexperiment SET journalExperimentId = (SELECT id FROM panoramapublic.experimentannotations ea
    WHERE ea.journalcopy = true AND ea.shorturl = shortaccessurl);

-- Drop the foreign key constraint on the ExperimentAnnotationsId column that refers to ExperimentAnnotations.id column.
-- This is the id of an experiment in a user's folder that was submitted to Panorama Public. Remove the constraint so that
-- the user is able to delete the experiment in their folder, or even delete their folder without first deleting the row
-- in journalexperiment. We do not want to remove the row in JournalExperiment if the data has been copied to Panorama Public.
ALTER TABLE panoramapublic.journalexperiment DROP CONSTRAINT FK_JournalExperiment_ExperimentAnnotations;


