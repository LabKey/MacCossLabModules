/*
 * Copyright (c) 2017 LabKey Corporation
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

CREATE TABLE lincs.LincsPspJob
(
    _ts TIMESTAMP,
    Id SERIAL NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,

    RunId INTEGER NOT NULL,
    PipelineJobId INTEGER,
    PspJobName VARCHAR(200),
    PspJobId VARCHAR(50),
    Status VARCHAR(100),
    Error VARCHAR(300),
    Progress SMALLINT NOT NULL DEFAULT 0,
    Json TEXT,

    CONSTRAINT PK_LincsPspJob PRIMARY KEY (Id)
);