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

ALTER TABLE panoramapublic.SpecLibValidation ADD COLUMN SpecLibInfoId INT;
CREATE INDEX IX_SpecLibValidation_SpecLibInfoId ON panoramapublic.SpecLibValidation(SpecLibInfoId);

ALTER TABLE panoramapublic.SpecLibInfo ADD CONSTRAINT UQ_SpecLibInfo UNIQUE (ExperimentAnnotationsId, LibraryType, Name, FileNameHint, SkylineLibraryId, Revision);

CREATE INDEX IX_ModificationValidation_ModInfoId ON panoramapublic.ModificationValidation(ModInfoId);

