ALTER TABLE testresults.testfails ADD language VARCHAR(10);
ALTER TABLE testresults.testpasses ADD duration INTEGER DEFAULT 0 NOT NULL;
CREATE INDEX testRunIdFails_idx ON testresults.testfails(testrunid);
CREATE INDEX testRunIdPasses_idx ON testresults.testpasses (testrunid); -- the big one
CREATE INDEX testRunsDay_idx ON testresults.testruns (day);
CREATE INDEX testRunsContainer_idx ON testresults.testruns (container);