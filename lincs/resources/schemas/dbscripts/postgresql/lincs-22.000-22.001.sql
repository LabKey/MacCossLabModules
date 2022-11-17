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

-- Column names in the lincsmetadata table on PanoramaWeb and PanoramaWeb-DR do not match the metadata in lincs.xml.
-- Schema has lincsmetadata.file whereas in the metadata it is lincsmedata.FileName.
-- It is highly unlikely that the LINCS module in deployed on any other servers. But if it is, rename the column.
DO $$
BEGIN
  IF EXISTS(SELECT *
    FROM information_schema.columns
    WHERE table_name='lincsmetadata' and column_name='filename')
  THEN
    ALTER TABLE lincs.lincsmetadata RENAME COLUMN filename TO file;
END IF;
END $$;