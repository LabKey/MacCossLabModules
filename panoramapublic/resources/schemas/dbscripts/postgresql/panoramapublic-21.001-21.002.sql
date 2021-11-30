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
CREATE TABLE panoramapublic.speclibinfo
(
    _ts               TIMESTAMP,
    Id                SERIAL NOT NULL,
    CreatedBy         USERID,
    Created           TIMESTAMP,
    ModifiedBy        USERID,
    Modified          TIMESTAMP,

    experimentAnnotationsId   INT NOT NULL,

    -- Columns from the targetedms.spectrumlibrary table. The same spectral library can be used in more than one
    -- document. We assume that libraries with identical values for these columns are the same library.
    -- The VARCHAR columns in the targetedms schema have a limit.  Here they are defined without a limit in case
    -- the column definitions for name, filenamehint and skylinelibraryid change in the targetedms schema.
    librarytype               VARCHAR(20)  NOT NULL,
    name                      VARCHAR NOT NULL, -- VARCHAR(400) NOT NULL,
    filenamehint              VARCHAR, -- VARCHAR(300),
    skylinelibraryid          VARCHAR, -- VARCHAR(200),
    revision                  VARCHAR(10),

    SourceType                INT NOT NULL,
    SourceUrl                 VARCHAR,
    SourceAccession           VARCHAR(100),
    SourceUsername            VARCHAR(100),
    SourcePassword            VARCHAR(100),
    DependencyType            INT NOT NULL,

    CONSTRAINT PK_SpecLibInfo PRIMARY KEY (Id),
    CONSTRAINT FK_SpecLibInfo_ExperimentAnnotations FOREIGN KEY (experimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id)
);
CREATE INDEX IX_SpecLibInfo_ExperimentAnnotations ON panoramapublic.SpecLibInfo(experimentAnnotationsId);

