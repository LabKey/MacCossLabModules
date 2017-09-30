/* Set posttime to timestamp + duration for all runs where the original timestamp exists.  Really old runs don't have
a timestamp so we just ignore. */
UPDATE testresults.testruns tr
SET posttime = tr.timestamp + (tr.duration * interval '1 minute')
WHERE not(timestamp is null);

/* Add timestamps to old failures based on the failure's run date. */
UPDATE testresults.testfails
SET timestamp = (SELECT posttime
FROM testresults.testruns
WHERE id = testresults.testfails.testrunid)
WHERE timestamp is null;


