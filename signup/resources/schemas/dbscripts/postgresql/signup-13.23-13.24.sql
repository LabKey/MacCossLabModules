CREATE TABLE signup.movedusers (
  _ts TIMESTAMP DEFAULT now(),
  id SERIAL NOT NULL,
  labkeyUserId USERID NOT NULL,
  oldgroup USERID  NOT NULL,
  newgroup USERID NOT NULL,
  CONSTRAINT PK_movedusers PRIMARY KEY (id)
);
