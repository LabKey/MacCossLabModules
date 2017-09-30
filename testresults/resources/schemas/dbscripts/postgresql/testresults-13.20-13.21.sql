/*
 * Copyright (c) 2011 LabKey Corporation
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

-- Create schema, tables, indexes, and constraints used for SignUp module here
-- All SQL VIEW definitions should be created in signup-create.sql and dropped in signup-drop.sql
CREATE SCHEMA testresults;

CREATE TABLE testresults.testruns (
  id serial,
  username VARCHAR(50) NOT NULL,
  duration INTEGER NOT NULL,
  day DATE NOT NULL,
  os VARCHAR(100) NOT NULL,
  revision INTEGER NOT NULL,

  CONSTRAINT PK_testruns PRIMARY KEY (id)
);

CREATE TABLE testresults.testfails (
  id serial,
  testrunid INTEGER NOT NULL,
  testname VARCHAR(200) NOT NULL,
  pass INTEGER NOT NULL,
  testId INTEGER NOT NULL,
  stackTrace TEXT,

  CONSTRAINT PK_testfails PRIMARY KEY (id),
  CONSTRAINT FK_testfails_testruns FOREIGN KEY (testrunid) REFERENCES testresults.testruns(id)
);

CREATE TABLE testresults.testpasses (
  id serial,
  testrunid INTEGER NOT NULL,
  testname VARCHAR(200) NOT NULL,
  pass INTEGER NOT NULL,
  testId INTEGER NOT NULL,
  language VARCHAR(50) NOT NULL,
  managedMemory REAL NOT NULL,
  totalMemory REAL NOT NULL,

  CONSTRAINT PK_testpasses PRIMARY KEY (id),
  CONSTRAINT FK_testpasses_testruns FOREIGN KEY (testrunid) REFERENCES testresults.testruns(id)
);

CREATE TABLE testresults.testleaks (
  id serial,
  testrunid INTEGER NOT NULL,
  testName VARCHAR(200) NOT NULL,
  bytes INTEGER NOT NULL,

  CONSTRAINT PK_testleaks PRIMARY KEY (id),
  CONSTRAINT FK_testleaks_testruns FOREIGN KEY (testrunid) REFERENCES testresults.testruns(id)
);

