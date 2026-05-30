/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
ALTER TABLE testresults.user DROP COLUMN active;
ALTER TABLE testresults.userdata ADD COLUMN active BOOLEAN DEFAULT FALSE;
