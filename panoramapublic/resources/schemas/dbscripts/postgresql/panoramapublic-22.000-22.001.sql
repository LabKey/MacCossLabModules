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
CREATE TABLE panoramapublic.DataValidation
(
    _ts TIMESTAMP,
    CreatedBy              USERID,
    Created                TIMESTAMP,
    ModifiedBy             USERID,
    Modified               TIMESTAMP,

    Id                         SERIAL NOT NULL,
    ExperimentAnnotationsId    INT    NOT NULL,
    JobId                      INTEGER,
    Status                     INT,

    CONSTRAINT PK_DataValidation PRIMARY KEY (Id),
    CONSTRAINT FK_DataValidation_ExperimentAnnotations FOREIGN KEY (ExperimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id)
);
CREATE INDEX IX_DataValidation_ExperimentAnnotations ON panoramapublic.DataValidation(ExperimentAnnotationsId);
CREATE INDEX IX_DataValidation_JobId ON panoramapublic.DataValidation(JobId);


CREATE TABLE panoramapublic.SkylineDocValidation
(
    Id                         SERIAL NOT NULL,
    ValidationId               INT NOT NULL,
    RunId                      BIGINT NOT NULL, -- targetedms.runs.Id
    Container                  ENTITYID NOT NULL, -- Container where the run lives. This could be a subfolder of the main experiment folder
    Name                       VARCHAR(300) NOT NULL, -- Name of the Skyline file

    CONSTRAINT PK_SkylineDocValidation PRIMARY KEY (Id),
    CONSTRAINT FK_SkylineDocValidation_DataValidation FOREIGN KEY (ValidationId) REFERENCES panoramapublic.DataValidation(Id)
);
CREATE INDEX IX_SkylineDocValidation_ValidationId ON panoramapublic.SkylineDocValidation(ValidationId);
CREATE INDEX IX_SkylineDocValidation_Container ON panoramapublic.SkylineDocValidation(Container);
CREATE INDEX IX_SkylineDocValidation_RunId ON panoramapublic.SkylineDocValidation(runId);


CREATE TABLE panoramapublic.SkylineDocSampleFile
(
    Id                         SERIAL NOT NULL,
    SkylineDocValidationId     INT NOT NULL,
    Name                       VARCHAR(300) NOT NULL, -- file name
    FilePathImported           VARCHAR(500) NOT NULL, -- sample file path imported into the Skyline document
    Path                       TEXT, -- path of the file on the server if it was found

    CONSTRAINT PK_SkylineDocSampleFile PRIMARY KEY (Id),
    CONSTRAINT FK_SkylineDocSampleFile_SkylineDocValidation FOREIGN KEY (SkylineDocValidationId) REFERENCES panoramapublic.SkylineDocValidation(Id)
);
CREATE INDEX IX_SkylineDocSampleFile_SkylineDocValidationId ON panoramapublic.SkylineDocSampleFile(SkylineDocValidationId);


CREATE TABLE panoramapublic.ModificationValidation
(
    Id                         SERIAL NOT NULL,
    ValidationId               INT NOT NULL,
    SkylineModName             VARCHAR(100) NOT NULL,
    UnimodId                   INT,
    UnimodName                 VARCHAR(100),
    Inferred                   BOOLEAN NOT NULL, -- true if the Unimod Id for this modification was inferred
    ModType                    INT NOT NULL, -- Structural, Isotopic
    DbModId                    BIGINT NOT NULL, -- targetedms.StructuralModification.Id OR targetedms.IsotopicModification.Id
    UnimodMatches              TEXT, -- Potential Unimod matches if no single match was found

    CONSTRAINT PK_ModificationValidation PRIMARY KEY (Id),
    CONSTRAINT FK_ModificationValidation_DataValidation FOREIGN KEY (ValidationId) REFERENCES panoramapublic.DataValidation(Id)
);
CREATE INDEX IX_ModificationValidation_ValidationId ON panoramapublic.ModificationValidation(ValidationId);


