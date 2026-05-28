/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
ALTER TABLE testresults.testpasses ADD timestamp TIMESTAMP;
ALTER TABLE testresults.testfails ADD timestamp TIMESTAMP;
ALTER TABLE testresults.testruns ADD timestamp TIMESTAMP;