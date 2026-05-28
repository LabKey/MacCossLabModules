/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Dropping ix_datasetstatus_experimentannotations [ExperimentAnnotationsId] because it overlaps with uq_datasetstatus_experimentannotations [ExperimentAnnotationsId]
DROP INDEX panoramapublic.ix_datasetstatus_experimentannotations;
-- Dropping ix_experimentannotations_shorturl [ShortUrl] because it overlaps with uq_experimentannotations_shorturl [ShortUrl]
DROP INDEX panoramapublic.ix_experimentannotations_shorturl;
-- Dropping ix_speclibinfo_experimentannotations [experimentAnnotationsId] because it overlaps with uq_speclibinfo [experimentAnnotationsId, librarytype, name, filenamehint, skylinelibraryid, revision]
DROP INDEX panoramapublic.ix_speclibinfo_experimentannotations;
-- Dropping ix_catalogentry_shorturl [ShortUrl] because it overlaps with uq_catalogentry_shorturl [ShortUrl]
DROP INDEX panoramapublic.ix_catalogentry_shorturl;
-- Dropping ix_experimentstructuralmodinfo_experimentannotationsid [ExperimentAnnotationsId] because it overlaps with uq_experimentstructuralmodinfo [ExperimentAnnotationsId, ModId]
DROP INDEX panoramapublic.ix_experimentstructuralmodinfo_experimentannotationsid;
-- Dropping ix_journalexperiment_journal [JournalId] because it overlaps with uq_journalexperiment [JournalId, ExperimentAnnotationsId]
DROP INDEX panoramapublic.ix_journalexperiment_journal;
-- Dropping ix_experimentisotopemodinfo_experimentannotationsid [ExperimentAnnotationsId] because it overlaps with uq_experimentisotopemodinfo [ExperimentAnnotationsId, ModId]
DROP INDEX panoramapublic.ix_experimentisotopemodinfo_experimentannotationsid;
