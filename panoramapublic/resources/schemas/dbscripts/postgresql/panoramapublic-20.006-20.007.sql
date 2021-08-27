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


-- ALTER TABLE panoramapublic.JournalExperiment ADD COLUMN version INT;
-- ALTER TABLE panoramapublic.JournalExperiment DROP CONSTRAINT UQ_JournalExperiment;
-- ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT UQ_JournalExperiment UNIQUE (JournalId, ExperimentAnnotationsId, CopiedExperimentId);
-- ALTER TABLE panoramapublic.JournalExperiment DROP CONSTRAINT UQ_JournalExperiment_copiedExperimentId;
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
    Version                INT,

    CONSTRAINT PK_Submission PRIMARY KEY (Id),
    CONSTRAINT FK_Submission_JournalExperiment FOREIGN KEY (JournalExperimentId) REFERENCES panoramapublic.JournalExperiment(Id),
    CONSTRAINT FK_Submission_ExperimentAnnotations FOREIGN KEY (CopiedExperimentId) REFERENCES panoramapublic.ExperimentAnnotations(Id),
    CONSTRAINT FK_Submission_ShortUrl FOREIGN KEY (ShortAccessURL) REFERENCES core.ShortUrl(EntityId)
);

INSERT INTO panoramapublic.Submission (_ts, CreatedBy, Created, ModifiedBy, Modified,
                                       JournalExperimentId, CopiedExperimentId, Copied, PxIdRequested, KeepPrivate, IncompletePxSubmission,
                                       LabHeadName, LabHeadEmail, LabHeadAffiliation,
                                       DataLicense)
SELECT _ts, CreatedBy, Created, ModifiedBy, Modified,
        Id, CopiedExperimentId, Copied, PxIdRequested, KeepPrivate, IncompletePxSubmission,
        LabHeadName, LabHeadEmail, LabHeadAffiliation,
        DataLicense FROM panoramapublic.JournalExperiment;

UPDATE panoramapublic.Submission set Version = 1 WHERE CopiedExperimentId IS NOT NULL;

