/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
CREATE TABLE testresults.hangs (
    id serial,
    testrunid INTEGER NOT NULL,
    pass INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    testName VARCHAR(200) NOT NULL,
    language VARCHAR(50) NOT NULL,
    CONSTRAINT PK_hangs PRIMARY KEY (id),
    CONSTRAINT FK_hangs_testrunid FOREIGN KEY (testrunid) REFERENCES testresults.testruns(id)
);
