CREATE TABLE testresults.GlobalSettings (
  warningB INTEGER NOT NULL,
  errorB INTEGER NOT NULL
);

ALTER TABLE testresults.testruns ADD medianmem INTEGER NOT NULL DEFAULT 0;
