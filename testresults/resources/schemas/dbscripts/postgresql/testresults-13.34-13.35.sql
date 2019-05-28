ALTER TABLE testresults.testleaks RENAME TO memoryleaks;
ALTER TABLE testresults.memoryleaks ADD COLUMN type VARCHAR(200);

CREATE TABLE testresults.handleleaks (
  id serial,
  testrunid INTEGER NOT NULL,
  testName VARCHAR(200) NOT NULL,
  handles double precision DEFAULT 0.0,
  type VARCHAR(200),


  CONSTRAINT PK_memoryleaks PRIMARY KEY (id),
  CONSTRAINT FK_memoryleaks_testruns FOREIGN KEY (testrunid) REFERENCES testresults.testruns(id)
);

ALTER TABLE testresults.testpasses ADD COLUMN userandgdihandles INTEGER,
  ADD COLUMN committedmemory INTEGER,
  ADD COLUMN handles INTEGER;
