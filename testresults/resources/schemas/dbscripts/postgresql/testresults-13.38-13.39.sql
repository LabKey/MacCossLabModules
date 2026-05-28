/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
ALTER TABLE testresults.user DROP COLUMN IF EXISTS active;
ALTER TABLE testresults.userdata ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT FALSE;
