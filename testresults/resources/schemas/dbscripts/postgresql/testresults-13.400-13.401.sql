-- Dropping testrunid_unqiue [id] because it overlaps with pk_testruns [id]
ALTER TABLE testresults.testruns DROP CONSTRAINT testrunid_unqiue;
