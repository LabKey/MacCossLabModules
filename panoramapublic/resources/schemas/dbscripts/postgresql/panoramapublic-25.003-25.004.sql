
-- Add publication tracking columns to DatasetStatus
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PotentialPublicationId VARCHAR(255);
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PublicationType VARCHAR(50);
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN PublicationSearchStrategy TEXT;
ALTER TABLE panoramapublic.DatasetStatus ADD COLUMN UserDismissedPublication BOOLEAN DEFAULT FALSE;
