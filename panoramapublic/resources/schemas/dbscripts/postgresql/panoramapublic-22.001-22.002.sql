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
CREATE TABLE panoramapublic.ExperimentStructuralModInfo
(
    _ts               TIMESTAMP,
    Id                SERIAL NOT NULL,
    CreatedBy         USERID,
    Created           TIMESTAMP,
    ModifiedBy        USERID,
    Modified          TIMESTAMP,

    ExperimentAnnotationsId   INT NOT NULL,
    ModId                     BIGINT NOT NULL, -- targetedms.structuralmodification.Id
    UnimodId                  INT NOT NULL,
    UnimodName                VARCHAR NOT NULL,
    UnimodId2                 INT,
    UnimodName2               VARCHAR,


    CONSTRAINT PK_ExperimentStructuralModInfo PRIMARY KEY (Id),
    CONSTRAINT FK_ExperimentStructuralModInfo_ExperimentAnnotations FOREIGN KEY (ExperimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id),
    CONSTRAINT FK_ExperimentStructuralModInfo_StructuralModification FOREIGN KEY (ModId) REFERENCES targetedms.StructuralModification(Id),
    CONSTRAINT UQ_ExperimentStructuralModInfo UNIQUE (ExperimentAnnotationsId, ModId)
);
CREATE INDEX IX_ExperimentStructuralModInfo_ExperimentAnnotationsId ON panoramapublic.ExperimentStructuralModInfo(experimentAnnotationsId);
CREATE INDEX IX_ExperimentStructuralModInfo_ModId ON panoramapublic.ExperimentStructuralModInfo(ModId);

CREATE TABLE panoramapublic.ExperimentIsotopeModInfo
(
    _ts               TIMESTAMP,
    Id                SERIAL NOT NULL,
    CreatedBy         USERID,
    Created           TIMESTAMP,
    ModifiedBy        USERID,
    Modified          TIMESTAMP,

    ExperimentAnnotationsId   INT NOT NULL,
    ModId                     BIGINT NOT NULL, -- targetedms.isotopemodification.Id
    UnimodId                  INT NOT NULL,
    UnimodName                VARCHAR NOT NULL,

    CONSTRAINT PK_ExperimentIsotopeModInfo PRIMARY KEY (Id),
    CONSTRAINT FK_ExperimentIsotopeModInfo_ExperimentAnnotations FOREIGN KEY (ExperimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id),
    CONSTRAINT FK_ExperimentIsotopeModInfo_IsotopeModification FOREIGN KEY (ModId) REFERENCES targetedms.IsotopeModification(Id),
    CONSTRAINT UQ_ExperimentIsotopeModInfo UNIQUE (ExperimentAnnotationsId, ModId)
);
CREATE INDEX IX_ExperimentIsotopeModInfo_ExperimentAnnotationsId ON panoramapublic.ExperimentIsotopeModInfo(experimentAnnotationsId);
CREATE INDEX IX_ExperimentIsotopeModInfo_ModId ON panoramapublic.ExperimentIsotopeModInfo(ModId);

-- We will no longer try to assign Unimod Ids automatically during data validation. We will rely instead on the user-assigned
-- values in the ExperimentStructuralModInfo and ExperimentIsotopeModInfo tables.
ALTER TABLE panoramapublic.ModificationValidation DROP COLUMN UnimodMatches;
-- ModInfoId refers to either panoramapublic.ExperimentStructuralModInfo.Id OR panoramapublic.ExperimentIsotopeModInfo.Id
-- depending on the value in ModType column
ALTER TABLE panoramapublic.ModificationValidation ADD COLUMN ModInfoId INT;
