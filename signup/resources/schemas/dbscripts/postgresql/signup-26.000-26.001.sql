/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- Room for the sha256: prefix plus a 64-character hash. NULL means the key was used to create an account.
ALTER TABLE signup.temporaryuser ALTER COLUMN key TYPE VARCHAR(100);
ALTER TABLE signup.temporaryuser ALTER COLUMN key DROP NOT NULL;
ALTER TABLE signup.temporaryuser ADD keyTimeout TIMESTAMP;

-- Rows with a LabKey userId already used their key to create the account.
UPDATE signup.temporaryuser SET key = NULL WHERE labkeyUserId IS NOT NULL;

-- Must match SignUpManager.hashKey(). Pending links expire seven days after upgrade.
UPDATE signup.temporaryuser
SET key = 'sha256:' || encode(sha256(convert_to(key, 'UTF8')), 'hex'),
    keyTimeout = LOCALTIMESTAMP + INTERVAL '7 days'
WHERE key IS NOT NULL;
