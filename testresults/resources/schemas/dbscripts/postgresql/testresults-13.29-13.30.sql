ALTER TABLE testresults.user DROP COLUMN active;
ALTER TABLE testresults.userdata ADD COLUMN active BOOLEAN DEFAULT FALSE;
