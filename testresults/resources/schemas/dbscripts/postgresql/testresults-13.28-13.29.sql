ALTER TABLE testresults.testruns ADD COLUMN pointsummary bytea;
ALTER TABLE testresults.testruns ADD COLUMN passedtests INTEGER;
ALTER TABLE testresults.testruns ADD COLUMN failedtests INTEGER;
ALTER TABLE testresults.testruns ADD COLUMN leakedtests INTEGER;
ALTER TABLE testresults.testruns ADD COLUMN averagemem INTEGER;
