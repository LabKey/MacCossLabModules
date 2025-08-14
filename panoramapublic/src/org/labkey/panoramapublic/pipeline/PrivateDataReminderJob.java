package org.labkey.panoramapublic.pipeline;

import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;
import org.labkey.panoramapublic.model.DatasetStatus;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.query.DatasetStatusManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.time.Instant;
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
        super("Panorama Public", info, root);
        setLogFile(root.getRootNioPath().resolve(FileUtil.makeFileNameWithTimestamp("PanoramaPublic-private-data-reminder", "log")));
        _panoramaPublic = panoramaPublic;

        _experimentAnnotationsIds = experimentAnnotationsIds;
        _test = test;
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
        PrivateDataReminderSettings settings = PrivateDataReminderSettings.get();
        for (Container folder : subFolders)
        {
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.getExperimentInContainer(folder);
            privateDataIds.add(exptAnnotations.getId());
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

        DatasetStatus datasetStatus = DatasetStatusManager.getForShortUrl(exptAnnotations.getShortUrl());
        if (datasetStatus != null)
        {
            if (datasetStatus.deletionRequested())
            {
                return ReminderDecision.skip("Submitter has requested deletion");
            }

            if (datasetStatus.isExtensionCurrent(settings))
            {
                return ReminderDecision.skip("Submitter requested an extension. Extension is current.");
            }

            if (datasetStatus.isLastReminderRecent(settings))
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
            return ReminderDecision.skip(String.format("First reminder not due until %s", firstReminderDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))));
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

    @Override
    public void run()
    {
        setStatus(TaskStatus.running);

        if (_panoramaPublic == null)
        {
            getLogger().error("Panorama Public project does not exist");
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
        getLogger().info(String.format("Posting reminder message to: %d message threads", expAnnotationIds.size()));

        int done = 0;

        PrivateDataReminderSettings settings = PrivateDataReminderSettings.get();

        AnnouncementService announcementSvc = AnnouncementService.get();

        List<Integer> experimentNotFound = new ArrayList<>();
        List<Integer> submissionNotFound = new ArrayList<>();
        List<Integer> announcementNotFound = new ArrayList<>();
        List<Integer> submitterNotFound = new ArrayList<>();
        int skipped = 0;

        Container announcementsFolder = panoramaPublic.getSupportContainer();
        if (announcementsFolder == null)
        {
            getLogger().error(String.format("%s does not have a support folder for messages.", panoramaPublic.getName()));
            return;
        }

        User journalAdmin = JournalManager.getJournalAdminUser(panoramaPublic);
        if (journalAdmin == null)
        {
            getLogger().error(String.format("Could not find an admin user for %s.", panoramaPublic.getName()));
            return;
        }

        Set<Integer> exptIds = new HashSet<>(expAnnotationIds);

        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            if (_test)
            {
                getLogger().info("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");
            }
            for (Integer experimentAnnotationsId : exptIds)
            {
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
                if (expAnnotations == null)
                {
                    getLogger().error("Could not find an experiment with Id: " + experimentAnnotationsId);
                    experimentNotFound.add(experimentAnnotationsId);
                    continue;
                }

                ReminderDecision decision = getReminderDecision(expAnnotations, settings);
                if (!decision.shouldPost())
                {
                    getLogger().info("Skipping reminder for experiment Id " + experimentAnnotationsId + " - " + decision.getReason());
                    skipped++;
                    continue;
                }
                JournalSubmission submission = SubmissionManager.getSubmissionForExperiment(expAnnotations);
                if (submission == null || submission.getLatestSubmission() == null)
                {
                    getLogger().error("Could not find a submission request for experiment Id: " + experimentAnnotationsId);
                    submissionNotFound.add(experimentAnnotationsId);
                    continue;
                }

                Announcement announcement = announcementSvc.getAnnouncement(announcementsFolder, getUser(), submission.getAnnouncementId());
                if (announcement == null)
                {
                    getLogger().error("Could not find the message thread for experiment Id: " + experimentAnnotationsId
                            + "; announcement Id: " + submission.getAnnouncementId() + " in the folder " + announcementsFolder.getPath());
                    announcementNotFound.add(experimentAnnotationsId);
                    continue;
                }

                User submitter = expAnnotations.getSubmitterUser();
                if (submitter == null)
                {
                    getLogger().error("Could not find a submitter user for experiment Id: " + experimentAnnotationsId);
                    submitterNotFound.add(experimentAnnotationsId);
                    continue;
                }

                if (!_test)
                {
                    // Older message threads, pre March 2023, will not have the submitter or lab head on the notify list. Add them.
                    List<User> notifyList = new ArrayList<>();
                    notifyList.add(submitter);
                    if (expAnnotations.getLabHeadUser() != null)
                    {
                        notifyList.add(expAnnotations.getLabHeadUser());
                    }
                    PanoramaPublicNotification.postPrivateDataReminderMessage(panoramaPublic, submission, expAnnotations,
                            submitter, getUser(), notifyList, announcement, announcementsFolder, journalAdmin);

                    DatasetStatus datasetStatus = DatasetStatusManager.getForShortUrl(expAnnotations.getShortUrl());
                    if (datasetStatus == null)
                    {
                        datasetStatus = new DatasetStatus();
                        datasetStatus.setShortUrl(expAnnotations.getShortUrl());
                        datasetStatus.setLastReminderDate(Date.from(Instant.now()));
                        DatasetStatusManager.save(datasetStatus, getUser());
                    }
                    else
                    {
                        datasetStatus.setLastReminderDate(Date.from(Instant.now()));
                        DatasetStatusManager.update(datasetStatus, getUser());
                    }
                }

                getLogger().info(String.format("Experiment ID: %d; Announcement ID %d; Short URL: %s.",
                        experimentAnnotationsId, announcement.getRowId(), expAnnotations.getShortUrl().renderShortURL()));
                getLogger().info(String.format("Folder: %s", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer()).getURIString()));
                getLogger().info(String.format("Completed: %d of %d", ++done, total));

            }
            transaction.commit();
        }

        if (!experimentNotFound.isEmpty())
        {
            getLogger().error("Experiments with the following Ids could not be found: " + StringUtils.join(experimentNotFound, ", "));
        }
        if (!submissionNotFound.isEmpty())
        {
            getLogger().error("Submission requests were not found for the following experiment Ids: " + StringUtils.join(submissionNotFound, ", "));
        }
        if (!announcementNotFound.isEmpty())
        {
            getLogger().error("Support message threads were not found for the following experiment Ids: " + StringUtils.join(announcementNotFound, ", "));
        }
        if (!submitterNotFound.isEmpty())
        {
            getLogger().error("Submitter user was not found for the following experiment Ids: " + StringUtils.join(submitterNotFound, ", "));
        }
        if (skipped > 0)
        {
            getLogger().info("Skipped posting reminders for " + skipped +  " experiments ");
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
}
