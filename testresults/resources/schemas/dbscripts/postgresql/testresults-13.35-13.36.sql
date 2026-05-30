/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
CREATE TABLE testresults.GlobalSettings (
  warningB INTEGER NOT NULL,
  errorB INTEGER NOT NULL
);

ALTER TABLE testresults.testruns ADD COLUMN medianmem INTEGER NOT NULL DEFAULT 0;
