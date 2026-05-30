/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- Add publication tracking columns to DatasetStatus
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PotentialPublicationId VARCHAR(255);
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PublicationType VARCHAR(50);
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PublicationMatchInfo TEXT;
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN Citation TEXT;
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN UserDismissedPublication TIMESTAMP;
