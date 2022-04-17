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

CREATE TABLE panoramapublic.IsotopeUnimodInfo
(
    _ts               TIMESTAMP,
    Id                SERIAL NOT NULL,
    CreatedBy         USERID,
    Created           TIMESTAMP,
    ModifiedBy        USERID,
    Modified          TIMESTAMP,

    ModInfoId                 INT NOT NULL,
    UnimodId                  INT NOT NULL,
    UnimodName                VARCHAR NOT NULL,

    CONSTRAINT PK_IsotopeUnimodInfo PRIMARY KEY (Id),
    CONSTRAINT FK_IsotopeUnimodInfo_ExperimentIsotopeModInfo FOREIGN KEY (ModInfoId) REFERENCES panoramapublic.ExperimentIsotopeModInfo(Id)
);
CREATE INDEX IX_IsotopeUnimodInfo_ModInfoId ON panoramapublic.IsotopeUnimodInfo(ModInfoId);
