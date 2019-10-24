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

-- Create schema, tables, indexes, and constraints used for PanoramaPublic module here
-- All SQL VIEW definitions should be created in panoramapublic-create.sql and dropped in panoramapublic-drop.sql
CREATE SCHEMA panoramapublic;
GO

-- ExperimentAnnotations table -------------------------------------------------------------
CREATE TABLE panoramapublic.ExperimentAnnotations
(
  -- standard fields
  _ts TIMESTAMP,
  Id INT IDENTITY(1, 1) NOT NULL,
  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  Title NVARCHAR(MAX),
  Organism NVARCHAR(100),
  ExperimentDescription NVARCHAR(MAX),
  SampleDescription NVARCHAR(MAX),
  Instrument NVARCHAR(250),
  SpikeIn BIT,
  Citation NVARCHAR(MAX),
  Abstract NVARCHAR(MAX),
  PublicationLink NVARCHAR(MAX),
  ExperimentId INT NOT NULL DEFAULT 0,
  JournalCopy BIT NOT NULL DEFAULT 0,
  IncludeSubfolders BIT NOT NULL DEFAULT 0,

  CONSTRAINT PK_ExperimentAnnotations PRIMARY KEY (Id)
);
CREATE INDEX IX_ExperimentAnnotations_Container ON panoramapublic.ExperimentAnnotations (Container);
CREATE INDEX IX_ExperimentAnnotations_ExperimentId ON panoramapublic.ExperimentAnnotations(ExperimentId);

ALTER TABLE panoramapublic.ExperimentAnnotations ADD CONSTRAINT FK_ExperimentAnnotations_Experiment FOREIGN KEY (ExperimentId) REFERENCES exp.Experiment(RowId);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD CONSTRAINT FK_ExperimentAnnotations_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId);

-- Journal table -------------------------------------------------------------
CREATE TABLE panoramapublic.Journal
(
  _ts TIMESTAMP,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  Id INT IDENTITY(1, 1) NOT NULL,
  Name NVARCHAR(255) NOT NULL,
  LabkeyGroupId INT NOT NULL,
  Project EntityId NOT NULL,

  CONSTRAINT PK_Journal PRIMARY KEY (Id),
  CONSTRAINT UQ_Journal_Name UNIQUE(Name),
  CONSTRAINT FK_Journal_Principals FOREIGN KEY (LabkeyGroupId) REFERENCES core.Principals(UserId),
  CONSTRAINT FK_Journal_Containers FOREIGN KEY (Project) REFERENCES core.Containers(EntityId)
);
CREATE INDEX IX_Journal_LabkeyGroupId ON panoramapublic.Journal(LabkeyGroupId);
CREATE INDEX IX_Journal_Project ON panoramapublic.Journal(Project);


ALTER TABLE panoramapublic.ExperimentAnnotations ADD sourceExperimentId INT;
ALTER TABLE panoramapublic.ExperimentAnnotations ADD sourceExperimentPath NVARCHAR(1000);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD shortUrl entityId;

CREATE INDEX IX_ExperimentAnnotations_SourceExperimentId ON panoramapublic.ExperimentAnnotations (sourceExperimentId);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD CONSTRAINT UQ_ExperimentAnnotations_ShortUrl UNIQUE (shortUrl);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD CONSTRAINT FK_ExperimentAnnotations_ShortUrl FOREIGN KEY (shorturl)
REFERENCES core.shorturl (entityId);


ALTER TABLE panoramapublic.ExperimentAnnotations ADD Keywords NVARCHAR(200);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD LabHead USERID;
ALTER TABLE panoramapublic.ExperimentAnnotations ADD LabHeadAffiliation NVARCHAR(200);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD Submitter USERID;
ALTER TABLE panoramapublic.ExperimentAnnotations ADD SubmitterAffiliation NVARCHAR(200);
ALTER TABLE panoramapublic.ExperimentAnnotations ADD pxid NVARCHAR(10);


ALTER TABLE panoramapublic.experimentannotations ALTER COLUMN Organism NVARCHAR(300);

-- JournalExperiment table -------------------------------------------------------------
CREATE TABLE panoramapublic.JournalExperiment
(
  _ts TIMESTAMP,
  CreatedBy USERID,
  Created DATETIME,

  JournalId INT NOT NULL,
  ExperimentAnnotationsId INT NOT NULL,
  ShortAccessURL EntityId NOT NULL,
  ShortCopyURL EntityId NOT NULL,
  Copied DATETIME,


  CONSTRAINT PK_JournalExperiment PRIMARY KEY (JournalId, ExperimentAnnotationsId),
  CONSTRAINT FK_JournalExperiment_Journal FOREIGN KEY (JournalId) REFERENCES panoramapublic.Journal(Id),
  CONSTRAINT FK_JournalExperiment_ExperimentAnnotations FOREIGN KEY (ExperimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id),
  CONSTRAINT FK_JournalExperiment_ShortUrl_Access FOREIGN KEY (ShortAccessURL) REFERENCES core.ShortUrl(EntityId),
  CONSTRAINT FK_JournalExperiment_ShortUrl_Copy FOREIGN KEY (ShortCopyURL) REFERENCES core.ShortUrl(EntityId)
);
CREATE INDEX IX_JournalExperiment_ShortAccessURL ON panoramapublic.JournalExperiment(ShortAccessURL);
CREATE INDEX IX_JournalExperiment_ShortCopyURL ON panoramapublic.JournalExperiment(ShortCopyURL);


ALTER TABLE panoramapublic.JournalExperiment ADD PxidRequested BIT NOT NULL DEFAULT '0';
ALTER TABLE panoramapublic.JournalExperiment ADD KeepPrivate BIT NOT NULL DEFAULT '1';