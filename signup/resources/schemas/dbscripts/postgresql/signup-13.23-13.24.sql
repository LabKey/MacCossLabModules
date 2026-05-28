/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
CREATE TABLE signup.movedusers (
  _ts TIMESTAMP DEFAULT now(),
  id SERIAL NOT NULL,
  labkeyUserId USERID NOT NULL,
  oldgroup USERID  NOT NULL,
  newgroup USERID NOT NULL,
  CONSTRAINT PK_movedusers PRIMARY KEY (id)
);
