ALTER TABLE testresults.testruns
ALTER column day type timestamp without time zone;
ALTER TABLE testresults.testruns
RENAME COLUMN day TO posttime;