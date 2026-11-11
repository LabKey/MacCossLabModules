/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;
import org.labkey.panoramapublic.model.DatasetStatus;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.ncbi.NcbiApiKeyCheck;
import org.labkey.panoramapublic.ncbi.NcbiConstants;
import org.labkey.panoramapublic.ncbi.NcbiPublicationSearchService;
import org.labkey.panoramapublic.ncbi.NcbiSearchException;
import org.labkey.panoramapublic.ncbi.PublicationMatch;
import org.labkey.panoramapublic.query.DatasetStatusManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrivateDataReminderJob extends PipelineJob
{
    private static final int MIN_DATASETS_FOR_FAILURE = 3;
    private static final double PUBLICATION_SEARCH_FAILURE_THRESHOLD = 0.5;
    // Consecutive datasets with all NCBI requests failed before the search is stopped for the run.
    private static final int MAX_CONSECUTIVE_ALL_FAILED = 3;

    /**
     * @return true when the publication search failure rate reaches the threshold, which suggests a
     * problem with searching NCBI rather than with one dataset.
     */
    static boolean publicationSearchFailingWidely(int failed, int attempted)
    {
        return attempted >= MIN_DATASETS_FOR_FAILURE
                && failed >= attempted * PUBLICATION_SEARCH_FAILURE_THRESHOLD;
    }

    private boolean _test;
    private boolean _forcePublicationCheck;
    private boolean _ignoreReminderLimit;
    private List<Integer> _experimentAnnotationsIds;
    private Journal _panoramaPublic;

    protected PrivateDataReminderJob()
    {
    }

    public PrivateDataReminderJob(ViewBackgroundInfo info, @NotNull PipeRoot root, boolean test)
    {
        this(info, root, getPanoramaPublic(), getPrivateDatasets(getPanoramaPublic()), test);
    }

    public PrivateDataReminderJob(ViewBackgroundInfo info, @NotNull PipeRoot root, Journal panoramaPublic, List<Integer> experimentAnnotationsIds, boolean test)
    {
        this(info, root, panoramaPublic, experimentAnnotationsIds, test, false);
    }

    public PrivateDataReminderJob(ViewBackgroundInfo info, @NotNull PipeRoot root, Journal panoramaPublic, List<Integer> experimentAnnotationsIds, boolean test, boolean forcePublicationCheck)
    {
        this(info, root, panoramaPublic, experimentAnnotationsIds, test, forcePublicationCheck, false);
    }

    /**
     * @param ignoreReminderLimit post a reminder that is otherwise due even if stopRemindersAfter reminders have been
     *                            posted without a response
     */
    public PrivateDataReminderJob(ViewBackgroundInfo info, @NotNull PipeRoot root, Journal panoramaPublic, List<Integer> experimentAnnotationsIds,
                                  boolean test, boolean forcePublicationCheck, boolean ignoreReminderLimit)
    {
        super("Panorama Public", info, root);
        setLogFile(root.getRootFileLike().resolveChild(FileUtil.makeFileNameWithTimestamp("PanoramaPublic-private-data-reminder", "log")));
        _panoramaPublic = panoramaPublic;

        _experimentAnnotationsIds = experimentAnnotationsIds;
        _test = test;
        _forcePublicationCheck = forcePublicationCheck;
        _ignoreReminderLimit = ignoreReminderLimit;
    }

    private static Journal getPanoramaPublic()
    {
        return JournalManager.getJournal(JournalManager.PANORAMA_PUBLIC);
    }

    public static List<Integer> getPrivateDatasets(Journal panoramaPublic)
    {
        if (panoramaPublic == null) return Collections.emptyList();

        Set<Container> subFolders = ContainerManager.getAllChildren(panoramaPublic.getProject());
        List<Integer> privateDataIds = new ArrayList<>();
        for (Container folder : subFolders)
        {
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(folder);
            if (exptAnnotations != null && !exptAnnotations.isPublic())
            {
                privateDataIds.add(exptAnnotations.getId());
            }
        }

        return privateDataIds;
    }

    /**
     * @return a skip decision for a dataset that is neither searched for a publication nor sent a message,
     * or null if the dataset is processed
     */
    private static @Nullable ReminderDecision getExclusion(@NotNull ExperimentAnnotations exptAnnotations, @Nullable DatasetStatus datasetStatus)
    {
        if (exptAnnotations.isPublic())
        {
            return ReminderDecision.skip("Data is already public");
        }

        if (!ExperimentAnnotationsManager.isCurrentVersion(exptAnnotations))
        {
            return ReminderDecision.skip("Not the current version of the experiment");
        }

        // Make sure the data does not have a pending re-submission request
        JournalSubmission journalSubmission = SubmissionManager.getSubmissionForJournalCopy(exptAnnotations);
        if (journalSubmission != null && journalSubmission.hasPendingSubmission())
        {
            return ReminderDecision.skip("Data has a pending re-submission request");
        }

        if (datasetStatus != null && datasetStatus.deletionRequested())
        {
            return ReminderDecision.skip("Submitter has requested deletion");
        }
        return null;
    }

    /**
     * Decides whether a reminder is posted. The publication search does not depend on this decision.
     */
    private static ReminderDecision getReminderDecision(@NotNull ExperimentAnnotations exptAnnotations, @Nullable DatasetStatus datasetStatus,
                                                        @NotNull PrivateDataReminderSettings settings, boolean ignoreReminderLimit)
    {
        if (datasetStatus != null)
        {
            if (settings.isExtensionValid(datasetStatus))
            {
                return ReminderDecision.skip("Submitter requested an extension. Extension is current");
            }

            if (!ignoreReminderLimit && settings.areRemindersStopped(datasetStatus))
            {
                return ReminderDecision.remindersStopped(String.format("Reminders stopped after %d reminders. The submitter has not responded",
                        datasetStatus.getReminderCount()));
            }

            if (settings.isLastReminderRecent(datasetStatus))
            {
                return ReminderDecision.skip("Recent reminder already sent");
            }
        }
        return reminderIsDue(exptAnnotations, settings);
    }

    private static ReminderDecision reminderIsDue(ExperimentAnnotations exptAnnotations, PrivateDataReminderSettings settings)
    {
        LocalDate copyDate = exptAnnotations.getCreated().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate firstReminderDate = copyDate.plusMonths(settings.getDelayUntilFirstReminder());
        if (LocalDate.now().isBefore(firstReminderDate))
        {
            return ReminderDecision.skip(String.format("First reminder not due until %s",
                    firstReminderDate.format(DateTimeFormatter.ofPattern(PrivateDataReminderSettings.DATE_FORMAT_PATTERN))));
        }
        return ReminderDecision.post();
    }

    public static class ReminderDecision {
        private final boolean shouldPost;
        private final boolean remindersStopped;
        private final String reason;

        private ReminderDecision(boolean shouldPost, boolean remindersStopped, String reason) {
            this.shouldPost = shouldPost;
            this.remindersStopped = remindersStopped;
            this.reason = reason;
        }

        public static ReminderDecision post() {
            return new ReminderDecision(true, false, null);
        }

        public static ReminderDecision skip(String reason) {
            return new ReminderDecision(false, false, reason);
        }

        public static ReminderDecision remindersStopped(String reason) {
            return new ReminderDecision(false, true, reason);
        }

        public boolean shouldPost() { return shouldPost; }
        public boolean remindersStopped() { return remindersStopped; }
        public String getReason() { return reason; }
    }

    private enum MessageType
    {
        REMINDER,
        PUBLICATION_FOUND // A new publication was found for a dataset that is not due a reminder
    }

    private static class PublicationSearchOutcome
    {
        private static final PublicationSearchOutcome NONE = new PublicationSearchOutcome(null, false);

        private final @Nullable PublicationMatch _match;
        private final boolean _searchCompleted;

        private PublicationSearchOutcome(@Nullable PublicationMatch match, boolean searchCompleted)
        {
            _match = match;
            _searchCompleted = searchCompleted;
        }

        /** @return the publication to report, from a search or from the match saved on DatasetStatus */
        public @Nullable PublicationMatch getMatch() { return _match; }

        /** @return true if NCBI was searched and the search completed */
        public boolean isSearchCompleted() { return _searchCompleted; }
    }

    /**
     * Finds the publication to report for the experiment, from the dataset's cached match or by
     * searching NCBI.
     *
     * A dataset with no cached match, or a cached match on author or title only, is searched once every
     * publicationSearchFrequency months. A dataset whose match the submitter dismissed is first searched
     * publicationSearchFrequency months after the dismissal. A run with forceCheck set searches regardless of the last
     * search date, but not within publicationSearchFrequency months of a dismissal.
     *
     * @return an outcome with no match when there is nothing to report, which includes a search that
     * found only the publication the submitter has already dismissed
     * @throws NcbiSearchException if a search ran and could not complete
     */
    private PublicationSearchOutcome searchForPublication(@NotNull ExperimentAnnotations expAnnotations,
                                                         @Nullable DatasetStatus datasetStatus,
                                                         @NotNull PrivateDataReminderSettings settings,
                                                         boolean forceCheck,
                                                         @NotNull ProcessingResults results)
    {
        Logger log = results._log;

        // Check if publication checking is enabled (either globally or forced for this run)
        if (!forceCheck && !settings.isEnablePublicationSearch())
        {
            log.debug("Publication checking is disabled in settings");
            return PublicationSearchOutcome.NONE;
        }

        PublicationMatch cachedMatch = null;
        if (datasetStatus != null)
        {
            // If user has dismissed the publication suggestion, check search delay
            Date dismissedDate = datasetStatus.getUserDismissedPublication();
            if (dismissedDate != null)
            {
                if (settings.isPublicationDismissalRecent(datasetStatus))
                {
                    log.info("User dismissed publication for experiment {} on {}; Publication search deferred ({} months)", expAnnotations.getId(), dismissedDate, settings.getPublicationSearchFrequency());
                    return PublicationSearchOutcome.NONE;
                }

                if (!forceCheck && !settings.isPublicationSearchDue(datasetStatus))
                {
                    log.info("Experiment {} was searched for publications on {}; next search after {} months",
                            expAnnotations.getId(), datasetStatus.getLastPublicationSearchDate(), settings.getPublicationSearchFrequency());
                    return PublicationSearchOutcome.NONE;
                }

                if (results.isPublicationSearchStopped())
                {
                    // LastPublicationSearchDate is not set, so the next run searches again.
                    log.info("Publication search is stopped for this run. Not re-searching for experiment {}", expAnnotations.getId());
                    results.addPublicationSearchSkipped(expAnnotations.getId());
                    return PublicationSearchOutcome.NONE;
                }

                // Search deferral expired — re-search NCBI
                log.info("Search deferral expired for experiment {} (dismissed {}); re-searching NCBI", expAnnotations.getId(), dismissedDate);
                results.addPublicationSearchAttempted();
                PublicationMatch newMatch = NcbiPublicationSearchService.get().searchForPublication(expAnnotations, log);
                results.addPublicationSearchCompleted();
                if (newMatch != null && !newMatch.getPublicationId().equals(datasetStatus.getPotentialPublicationId()))
                {
                    // Different publication found — return it (caller will save and notify)
                    log.info("New publication {} found for experiment {} (previously dismissed {})", newMatch.getPublicationId(), expAnnotations.getId(), datasetStatus.getPotentialPublicationId());
                    return new PublicationSearchOutcome(newMatch, true);
                }
                log.info("No new publication for experiment {}", expAnnotations.getId());
                return new PublicationSearchOutcome(null, true);
            }

            cachedMatch = PublicationMatch.fromDatasetStatus(datasetStatus);
            // A cached match that cites the dataset's identifiers is final. A match on author or title is searched
            // again, in case a paper that cites the identifiers is published later.
            if (cachedMatch != null && cachedMatch.matchesDataIdentifier())
            {
                log.info("Using cached publication {} for experiment {}", cachedMatch.getPublicationIdLabel(), expAnnotations.getId());
                return new PublicationSearchOutcome(cachedMatch, false);
            }

            if (!forceCheck && !settings.isPublicationSearchDue(datasetStatus))
            {
                log.info("Experiment {} was searched for publications on {}; next search after {} months",
                        expAnnotations.getId(), datasetStatus.getLastPublicationSearchDate(), settings.getPublicationSearchFrequency());
                return cachedMatch != null ? new PublicationSearchOutcome(cachedMatch, false) : PublicationSearchOutcome.NONE;
            }
        }
        PublicationSearchOutcome cachedOutcome = cachedMatch != null ? new PublicationSearchOutcome(cachedMatch, false) : PublicationSearchOutcome.NONE;

        if (results.isPublicationSearchStopped())
        {
            log.info("Publication search is stopped for this run. Not searching for experiment {}", expAnnotations.getId());
            results.addPublicationSearchSkipped(expAnnotations.getId());
            return cachedOutcome;
        }

        // Perform the publication search
        log.info("Searching for publications for experiment {}", expAnnotations.getId());
        results.addPublicationSearchAttempted();
        PublicationMatch match = NcbiPublicationSearchService.get().searchForPublication(expAnnotations, log);
        results.addPublicationSearchCompleted();
        if (cachedMatch != null && (match == null || !match.matchesDataIdentifier()))
        {
            // Only a match on the dataset's identifiers replaces a cached match on author or title.
            return new PublicationSearchOutcome(cachedMatch, true);
        }
        return new PublicationSearchOutcome(match, true);
    }

    @Override
    public void run()
    {
        setStatus(TaskStatus.running);

        if (_panoramaPublic == null)
        {
            getLogger().error("Panorama Public project does not exist.");
            setStatus(TaskStatus.error);
            return;
        }

        setStatus(postMessage(_experimentAnnotationsIds, _panoramaPublic));
    }

    /**
     * Check a configured NCBI API key before any dataset is touched. A key NCBI rejects would fail the
     * publication search for every dataset, so the job stops with nothing posted. The job goes ahead
     * when the check could not be completed, since that says nothing about the key.
     */
    private boolean ncbiApiKeyAccepted()
    {
        PrivateDataReminderSettings settings = PrivateDataReminderSettings.get();
        if (!settings.isEnablePublicationSearch() && !_forcePublicationCheck)
        {
            return true;
        }

        String apiKey = settings.getNcbiApiKey();
        if (StringUtils.isBlank(apiKey))
        {
            // Searches run without a key, at NCBI's lower request rate.
            return true;
        }

        NcbiApiKeyCheck check = NcbiPublicationSearchService.get().checkApiKey(apiKey, getLogger());
        if (check.isRejected())
        {
            getLogger().error("NCBI rejected the API key, so no reminders were posted. Correct the key on the Private Data Reminder Settings page and run the job again. {}",
                    check.getMessage());
            return false;
        }

        if (!check.isValid())
        {
            getLogger().warn("Could not reach NCBI to check the API key. Continuing. {}", check.getMessage());
        }
        return true;
    }

    /**
     * @return error when the job could not start, a dataset could not be processed, or the publication search
     * failed for half or more of the datasets it ran for or was stopped. Cancelled when the job was cancelled
     * with nothing else to report, and complete otherwise.
     */
    private TaskStatus postMessage(List<Integer> expAnnotationIds, Journal panoramaPublic)
    {
        int total = expAnnotationIds.size();
        if (total == 0)
        {
            getLogger().info("No private datasets were found.");
            return TaskStatus.complete;
        }
        if (!ncbiApiKeyAccepted())
        {
            return TaskStatus.error;
        }
        Logger log = getLogger();

        ProcessingContext context = ProcessingContext.create(panoramaPublic, getUser(), _test);
        if(!context.isValid())
        {
            context.logErrors(log);
            return TaskStatus.error;
        }
        ProcessingResults processingResults = new ProcessingResults(expAnnotationIds.size(), log);

        boolean completed = processExperiments(expAnnotationIds, context, processingResults, log);

        // An ERROR logged through the job's logger sets the status to error, and the status set here
        // would overwrite it. Report the errors the job recorded instead.
        if (processingResults.getTotalErrors() > 0 || processingResults.publicationSearchFailingWidely()
                || processingResults.isPublicationSearchStopped())
        {
            return TaskStatus.error;
        }
        return completed ? TaskStatus.complete : TaskStatus.cancelled;
    }

    /**
     * @return false if the job was cancelled before every dataset was processed.
     */
    private boolean processExperiments(List<Integer> expAnnotationIds, ProcessingContext context, ProcessingResults processingResults, Logger log)
    {
        log.info("Posting reminder message to: {} message threads.", expAnnotationIds.size());

        Set<Integer> exptIds = new HashSet<>(expAnnotationIds);
        if (_test)
        {
            log.info("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");
        }
        boolean completed = true;
        for (Integer experimentAnnotationsId : exptIds)
        {
            if (checkInterrupted() // checkInterrupted is set by Cancel in the pipeline UI.
                    || Thread.currentThread().isInterrupted())
            {
                log.warn("Job was cancelled. Stopping before experiment {}.", experimentAnnotationsId);
                completed = false;
                break;
            }

            try
            {
                processExperiment(experimentAnnotationsId, context, processingResults);
            }
            catch (Exception e)
            {
                processingResults.addProcessingFailed(experimentAnnotationsId, e);
            }
        }

        processingResults.logResults(log);
        return completed;
    }

    private void processExperiment(Integer experimentAnnotationsId, ProcessingContext context, ProcessingResults processingResults)
    {
        ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
        if (expAnnotations == null)
        {
            processingResults.addExperimentNotFound(experimentAnnotationsId);
            return;
        }

        DatasetStatus datasetStatus = DatasetStatusManager.getForExperiment(expAnnotations);
        ReminderDecision exclusion = getExclusion(expAnnotations, datasetStatus);
        if (exclusion != null)
        {
            processingResults.addSkipped(experimentAnnotationsId, exclusion);
            return;
        }

        // A dataset that cannot be sent a message is not searched for a publication.
        JournalSubmission submission = SubmissionManager.getSubmissionForExperiment(expAnnotations);
        if (submission == null)
        {
            processingResults.addSubmissionNotFound(experimentAnnotationsId);
            return;
        }
        if (submission.getLatestSubmission() == null)
        {
            processingResults.addLatestSubmissionNotFound(experimentAnnotationsId);
            return;
        }

        Container announcementsFolder = context.getAnnouncementsFolder();
        Announcement announcement = submission.getAnnouncement(context.getAnnouncementService(), context.getAnnouncementsFolder(), getUser());
        if (announcement == null)
        {
            processingResults.addAnnouncementNotFound(experimentAnnotationsId, submission, announcementsFolder);
            return;
        }

        User submitter = expAnnotations.getSubmitterUser();
        if (submitter == null)
        {
            processingResults.addSubmitterNotFound(experimentAnnotationsId);
            return;
        }

        if (expAnnotations.getShortUrl() == null)
        {
            processingResults.addShortUrlNotFound(experimentAnnotationsId);
            return;
        }

        // Search whether or not a reminder is due, so that a paper published before the first reminder is found.
        PublicationSearchOutcome search = PublicationSearchOutcome.NONE;
        try
        {
            search = searchForPublication(expAnnotations, datasetStatus, context.getSettings(), _forcePublicationCheck, processingResults);
        }
        catch (Exception e)
        {
            processingResults.addPublicationSearchFailed(experimentAnnotationsId, e);
            // A reminder that is due still names the saved paper, unless the submitter dismissed it.
            if (datasetStatus != null && datasetStatus.getUserDismissedPublication() == null)
            {
                search = new PublicationSearchOutcome(PublicationMatch.fromDatasetStatus(datasetStatus), false);
            }
        }
        boolean searchCompleted = search.isSearchCompleted();

        // Read again in case the submitter made the data public, requested an extension or deletion, or dismissed
        // the paper while the search ran.
        ExperimentAnnotations currentExpAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
        if (currentExpAnnotations == null)
        {
            processingResults.addExperimentNotFound(experimentAnnotationsId);
            return;
        }
        DatasetStatus currentStatus = DatasetStatusManager.getForExperiment(currentExpAnnotations);
        ReminderDecision exclusionAfterSearch = getExclusion(currentExpAnnotations, currentStatus);
        if (exclusionAfterSearch != null)
        {
            processingResults.addSkipped(experimentAnnotationsId, exclusionAfterSearch);
            return;
        }

        ReminderDecision decision = getReminderDecision(currentExpAnnotations, currentStatus, context.getSettings(), _ignoreReminderLimit);
        if (decision.remindersStopped())
        {
            processingResults.addRemindersStopped(currentExpAnnotations);
        }

        PublicationMatch publicationResult = isDismissed(search.getMatch(), currentStatus) ? null : search.getMatch();

        MessageType messageType = null;
        if (decision.shouldPost())
        {
            messageType = MessageType.REMINDER;
        }
        else if (isNewPublication(publicationResult, currentStatus))
        {
            messageType = MessageType.PUBLICATION_FOUND;
        }

        if (messageType == null)
        {
            if (searchCompleted && !context.isTestMode())
            {
                try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                {
                    updateDatasetStatus(expAnnotations, null, null, true);
                    transaction.commit();
                }
            }
            processingResults.addSkipped(experimentAnnotationsId, decision);
            return;
        }

        if (!context.isTestMode())
        {
            final MessageType type = messageType;
            postAndRecordMessage(() -> postReminderMessage(expAnnotations, submission, announcement, submitter, publicationResult, context),
                    () -> updateDatasetStatus(expAnnotations, type, publicationResult, searchCompleted));
        }

        processingResults.addProcessed(expAnnotations, announcement, messageType, decision.remindersStopped());
    }

    /**
     * Posts the message, then records it on DatasetStatus. Call with no transaction open. The message is then saved
     * before the email is sent, so the submitter is never emailed about a message that was not saved. If the post
     * throws, nothing is recorded and the next run posts the message again. The submitter gets the message a second
     * time if the post saved it before throwing, or if the update fails after the post.
     */
    static void postAndRecordMessage(Runnable postMessage, Runnable recordMessage)
    {
        postMessage.run();
        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            recordMessage.run();
            transaction.commit();
        }
    }

    /**
     * @return true if the match is a different publication from the one saved on DatasetStatus, or the same
     * publication now matched on the dataset's identifiers when it was saved as a match on author or title.
     * A match on the publication the submitter dismissed is not new.
     */
    private static boolean isNewPublication(@Nullable PublicationMatch match, @Nullable DatasetStatus datasetStatus)
    {
        if (match == null || isDismissed(match, datasetStatus))
        {
            return false;
        }
        if (datasetStatus == null || !match.getPublicationId().equals(datasetStatus.getPotentialPublicationId()))
        {
            return true;
        }
        PublicationMatch saved = PublicationMatch.fromDatasetStatus(datasetStatus);
        return match.matchesDataIdentifier() && (saved == null || !saved.matchesDataIdentifier());
    }

    private static boolean isDismissed(@Nullable PublicationMatch match, @Nullable DatasetStatus datasetStatus)
    {
        return match != null && datasetStatus != null && datasetStatus.getUserDismissedPublication() != null
                && match.getPublicationId().equals(datasetStatus.getPotentialPublicationId());
    }

    private void postReminderMessage(ExperimentAnnotations expAnnotations, JournalSubmission submission,
                                     Announcement announcement, User submitter, @Nullable PublicationMatch publicationResult,
                                     ProcessingContext context)
    {
        // Older message threads, pre March 2023, will not have the submitter or lab head on the notify list. Add them.
        List<User> notifyList = new ArrayList<>();
        notifyList.add(submitter);
        if (expAnnotations.getLabHeadUser() != null) {
            notifyList.add(expAnnotations.getLabHeadUser());
        }

        PanoramaPublicNotification.postPrivateDataReminderMessage(
                context.getJournal(),
                submission,
                expAnnotations,
                submitter,
                context.getCurrentUser(),
                notifyList,
                announcement,
                context.getAnnouncementsFolder(),
                context.getJournalAdmin(),
                publicationResult
        );
    }

    /**
     * A reminder or a publication notice sets lastReminderDate, so no reminder follows either one within the reminder
     * frequency. Only a reminder adds to reminderCount, which decides when reminders stop.
     */
    private void updateDatasetStatus(ExperimentAnnotations expAnnotations, @Nullable MessageType messageType,
                                     @Nullable PublicationMatch publicationResult, boolean searchCompleted)
    {
        // Read again rather than use the copy read before the NCBI search. The submitter may have dismissed a
        // publication or requested an extension while the search ran.
        DatasetStatus datasetStatus = DatasetStatusManager.getForExperiment(expAnnotations);
        boolean newStatus = datasetStatus == null;
        if (newStatus)
        {
            datasetStatus = new DatasetStatus();
            datasetStatus.setExperimentAnnotationsId(expAnnotations.getId());
        }

        Date now = new Date();
        if (messageType != null)
        {
            datasetStatus.setLastReminderDate(now);
        }
        if (messageType == MessageType.REMINDER)
        {
            datasetStatus.setReminderCount(datasetStatus.getReminderCount() + 1);
        }

        if (searchCompleted)
        {
            datasetStatus.setLastPublicationSearchDate(now);
        }

        // If this is a new/different publication, update it and clear dismissal
        if (isNewPublication(publicationResult, datasetStatus))
        {
            datasetStatus.setPotentialPublicationId(publicationResult.getPublicationId());
            datasetStatus.setPublicationType(publicationResult.getPublicationType().name());
            datasetStatus.setPublicationMatchInfo(publicationResult.getMatchInfo());
            datasetStatus.setCitation(publicationResult.getCitation());
            datasetStatus.setUserDismissedPublication(null);
        }

        if (newStatus)
        {
            DatasetStatusManager.save(datasetStatus, getUser());
        }
        else
        {
            DatasetStatusManager.update(datasetStatus, getUser());
        }
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Post private data reminder messages";
    }

    @Override
    protected boolean canInterrupt()
    {
        return true;
    }

    private static class  ProcessingContext
    {
        private final PrivateDataReminderSettings _reminderSettings;
        private final AnnouncementService _announcementService;
        private final Container _announcementsFolder;
        private final User _journalAdmin;
        private final User _currentUser;
        private final Journal _journal;
        private final boolean _testMode;

        private final List<String> _errors;

        private ProcessingContext(Builder builder)
        {
            _reminderSettings = builder.settings;
            _announcementService = builder.announcementService;
            _announcementsFolder = builder.announcementsFolder;
            _journalAdmin = builder.journalAdmin;
            _currentUser = builder.currentUser;
            _journal = builder.journal;
            _testMode = builder.testMode;
            _errors = new ArrayList<>(builder.creationErrors);
        }

        public PrivateDataReminderSettings getSettings()
        {
            return _reminderSettings;
        }
        public AnnouncementService getAnnouncementService()
        {
            return _announcementService;
        }
        public Container getAnnouncementsFolder()
        {
            return _announcementsFolder;
        }
        public User getJournalAdmin()
        {
            return _journalAdmin;
        }
        public User getCurrentUser()
        {
            return _currentUser;
        }
        public Journal getJournal()
        {
            return _journal;
        }
        public boolean isTestMode()
        {
            return _testMode;
        }

        public boolean isValid()
        {
            return _reminderSettings != null &&
                    _announcementService != null &&
                    _announcementsFolder != null &&
                    _journalAdmin != null &&
                    _currentUser != null &&
                    _journal != null;
        }

        public void logErrors(Logger log)
        {
            for (String error: _errors)
            {
                log.error(error);
            }
        }

        private static class Builder
        {
            private PrivateDataReminderSettings settings;
            private AnnouncementService announcementService;
            private Container announcementsFolder;
            private User journalAdmin;
            private User currentUser;
            private Journal journal;
            private boolean testMode = false;
            private final List<String> creationErrors = new ArrayList<>();

            public Builder withSettings(@Nullable PrivateDataReminderSettings settings)
            {
                this.settings = settings;
                if (settings == null)
                {
                    creationErrors.add("Could not load PrivateDataReminderSettings.");
                }
                return this;
            }

            public Builder withAnnouncementService(@Nullable AnnouncementService announcementService)
            {
                this.announcementService = announcementService;
                if (announcementService == null)
                {
                    creationErrors.add("Could not get AnnouncementService.");
                }
                return this;
            }

            public Builder withAnnouncementsFolder(@Nullable Container announcementsFolder)
            {
                this.announcementsFolder = announcementsFolder;
                if (announcementsFolder == null)
                {
                    creationErrors.add("Announcements folder is null - Panorama Public project does not have a support folder for messages.");
                }
                return this;
            }

            public Builder withJournalAdmin(@Nullable User journalAdmin)
            {
                this.journalAdmin = journalAdmin;
                if (journalAdmin == null)
                {
                    creationErrors.add("Could not find an admin user for the Panorama Public project.");
                }
                return this;
            }

            public Builder withCurrentUser(@Nullable User currentUser)
            {
                this.currentUser = currentUser;
                if (currentUser == null)
                {
                    creationErrors.add("Current user is null.");
                }
                return this;
            }

            public Builder withJournal(@Nullable Journal journal)
            {
                this.journal = journal;
                if (journal == null)
                {
                    creationErrors.add("Panorama Public journal is null.");
                }
                return this;
            }

            public Builder withTestMode(boolean testMode)
            {
                this.testMode = testMode;
                return this;
            }

            /**
             * Adds a custom error message to the creation errors
             */
            public Builder addError(String errorMessage)
            {
                if (errorMessage != null && !errorMessage.trim().isEmpty())
                {
                    creationErrors.add(errorMessage.trim());
                }
                return this;
            }

            public ProcessingContext build()
            {
                return new ProcessingContext(this);
            }
        }
        public static ProcessingContext create(@NotNull Journal panoramaPublic, User user, boolean testMode)
        {
            Builder builder = new Builder()
                    .withJournal(panoramaPublic)
                    .withJournalAdmin(JournalManager.getJournalAdminUser(panoramaPublic))
                    .withAnnouncementsFolder(panoramaPublic.getSupportContainer())
                    .withCurrentUser(user)
                    .withTestMode(testMode)
                    .withSettings(PrivateDataReminderSettings.get())
                    .withAnnouncementService(AnnouncementService.get());

            return builder.build();
        }
    }

    private static class ProcessingResults
    {
        private final List<Integer> _experimentNotFound = new ArrayList<>();
        private final List<Integer> _submissionNotFound = new ArrayList<>();
        private final List<Integer> _announcementNotFound = new ArrayList<>();
        private final List<Integer> _noSupportThread = new ArrayList<>();
        private final List<Integer> _submitterNotFound = new ArrayList<>();
        private final List<Integer> _shortUrlNotFound = new ArrayList<>();
        private final List<Integer> _publicationSearchFailed = new ArrayList<>();
        private int _publicationSearchAttempted = 0;
        private int _consecutiveAllFailed = 0;
        private boolean _publicationSearchStopped = false;
        private final List<Integer> _publicationSearchSkipped = new ArrayList<>();
        private final List<Integer> _processingFailed = new ArrayList<>();
        private final List<Integer> _skipped = new ArrayList<>();
        private final List<String> _remindersStopped = new ArrayList<>();
        private int _remindersStoppedSkipped = 0;
        private int _remindersStoppedNotified = 0;
        private int _processed = 0;
        private int _publicationNotices = 0;
        private final int _total;
        private final Logger _log;

        public ProcessingResults(int totalExperiments, Logger log)
        {
            _total = totalExperiments;
            _log = log;
        }

        public void addExperimentNotFound(Integer experimentId)
        {
            _experimentNotFound.add(experimentId);
            _log.error("Could not find an experiment with Id: {}.", experimentId);
        }

        public void addSubmissionNotFound(Integer experimentId)
        {
            _submissionNotFound.add(experimentId);
            _log.error("Could not find a submission request for experiment Id: {}.", experimentId);
        }
        public void addLatestSubmissionNotFound(Integer experimentId)
        {
            _submissionNotFound.add(experimentId);
            _log.error("Submission found but latest submission is null for experiment Id: {}.", experimentId);
        }

        public void addAnnouncementNotFound(Integer experimentId, JournalSubmission submission, Container announcementsFolder)
        {
            if (submission.getAnnouncementId() == null)
            {
                // Data submitted before Panorama Public started posting submission requests to a
                // message board.
                _noSupportThread.add(experimentId);
                return;
            }
            _announcementNotFound.add(experimentId);
            _log.error("Could not find the message thread for experiment Id: {}; announcement Id: {} in the folder {}.", experimentId, submission.getAnnouncementId(), announcementsFolder.getPath());
        }

        public void addSubmitterNotFound(Integer experimentId)
        {
            _submitterNotFound.add(experimentId);
            _log.error("Could not find a submitter user for experiment Id: {}.", experimentId);
        }

        public void addShortUrlNotFound(Integer experimentId)
        {
            _shortUrlNotFound.add(experimentId);
            _log.error("Could not find a short URL for experiment Id: {}.", experimentId);
        }

        public void addPublicationSearchAttempted()
        {
            _publicationSearchAttempted++;
        }

        public void addPublicationSearchCompleted()
        {
            _consecutiveAllFailed = 0;
        }

        public void addPublicationSearchFailed(Integer experimentId, Exception e)
        {
            _publicationSearchFailed.add(experimentId);
            _log.warn("Publication search failed for experiment Id: {}. A reminder that is due is still posted. {}", experimentId, e.getMessage(), e);

            // Some requests completing means NCBI is responding, so the failure is intermittent. Any other error says
            // nothing about NCBI and leaves the count unchanged.
            if (e instanceof NcbiSearchException ncbiError)
            {
                _consecutiveAllFailed = ncbiError.isAllRequestsFailed() ? _consecutiveAllFailed + 1 : 0;
            }
            if (!_publicationSearchStopped && _consecutiveAllFailed >= MAX_CONSECUTIVE_ALL_FAILED)
            {
                _publicationSearchStopped = true;
                _log.error("Every NCBI request failed for {} datasets in a row. The publication search is stopped for the rest of this run. Reminders are still posted.",
                        _consecutiveAllFailed);
            }
        }

        public boolean isPublicationSearchStopped()
        {
            return _publicationSearchStopped;
        }

        public void addPublicationSearchSkipped(Integer experimentId)
        {
            _publicationSearchSkipped.add(experimentId);
        }

        public void addProcessingFailed(Integer experimentId, Exception e)
        {
            _processingFailed.add(experimentId);
            _log.error("Error processing experiment {}: {}", experimentId, e.getMessage(), e);
        }

        public void addSkipped(Integer experimentId, ReminderDecision decision)
        {
            _skipped.add(experimentId);
            if (decision.remindersStopped())
            {
                _remindersStoppedSkipped++;
            }
            _log.info("Skipping reminder for experiment Id {} - {}.", experimentId, decision.getReason());
        }

        /**
         * Records a dataset whose reminders stopped because the submitter has not responded. It is not an error.
         */
        public void addRemindersStopped(ExperimentAnnotations expAnnotations)
        {
            _remindersStopped.add(expAnnotations.getId() + " (" + shortUrl(expAnnotations) + ")");
        }

        public void addProcessed(ExperimentAnnotations expAnnotations, Announcement announcement, MessageType messageType, boolean remindersStopped)
        {
            _processed++;
            if (remindersStopped)
            {
                _remindersStoppedNotified++;
            }
            if (messageType == MessageType.PUBLICATION_FOUND)
            {
                _publicationNotices++;
            }
            _log.info("Experiment ID: {}; Announcement ID {}; Short URL: {}; Message: {}.", expAnnotations.getId(), announcement.getRowId(),
                    shortUrl(expAnnotations), messageType == MessageType.REMINDER ? "reminder" : "new publication");
            _log.info("Folder: {}", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer()).getURIString());
            _log.info("Completed: {} of {}", _processed, _total);
        }

        public void logResults(Logger log)
        {
            logSkipped(log);
            logSummary(log);
        }

        public void logSkipped(Logger log)
        {
            if (!_experimentNotFound.isEmpty())
            {
                log.error("Experiments with the following Ids could not be found: {}", StringUtils.join(_experimentNotFound, ", "));
            }

            if (!_submissionNotFound.isEmpty())
            {
                log.error("Submission requests were not found for the following experiment Ids: {}", StringUtils.join(_submissionNotFound, ", "));
            }

            if (!_announcementNotFound.isEmpty())
            {
                log.error("Support message threads were not found for the following experiment Ids: {}", StringUtils.join(_announcementNotFound, ", "));
            }

            if (!_noSupportThread.isEmpty())
            {
                log.warn("The following experiment Ids were submitted before Panorama Public posted submission requests to a message board, so they have no support message thread and cannot be sent a reminder: {}",
                        StringUtils.join(_noSupportThread, ", "));
            }

            if (!_publicationSearchFailed.isEmpty())
            {
                String message = "Publication search failed for {} of the {} datasets a search was run for. Experiment Ids: {}. The NCBI requests that failed are logged as warnings above.";
                if (publicationSearchFailingWidely())
                {
                    log.error(message, _publicationSearchFailed.size(), _publicationSearchAttempted, StringUtils.join(_publicationSearchFailed, ", "));
                }
                else
                {
                    log.warn(message, _publicationSearchFailed.size(), _publicationSearchAttempted, StringUtils.join(_publicationSearchFailed, ", "));
                }
            }

            if (_publicationSearchStopped)
            {
                log.error("The publication search was stopped after every NCBI request failed for {} datasets in a row. Reminders were still posted. Experiment Ids not searched: {}",
                        MAX_CONSECUTIVE_ALL_FAILED,
                        _publicationSearchSkipped.isEmpty() ? "none" : StringUtils.join(_publicationSearchSkipped, ", "));
            }

            if (!_processingFailed.isEmpty())
            {
                log.error("Processing failed for the following experiment Ids: {}", StringUtils.join(_processingFailed, ", "));
            }

            if (!_submitterNotFound.isEmpty())
            {
                log.error("Submitter user was not found for the following experiment Ids: {}", StringUtils.join(_submitterNotFound, ", "));
            }

            if (!_shortUrlNotFound.isEmpty())
            {
                log.error("Short URL was not found for the following experiment Ids: {}", StringUtils.join(_shortUrlNotFound, ", "));
            }

            if (!_skipped.isEmpty())
            {
                log.info("The following experiments were skipped: {}", StringUtils.join(_skipped, ", "));
            }

            if (!_remindersStopped.isEmpty())
            {
                // WARN, not ERROR. These datasets are in a state the admin should know about, not a job failure.
                log.warn("Reminders have stopped for {} because the submitter has not responded. Publication searches continue. Experiment Ids: {}",
                        StringUtilsLabKey.pluralize(_remindersStopped.size(), "dataset"), StringUtils.join(_remindersStopped, ", "));
            }
        }

        public int getRemindersStoppedCount()
        {
            return _remindersStopped.size();
        }

        public int getRemindersStoppedSkippedCount()
        {
            return _remindersStoppedSkipped;
        }

        private static String shortUrl(ExperimentAnnotations expAnnotations)
        {
            return expAnnotations.getShortUrl() != null ? expAnnotations.getShortUrl().renderShortURL() : "no short URL";
        }

        public boolean publicationSearchFailingWidely()
        {
            return PrivateDataReminderJob.publicationSearchFailingWidely(
                    _publicationSearchFailed.size(), _publicationSearchAttempted);
        }

        /**
         * @return the number of datasets that could not be processed. Datasets whose publication search
         * failed are not counted, because the rest of the processing still ran for them.
         */
        public int getTotalErrors()
        {
            return _experimentNotFound.size() +
                    _submissionNotFound.size() +
                    _announcementNotFound.size() +
                    _submitterNotFound.size() +
                    _shortUrlNotFound.size() +
                    _processingFailed.size();
        }
        public void logSummary(Logger log)
        {
            if (!_skipped.isEmpty())
            {
               log.info("Skipped posting reminders for {}.", StringUtilsLabKey.pluralize(_skipped.size(), "experiment"));
            }

            if (_processed > 0)
            {
                log.info("Successfully processed {}.", StringUtilsLabKey.pluralize(_processed, "experiment"));
            }

            log.info("Processing complete: {} total, {} processed ({} reminders, {} new publication notices), {} skipped, {} with no support message thread, {} errors",
                    _total, _processed, _processed - _publicationNotices, _publicationNotices, _skipped.size(),
                    _noSupportThread.size(), getTotalErrors());

            if (!_remindersStopped.isEmpty())
            {
                // These datasets are also counted in the line above.
                log.info("{} at the reminder limit ({} skipped, {} sent a publication notice)",
                        StringUtilsLabKey.pluralize(_remindersStopped.size(), "dataset"), _remindersStoppedSkipped, _remindersStoppedNotified);
            }
        }
    }

    public static class TestCase extends Assert
    {
        private static final Logger TEST_LOG = LogHelper.getLogger(TestCase.class, "Private data reminder job tests");

        static
        {
            // The tests record failures on purpose. Keep their ERROR lines out of the server log.
            Configurator.setLevel(TEST_LOG.getName(), Level.OFF);
        }

        @Test
        public void testPublicationSearchFailingWidely()
        {
            // Below the floor the rate says too little to act on, even when every search failed.
            assertFalse("2 of 2 is under the floor", publicationSearchFailingWidely(2, 2));

            // At the floor, half or more of the searches that ran.
            assertTrue("2 of 3 is over half", publicationSearchFailingWidely(2, 3));
            assertFalse("1 of 3 is under half", publicationSearchFailingWidely(1, 3));

            // Exactly half is enough.
            assertTrue("2 of 4 is exactly half", publicationSearchFailingWidely(2, 4));
            assertFalse("1 of 4 is under half", publicationSearchFailingWidely(1, 4));

            assertFalse("No failures", publicationSearchFailingWidely(0, 10));
            assertFalse("No searches ran", publicationSearchFailingWidely(0, 0));
        }

        @Test
        public void testGetTotalErrors()
        {
            ProcessingResults results = new ProcessingResults(10, TEST_LOG);
            assertEquals("A run with nothing recorded has no errors", 0, results.getTotalErrors());

            results.addExperimentNotFound(1);
            results.addSubmissionNotFound(2);
            results.addLatestSubmissionNotFound(3);
            results.addSubmitterNotFound(4);
            results.addProcessingFailed(5, new IllegalStateException("test"));
            assertEquals("Each dataset that missed its reminder is counted once", 5, results.getTotalErrors());

            results.addPublicationSearchFailed(6, new NcbiSearchException("test"));
            assertEquals("A failed publication search is not a missed reminder", 5, results.getTotalErrors());
        }

        @Test
        public void testPublicationSearchStopped()
        {
            ProcessingResults results = new ProcessingResults(10, TEST_LOG);

            // Two datasets in a row with every request failed, then a search that completed.
            results.addPublicationSearchFailed(1, allRequestsFailed());
            results.addPublicationSearchFailed(2, allRequestsFailed());
            results.addPublicationSearchCompleted();
            results.addPublicationSearchFailed(3, allRequestsFailed());
            assertFalse("A completed search should reset the count", results.isPublicationSearchStopped());

            // A partial failure resets the count.
            results.addPublicationSearchFailed(4, allRequestsFailed());
            results.addPublicationSearchFailed(5, new NcbiSearchException("test", false));
            results.addPublicationSearchFailed(6, allRequestsFailed());
            assertFalse("A search where some requests completed should reset the count", results.isPublicationSearchStopped());

            results.addPublicationSearchFailed(7, allRequestsFailed());
            assertFalse("Two in a row should not stop the search", results.isPublicationSearchStopped());
            results.addPublicationSearchFailed(8, allRequestsFailed());
            assertTrue("Three in a row with every request failed should stop the search", results.isPublicationSearchStopped());

            // An error that is not an NCBI request failure leaves the count unchanged.
            results = new ProcessingResults(10, TEST_LOG);
            results.addPublicationSearchFailed(1, allRequestsFailed());
            results.addPublicationSearchFailed(2, allRequestsFailed());
            results.addPublicationSearchFailed(3, new IllegalStateException("test"));
            results.addPublicationSearchFailed(4, allRequestsFailed());
            assertTrue("An unexpected error should not reset the count of datasets with every request failed",
                    results.isPublicationSearchStopped());

            // Stopping the search sets the status, not the error count.
            assertEquals("Stopping the search should not count as a missed reminder", 0, results.getTotalErrors());
        }

        @Test
        public void testRemindersStoppedIsNotAnError()
        {
            ProcessingResults results = new ProcessingResults(10, TEST_LOG);
            ExperimentAnnotations noShortUrl = new ExperimentAnnotations();
            noShortUrl.setId(1);
            results.addRemindersStopped(noShortUrl);
            assertEquals("The dataset should be listed with the datasets whose reminders stopped", 1, results.getRemindersStoppedCount());
            assertEquals("Stopped reminders should not count as an error", 0, results.getTotalErrors());

            results.addSkipped(1, ReminderDecision.remindersStopped("test"));
            results.addSkipped(2, ReminderDecision.skip("test"));
            assertEquals("Only the skipped dataset whose reminders stopped should be counted", 1, results.getRemindersStoppedSkippedCount());
        }

        private static NcbiSearchException allRequestsFailed()
        {
            return new NcbiSearchException("test", true);
        }

        @Test
        public void testAnnouncementNotFoundSplitsOnAnnouncementId()
        {
            ProcessingResults results = new ProcessingResults(10, TEST_LOG);
            Container container = ContainerManager.getRoot();

            // No announcement id means the data predates the support message board. It cannot be sent
            // a reminder and is not an error.
            results.addAnnouncementNotFound(1, submissionWithAnnouncementId(null), container);
            assertEquals("A dataset that never had a thread is not an error", 0, results.getTotalErrors());

            // An announcement id whose thread could not be read is worth investigating.
            results.addAnnouncementNotFound(2, submissionWithAnnouncementId(99), container);
            assertEquals("A thread that went missing is an error", 1, results.getTotalErrors());
        }

        @Test
        public void testIsNewPublication()
        {
            DatasetStatus status = new DatasetStatus();
            status.setPotentialPublicationId("123");
            status.setPublicationType(NcbiConstants.DB.PubMed.name());
            status.setPublicationMatchInfo("Author, Title");
            PublicationMatch identifierMatch = PublicationMatch.fromMatchInfo("123", NcbiConstants.DB.PubMed, "ProteomeXchange ID");

            assertTrue("The same paper should be new when it is matched on the ProteomeXchange ID and was saved as an author and title match",
                    isNewPublication(identifierMatch, status));

            status.setUserDismissedPublication(new Date());
            assertFalse("The paper the submitter dismissed should not be new, even when it is matched on the ProteomeXchange ID",
                    isNewPublication(identifierMatch, status));
            assertTrue("A different paper should be new after a dismissal",
                    isNewPublication(PublicationMatch.fromMatchInfo("456", NcbiConstants.DB.PubMed, "Author, Title"), status));
        }

        @Test
        public void testPostAndRecordMessage()
        {
            List<String> calls = new ArrayList<>();
            try
            {
                postAndRecordMessage(() -> { throw new IllegalStateException("Post failed"); }, () -> calls.add("record"));
                fail("A failed post should throw");
            }
            catch (RuntimeException expected)
            {
            }
            assertTrue("A message that failed to post should not be recorded on DatasetStatus", calls.isEmpty());

            postAndRecordMessage(() -> calls.add("post"), () -> calls.add("record"));
            assertEquals("The message should be recorded on DatasetStatus after it is posted", List.of("post", "record"), calls);
        }

        private static JournalSubmission submissionWithAnnouncementId(Integer announcementId)
        {
            JournalExperiment journalExperiment = new JournalExperiment();
            journalExperiment.setAnnouncementId(announcementId);
            return new JournalSubmission(journalExperiment);
        }
    }
}
