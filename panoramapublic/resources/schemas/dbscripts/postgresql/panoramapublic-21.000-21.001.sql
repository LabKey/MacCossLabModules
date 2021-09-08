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
CREATE TABLE panoramapublic.Submission
(
    _ts TIMESTAMP,
    CreatedBy              USERID,
    Created                TIMESTAMP,
    ModifiedBy             USERID,
    Modified               TIMESTAMP,

    Id                     SERIAL NOT NULL,
    JournalExperimentId    INT    NOT NULL,
    CopiedExperimentId     INT,
    Copied                 TIMESTAMP,
    PxIdRequested          boolean,
    KeepPrivate            boolean,
    IncompletePxSubmission boolean,
    LabHeadName            VARCHAR(100),
    LabHeadEmail           VARCHAR(100),
    LabHeadAffiliation     VARCHAR(200),
    DataLicense            VARCHAR(10),

    CONSTRAINT PK_Submission PRIMARY KEY (Id),
    CONSTRAINT FK_Submission_JournalExperiment FOREIGN KEY (JournalExperimentId) REFERENCES panoramapublic.JournalExperiment(Id),
    CONSTRAINT FK_Submission_ExperimentAnnotations FOREIGN KEY (CopiedExperimentId) REFERENCES panoramapublic.ExperimentAnnotations(Id)
);

INSERT INTO panoramapublic.Submission (_ts, CreatedBy, Created, ModifiedBy, Modified,
                                       JournalExperimentId, CopiedExperimentId, Copied,
                                       PxIdRequested, KeepPrivate, IncompletePxSubmission,
                                       LabHeadName, LabHeadEmail, LabHeadAffiliation,
                                       DataLicense)
SELECT _ts, CreatedBy, Created, ModifiedBy, Modified,
        Id, CopiedExperimentId, Copied,
        PxIdRequested, KeepPrivate, IncompletePxSubmission,
        LabHeadName, LabHeadEmail, LabHeadAffiliation,
        DataLicense FROM panoramapublic.JournalExperiment;

ALTER TABLE panoramapublic.JournalExperiment DROP COLUMN CopiedExperimentId, DROP COLUMN Copied,
                                             DROP COLUMN PxIdRequested, DROP COLUMN IncompletePxSubmission,
                                             DROP COLUMN KeepPrivate, DROP COLUMN LabHeadName,
                                             DROP COLUMN LabHeadEmail, DROP COLUMN LabHeadAffiliation,
                                             DROP COLUMN DataLicense;


-- Add a 'DataVersion' column to ExperimentAnnotations and set the value to 1 for each experiment that was copied to Panorama Public
ALTER TABLE panoramapublic.ExperimentAnnotations ADD COLUMN DataVersion INT;
UPDATE panoramapublic.ExperimentAnnotations set DataVersion = 1 WHERE SourceExperimentId IS NOT NULL;
-- Remove the 'JournalCopy' column since we can use the 'SourceExperimentId' column to determine if an experiment is a journal copy
ALTER TABLE panoramapublic.ExperimentAnnotations DROP COLUMN JournalCopy;

-- Add a column to save the userid of the assigned reviewer
ALTER TABLE panoramapublic.JournalExperiment ADD COLUMN Reviewer USERID;

-- Add indexes
CREATE INDEX IX_ExperimentAnnotations_Pxid ON panoramapublic.ExperimentAnnotations(pxid);
CREATE INDEX IX_ExperimentAnnotations_ShortUrl ON panoramapublic.ExperimentAnnotations(ShortUrl);
CREATE INDEX IX_JournalExperiment_Journal ON panoramapublic.JournalExperiment(JournalId);
CREATE INDEX IX_JournalExperiment_ExperimentAnnotations ON panoramapublic.JournalExperiment(ExperimentAnnotationsId);
CREATE INDEX IX_Submission_JournalExperiment ON panoramapublic.Submission(JournalExperimentId);
CREATE INDEX IX_Submission_ExperimentAnnotations ON panoramapublic.Submission(CopiedExperimentId);

