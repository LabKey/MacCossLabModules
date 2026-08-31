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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;
import org.labkey.panoramapublic.model.DatasetStatus;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
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
    private boolean _test;
    private boolean _forcePublicationCheck;
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
        super("Panorama Public", info, root);
        setLogFile(root.getRootFileLike().resolveChild(FileUtil.makeFileNameWithTimestamp("PanoramaPublic-private-data-reminder", "log")));
        _panoramaPublic = panoramaPublic;

        _experimentAnnotationsIds = experimentAnnotationsIds;
        _test = test;
        _forcePublicationCheck = forcePublicationCheck;
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

    private static ReminderDecision getReminderDecision(@NotNull ExperimentAnnotations exptAnnotations, @NotNull PrivateDataReminderSettings settings)
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

        DatasetStatus datasetStatus = DatasetStatusManager.getForExperiment(exptAnnotations);
        if (datasetStatus != null)
        {
            if (datasetStatus.deletionRequested())
            {
                return ReminderDecision.skip("Submitter has requested deletion");
            }

            if (settings.isExtensionValid(datasetStatus))
            {
                return ReminderDecision.skip("Submitter requested an extension. Extension is current");
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
        private final String reason;

        private ReminderDecision(boolean shouldPost, String reason) {
            this.shouldPost = shouldPost;
            this.reason = reason;
        }

        public static ReminderDecision post() {
            return new ReminderDecision(true, null);
        }

        public static ReminderDecision skip(String reason) {
            return new ReminderDecision(false, reason);
        }

        public boolean shouldPost() { return shouldPost; }
        public String getReason() { return reason; }
    }
    
    /**
     * Checks for publications associated with the experiment if enabled in settings
     * @param expAnnotations The experiment to check
     * @param settings The reminder settings
     * @param forceCheck Force publication check regardless of global setting
     * @param log Logger for diagnostic messages
     * @return NcbiArticleMatch with search result
     */
    private PublicationMatch searchForPublication(@NotNull ExperimentAnnotations expAnnotations,
                                                         @NotNull PrivateDataReminderSettings settings,
                                                         boolean forceCheck,
                                                         @NotNull User user,
                                                         boolean testMode,
                                                         @NotNull Logger log)
    {
        // Check if publication checking is enabled (either globally or forced for this run)
        if (!forceCheck && !settings.isEnablePublicationSearch())
        {
            log.debug("Publication checking is disabled in settings");
            return null;
        }

        // Get existing DatasetStatus to check cached results and user dismissals
        DatasetStatus datasetStatus = DatasetStatusManager.getForExperiment(expAnnotations);
        if (datasetStatus != null)
        {
            // If user has dismissed the publication suggestion, check search delay
            Date dismissedDate = datasetStatus.getUserDismissedPublication();
            if (dismissedDate != null)
            {
                if (settings.isPublicationDismissalRecent(datasetStatus))
                {
                    log.info("User dismissed publication for experiment {} on {}; Publication search deferred ({} months)", expAnnotations.getId(), dismissedDate, settings.getPublicationSearchFrequency());
                    return null;
                }

                // Search deferral expired — re-search NCBI
                log.info("Search deferral expired for experiment {} (dismissed {}); re-searching NCBI", expAnnotations.getId(), dismissedDate);
                try
                {
                    PublicationMatch newMatch = NcbiPublicationSearchService.get().searchForPublication(expAnnotations, log);
                    if (newMatch != null && !newMatch.getPublicationId().equals(datasetStatus.getPotentialPublicationId()))
                    {
                        // Different publication found — return it (caller will save and notify)
                        log.info("New publication {} found for experiment {} (previously dismissed {})", newMatch.getPublicationId(), expAnnotations.getId(), datasetStatus.getPotentialPublicationId());
                        return newMatch;
                    }
                    else
                    {
                        // Same publication or nothing found — update dismissal date to restart search deferral
                        if (testMode)
                        {
                            log.info("TEST MODE: No new publication for experiment {}; Would reset search deferral", expAnnotations.getId());
                        }
                        else
                        {
                            log.info("No new publication for experiment {}; resetting search deferral", expAnnotations.getId());
                            datasetStatus.setUserDismissedPublication(new Date());
                            DatasetStatusManager.update(datasetStatus, user);
                        }
                        return null;
                    }
                }
                catch (NcbiSearchException e)
                {
                    // A search that could not run must not reset the deferral, which would record it
                    // as having found no new publication.
                    throw e;
                }
                catch (Exception e)
                {
                    log.error("Error re-searching publication for experiment {}: {}", expAnnotations.getId(), e.getMessage(), e);
                    return null;
                }
            }

            // If we already have a cached publication ID, use it
            if (!StringUtils.isBlank(datasetStatus.getPotentialPublicationId()))
            {
                log.info("Using cached publication {} {} for experiment {}", datasetStatus.getPublicationType(), datasetStatus.getPotentialPublicationId(), expAnnotations.getId());
                return PublicationMatch.fromDatasetStatus(datasetStatus);
            }
        }

        // Perform the publication search
        log.info("Searching for publications for experiment {}", expAnnotations.getId());
        try
        {
            return NcbiPublicationSearchService.get().searchForPublication(expAnnotations, log);
        }
        catch (NcbiSearchException e)
        {
            // The caller records this as a failed search. Returning null here would make it
            // indistinguishable from a dataset with no published paper.
            throw e;
        }
        catch (Exception e)
        {
            log.error("Error searching for publication for experiment {}: {}", expAnnotations.getId(), e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void run()
    {
        setStatus(TaskStatus.running);

        if (_panoramaPublic == null)
        {
            getLogger().error("Panorama Public project does not exist.");
            return;
        }

        postMessage(_experimentAnnotationsIds, _panoramaPublic);

        setStatus(TaskStatus.complete);
    }

    private void postMessage(List<Integer> expAnnotationIds, Journal panoramaPublic)
    {
        int total = expAnnotationIds.size();
        if (total == 0)
        {
            getLogger().info("No private datasets were found.");
            return;
        }
        Logger log = getLogger();

        ProcessingContext context = ProcessingContext.create(panoramaPublic, getUser(), _test);
        if(!context.isValid())
        {
            context.logErrors(log);
            return;
        }
        ProcessingResults processingResults = new ProcessingResults(expAnnotationIds.size(), log);

        processExperiments(_experimentAnnotationsIds, context, processingResults, log);

    }

    private void processExperiments(List<Integer> expAnnotationIds, ProcessingContext context, ProcessingResults processingResults, Logger log)
    {
        log.info("Posting reminder message to: {} message threads.", expAnnotationIds.size());

        Set<Integer> exptIds = new HashSet<>(expAnnotationIds);
        if (_test)
        {
            log.info("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");
        }
        for (Integer experimentAnnotationsId : exptIds)
        {
            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                processExperiment(experimentAnnotationsId, context, processingResults);
                transaction.commit();
            }
            catch (Exception e)
            {
                log.error("Error processing experiment {}: {}", experimentAnnotationsId, e.getMessage(), e);
            }
        }

        processingResults.logResults(log);
    }

    private void processExperiment(Integer experimentAnnotationsId, ProcessingContext context, ProcessingResults processingResults)
    {
        ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
        if (expAnnotations == null)
        {
            processingResults.addExperimentNotFound(experimentAnnotationsId);
            return;
        }

        ReminderDecision decision = getReminderDecision(expAnnotations, context.getSettings());
        if (!decision.shouldPost())
        {
            processingResults.addSkipped(experimentAnnotationsId, decision);
            return;
        }
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

        // Check for publications if enabled. A search that could not run still gets a reminder, since
        // the reminder is about the data being private, not about the paper.
        PublicationMatch publicationResult = null;
        try
        {
            publicationResult = searchForPublication(expAnnotations, context.getSettings(), _forcePublicationCheck, getUser(), context.isTestMode(), processingResults._log);
        }
        catch (NcbiSearchException e)
        {
            processingResults.addPublicationSearchFailed(experimentAnnotationsId, e);
        }

        if (!context.isTestMode())
        {
            postReminderMessage(expAnnotations, submission, announcement, submitter, publicationResult, context);

            updateDatasetStatus(expAnnotations, publicationResult);
        }

        processingResults.addProcessed(expAnnotations, announcement);
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

    private void updateDatasetStatus(ExperimentAnnotations expAnnotations, @Nullable PublicationMatch publicationResult)
    {
        DatasetStatus datasetStatus = DatasetStatusManager.getForExperiment(expAnnotations);
        if (datasetStatus == null)
        {
            datasetStatus = new DatasetStatus();
            datasetStatus.setExperimentAnnotationsId(expAnnotations.getId());
            datasetStatus.setLastReminderDate(new Date());

            // Save publication search results if found
            if (publicationResult != null)
            {
                datasetStatus.setPotentialPublicationId(publicationResult.getPublicationId());
                datasetStatus.setPublicationType(publicationResult.getPublicationType().name());
                datasetStatus.setPublicationMatchInfo(publicationResult.getMatchInfo());
                datasetStatus.setCitation(publicationResult.getCitation());
            }

            DatasetStatusManager.save(datasetStatus, getUser());
        }
        else
        {
            datasetStatus.setLastReminderDate(new Date());

            // Update publication search results
            if (publicationResult != null)
            {
                // If this is a new/different publication, update it and clear dismissal
                if (!publicationResult.getPublicationId().equals(datasetStatus.getPotentialPublicationId()))
                {
                    datasetStatus.setPotentialPublicationId(publicationResult.getPublicationId());
                    datasetStatus.setPublicationType(publicationResult.getPublicationType().name());
                    datasetStatus.setPublicationMatchInfo(publicationResult.getMatchInfo());
                    datasetStatus.setCitation(publicationResult.getCitation());
                    datasetStatus.setUserDismissedPublication(null); // Clear dismissal for new publication
                }
            }

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
        private final List<Integer> _submitterNotFound = new ArrayList<>();
        private final List<Integer> _publicationSearchFailed = new ArrayList<>();
        private final List<Integer> _skipped = new ArrayList<>();
        private int _processed = 0;
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
            _announcementNotFound.add(experimentId);
            _log.error("Could not find the message thread for experiment Id: {}; announcement Id: {} in the folder {}.", experimentId, submission.getAnnouncementId(), announcementsFolder.getPath());
        }

        public void addSubmitterNotFound(Integer experimentId)
        {
            _submitterNotFound.add(experimentId);
            _log.error("Could not find a submitter user for experiment Id: {}.", experimentId);
        }

        public void addPublicationSearchFailed(Integer experimentId, Exception e)
        {
            _publicationSearchFailed.add(experimentId);
            _log.error("Publication search failed for experiment Id: {}. A reminder was still posted. {}", experimentId, e.getMessage(), e);
        }

        public void addSkipped(Integer experimentId, ReminderDecision decision)
        {
            _skipped.add(experimentId);
            _log.info("Skipping reminder for experiment Id {} - {}.", experimentId, decision.getReason());
        }

        public void addProcessed(ExperimentAnnotations expAnnotations, Announcement announcement)
        {
            _processed++;
            _log.info("Experiment ID: {}; Announcement ID {}; Short URL: {}.", expAnnotations.getId(), announcement.getRowId(), expAnnotations.getShortUrl().renderShortURL());
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

            if (!_publicationSearchFailed.isEmpty())
            {
                log.error("Publication search failed for the following experiment Ids: {}. Check the NCBI API key in the Private Data Reminder Settings.", StringUtils.join(_publicationSearchFailed, ", "));
            }

            if (!_submitterNotFound.isEmpty())
            {
                log.error("Submitter user was not found for the following experiment Ids: {}", StringUtils.join(_submitterNotFound, ", "));
            }

            if (!_skipped.isEmpty())
            {
                log.info("The following experiments were skipped: {}", StringUtils.join(_skipped, ", "));
            }
        }

        public int getTotalErrors()
        {
            return _experimentNotFound.size() +
                    _submissionNotFound.size() +
                    _announcementNotFound.size() +
                    _submitterNotFound.size();
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

            log.info("Processing complete: {} total, {} processed, {} skipped, {} errors", _total, _processed, _skipped.size(), getTotalErrors());
        }
    }
}
