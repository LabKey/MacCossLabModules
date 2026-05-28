/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Dropping testrunid_unqiue [id] because it overlaps with pk_testruns [id]
ALTER TABLE testresults.testruns DROP CONSTRAINT testrunid_unqiue;
