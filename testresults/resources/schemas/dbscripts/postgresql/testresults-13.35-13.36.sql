CREATE TABLE testresults.GlobalSettings (
  warningB INTEGER NOT NULL,
  errorB INTEGER NOT NULL
);

ALTER TABLE testresults.testruns ADD COLUMN medianmem INTEGER NOT NULL DEFAULT 0;
