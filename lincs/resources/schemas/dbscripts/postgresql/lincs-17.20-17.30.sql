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

/* lincs-17.20-17.21.sql */

CREATE SCHEMA lincs;

CREATE TABLE lincs.LincsMetadata
(
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    FileName VARCHAR(300) NOT NULL,
    Token VARCHAR(100) NOT NULL,
    Label VARCHAR(200) NOT NULL,

    -- Primary key is required to show the "Edit" column and activate the "Delete" button in the grid.
    CONSTRAINT PK_LincsMetadata PRIMARY KEY (Token)
);