/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- ------------------------------------------------------------------------
-- Replace the ShortUrl column with an ExperimentAnnotationsId column.
-- ------------------------------------------------------------------------

-- Add ExperimentAnnotationsId column as nullable first
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN ExperimentAnnotationsId INT;

-- Populate the column by matching ShortUrl values
UPDATE panoramapublic.DatasetStatus
SET ExperimentAnnotationsId = ea.Id
FROM panoramapublic.ExperimentAnnotations ea
WHERE ea.ShortUrl = panoramapublic.DatasetStatus.ShortUrl;

-- Delete rows that couldn't be matched (where ExperimentAnnotationsId is still null)
DELETE FROM panoramapublic.DatasetStatus WHERE ExperimentAnnotationsId IS NULL;

-- Now make ExperimentAnnotationsId NOT NULL
ALTER TABLE panoramapublic.DatasetStatus ALTER COLUMN ExperimentAnnotationsId SET NOT NULL;

-- Add constraints and index
ALTER TABLE panoramapublic.DatasetStatus ADD CONSTRAINT FK_DatasetStatus_ExperimentAnnotations FOREIGN KEY (ExperimentAnnotationsId) REFERENCES panoramapublic.ExperimentAnnotations(Id);
ALTER TABLE panoramapublic.DatasetStatus ADD CONSTRAINT UQ_DatasetStatus_ExperimentAnnotations UNIQUE (ExperimentAnnotationsId);
CREATE INDEX IX_DatasetStatus_ExperimentAnnotations ON panoramapublic.DatasetStatus(ExperimentAnnotationsId);

-- Drop old constraints and index
ALTER TABLE panoramapublic.DatasetStatus DROP CONSTRAINT FK_DatasetStatus_ShortUrl;
ALTER TABLE panoramapublic.DatasetStatus DROP CONSTRAINT UQ_DatasetStatus_ShortUrl;
DROP INDEX panoramapublic.IX_DatasetStatus_ShortUrl;

-- Finally drop the ShortUrl column
ALTER TABLE panoramapublic.DatasetStatus DROP COLUMN ShortUrl;

