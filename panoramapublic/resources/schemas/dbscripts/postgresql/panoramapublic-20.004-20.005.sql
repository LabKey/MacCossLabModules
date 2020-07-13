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


ALTER TABLE panoramapublic.JournalExperiment RENAME COLUMN journalExperimentId TO CopiedExperimentId;

ALTER TABLE panoramapublic.JournalExperiment ADD COLUMN ModifiedBy USERID;
ALTER TABLE panoramapublic.JournalExperiment ADD COLUMN Modified TIMESTAMP DEFAULT now();
UPDATE panoramapublic.JournalExperiment SET Modified =  Created;
UPDATE panoramapublic.JournalExperiment SET ModifiedBy = CreatedBy;

-- We want to add an auto-increment PK column on table journalexperiment. Simply adding the column does not generate values
-- in the desired order. Create a new table instead and add id values ordered by values in the created column.
-- https://stackoverflow.com/questions/53370072/add-auto-increment-column-to-existing-table-ordered-by-date
create sequence panoramapublic.journalexperiment_id_seq;
create table panoramapublic.journalexperiment_new as select nextval('panoramapublic.journalexperiment_id_seq') Id, *
       from panoramapublic.JournalExperiment order by Created;
DROP TABLE panoramapublic.JournalExperiment;
ALTER TABLE panoramapublic.journalexperiment_new RENAME TO JournalExperiment;
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT PK_JournalExperiment PRIMARY KEY (Id);
ALTER SEQUENCE panoramapublic.journalexperiment_id_seq OWNED BY panoramapublic.JournalExperiment.Id;
-- Re-create foreign keys and indexes
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT FK_JournalExperiment_Journal FOREIGN KEY (JournalId) REFERENCES panoramapublic.Journal(Id);
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT FK_JournalExperiment_ShortUrl_Access FOREIGN KEY (ShortAccessURL) REFERENCES core.ShortUrl(EntityId);
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT FK_JournalExperiment_ShortUrl_Copy FOREIGN KEY (ShortCopyURL) REFERENCES core.ShortUrl(EntityId);
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT FK_JournalExperiment_copiedExperimentId FOREIGN KEY (CopiedExperimentId) REFERENCES panoramapublic.ExperimentAnnotations(Id);
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT UQ_JournalExperiment_copiedExperimentId UNIQUE (CopiedExperimentId);
ALTER TABLE panoramapublic.JournalExperiment ADD CONSTRAINT UQ_JournalExperiment UNIQUE (JournalId, ExperimentAnnotationsId);
CREATE INDEX IX_JournalExperiment_ShortAccessURL ON panoramapublic.JournalExperiment(ShortAccessURL);
CREATE INDEX IX_JournalExperiment_ShortCopyURL ON panoramapublic.JournalExperiment(ShortCopyURL);


CREATE TABLE panoramapublic.pxxml
(
    _ts                   TIMESTAMP,
    Id                    SERIAL   NOT NULL,
    CreatedBy             USERID,
    Created               TIMESTAMP,
    ModifiedBy            USERID,
    Modified              TIMESTAMP,

    JournalExperimentId   INT NOT NULL,
    Xml                   TEXT NOT NULL,
    Version               SMALLINT NOT NULL,
    UpdateLog             TEXT,

    CONSTRAINT PK_PxXml PRIMARY KEY (Id),
    CONSTRAINT FK_PxXml_JournalExperiment FOREIGN KEY (JournalExperimentId) REFERENCES panoramapublic.JournalExperiment(Id)
);
CREATE INDEX IX_PxXml_JournalExperiment ON panoramapublic.pxxml(JournalExperimentId);