CREATE TABLE panoramapublic.SkylineDocModification
(
    Id                         SERIAL NOT NULL,
    SkylineDocValidationId     INT NOT NULL,
    ModificationValidationId   INT NOT NULL,

    CONSTRAINT PK_SkylineDocModification PRIMARY KEY (Id),
    CONSTRAINT FK_SkylineDocModification_SkylineDocValidation FOREIGN KEY (SkylineDocValidationId) REFERENCES panoramapublic.SkylineDocValidation(Id),
    CONSTRAINT FK_SkylineDocModification_SpecLibValidation FOREIGN KEY (ModificationValidationId) REFERENCES panoramapublic.ModificationValidation(Id)
);
CREATE INDEX IX_SkylineDocModification_SkylineDocValidationId ON panoramapublic.SkylineDocModification(SkylineDocValidationId);
CREATE INDEX IX_SkylineDocModification_ModificationValidationId ON panoramapublic.SkylineDocModification(ModificationValidationId);


CREATE TABLE panoramapublic.SpecLibValidation
(
    Id                         SERIAL NOT NULL,
    ValidationId               INT NOT NULL,
    LibName                    VARCHAR(300) NOT NULL,
    FileName                   VARCHAR(300) NOT NULL,
    Size                       BIGINT,
    LibType                    VARCHAR(30) NOT NULL,

    CONSTRAINT PK_SpecLibValidation PRIMARY KEY (Id),
    CONSTRAINT FK_SpecLibValidation_DataValidation FOREIGN KEY (ValidationId) REFERENCES panoramapublic.DataValidation(Id)
);
CREATE INDEX IX_SpecLibValidation_ValidationId ON panoramapublic.SpecLibValidation(ValidationId);


CREATE TABLE panoramapublic.SpecLibSourceFile
(
    Id                         SERIAL NOT NULL,
    SpecLibValidationId        INT NOT NULL,
    Name                       VARCHAR(300) NOT NULL,
    SourceType                 INT NOT NULL,
    Path                       TEXT, -- Path of the file if it was found

    CONSTRAINT PK_SpecLibSourceFile PRIMARY KEY (Id),
    CONSTRAINT FK_SpecLibSourceFile_SpecLibValidation FOREIGN KEY (SpecLibValidationId) REFERENCES panoramapublic.SpecLibValidation(Id)
);
CREATE INDEX IX_SpecLibSourceFile_SpecLibValidationId ON panoramapublic.SpecLibSourceFile(SpecLibValidationId);


CREATE TABLE panoramapublic.SkylineDocSpecLib
(
    Id                         SERIAL NOT NULL,
    SkylineDocValidationId     INT NOT NULL,
    SpecLibValidationId        INT NOT NULL,
    Included                   BOOLEAN NOT NULL, -- true if the library is included in the .sky.zip
    SpectrumLibraryId          BIGINT NOT NULL, -- targetedms.SpectrumLibrary.Id

    CONSTRAINT PK_SkyDocSpecLib PRIMARY KEY (Id),
    CONSTRAINT FK_SkyDocSpecLib_SkylineDocValidation FOREIGN KEY (SkylineDocValidationId) REFERENCES panoramapublic.SkylineDocValidation(Id),
    CONSTRAINT FK_SkyDocSpecLib_SpecLibValidation FOREIGN KEY (SpecLibValidationId) REFERENCES panoramapublic.SpecLibValidation(Id)
);
CREATE INDEX IX_SkylineDocSpecLib_SkylineDocValidationId ON panoramapublic.SkylineDocSpecLib(SkylineDocValidationId);
CREATE INDEX IX_SkylineDocSpecLib_SpecLibValidationId ON panoramapublic.SkylineDocSpecLib(SpecLibValidationId);
CREATE INDEX IX_SkylineDocSpecLib_SpectrumLibraryId ON panoramapublic.SkylineDocSpecLib(SpectrumLibraryId);
