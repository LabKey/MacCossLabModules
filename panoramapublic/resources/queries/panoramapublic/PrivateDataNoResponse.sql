/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- The current version of private datasets that have been sent a reminder. The Stopped Reminders link on the
-- Private Data Reminder Settings page filters ReminderCount to list the datasets whose reminders have stopped.
SELECT ds.ExperimentAnnotationsId,
       ds.ExperimentAnnotationsId.Link AS Link,
       ds.ExperimentAnnotationsId.Submitter AS Submitter,
       ds.ExperimentAnnotationsId.LabHead AS LabHead,
       ds.ReminderCount,
       ds.LastReminderDate,
       ds.ExtensionRequestedDate,
       ds.PotentialPublicationId,
       ds.Citation,
       ds.LastPublicationSearchDate
FROM DatasetStatus ds
WHERE ds.ReminderCount > 0
  AND ds.DeletionRequestedDate IS NULL
  AND ds.ExperimentAnnotationsId.Public = 'No'
  AND ds.ExperimentAnnotationsId.Version = 'Current'
