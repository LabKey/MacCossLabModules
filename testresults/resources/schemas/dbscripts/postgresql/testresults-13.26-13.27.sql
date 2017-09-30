
-- CREATE A NEW USER TABLE AND REPLACE testruns (username) with FK testruns (userid) referencing new user table
CREATE TABLE testresults.user (
  id serial,
  username VARCHAR(100) UNIQUE NOT NULL,

  CONSTRAINT PK_user PRIMARY KEY (id)
);


INSERT INTO testresults.user (username) SELECT DISTINCT testruns.username FROM testresults.testruns;

ALTER TABLE testresults.testruns ADD userid int;
ALTER TABLE testresults.testruns ADD xml bytea;

UPDATE testresults.testruns SET userid=subquery.id
FROM
(SELECT id, username
      FROM  testresults.user) AS subquery
WHERE testruns.username=subquery.username;

ALTER TABLE testresults.testruns ALTER COLUMN userid SET NOT NULL;

ALTER TABLE testresults.testruns
ADD CONSTRAINT my_userFK
FOREIGN KEY (userid)
REFERENCES testresults.user (id);

ALTER TABLE testresults.testruns DROP COLUMN username;

ALTER TABLE testresults.testruns  ADD CONSTRAINT testrunid_unqiue UNIQUE (id);
-- TRAIN DATA TABLE
CREATE TABLE testresults.trainruns (
  id serial,
  runId int NOT NULL UNIQUE,

  CONSTRAINT PK_trainruns PRIMARY KEY (id),
  CONSTRAINT FK_trainruns_run FOREIGN KEY (runId) REFERENCES testresults.testruns(id)
);

-- TABLE WITH CALCULATED DATA FOR EACH USER/CONTAINER
CREATE TABLE testresults.userdata (
  id serial,
  userid int NOT NULL,
  container EntityId NOT NULL,
  meanTestsRun double precision DEFAULT 0.0,
  meanMemory double precision DEFAULT 0.0,
  stdDevTestsRun double precision DEFAULT  0.0,
  stdDevMemory double precision DEFAULT  0.0,

  CONSTRAINT PK_userdata PRIMARY KEY (id),
  CONSTRAINT FK_userdata_user FOREIGN KEY (userid) REFERENCES testresults.user(id)
);

