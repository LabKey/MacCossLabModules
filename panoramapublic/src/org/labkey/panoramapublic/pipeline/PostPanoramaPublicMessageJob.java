package org.labkey.panoramapublic.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PostPanoramaPublicMessageJob extends PipelineJob
{
    private String _titlePrefix;
    private String _message;
    private List<Integer> _experimentAnnotationsIds;
    private boolean _test;

    // For serialization
    protected PostPanoramaPublicMessageJob()
    {
    }

    public PostPanoramaPublicMessageJob(ViewBackgroundInfo info, @NotNull PipeRoot root, List<Integer> experimentAnnotationsIds, String message,
                                        String titlePrefix, boolean test)
    {
        super("Panorama Public", info, root);
        setLogFile(root.getRootNioPath().resolve(FileUtil.makeFileNameWithTimestamp("PanoramaPublic-post-to-message-thread", "log")));
        _experimentAnnotationsIds = experimentAnnotationsIds;
        _message = message;
        _titlePrefix = titlePrefix;
        _test = test;
    }

    @Override
    public void run()
    {
        setStatus(PipelineJob.TaskStatus.running);
        if (StringUtils.isEmpty(_message))
        {
            getLogger().error("Message was blank. Exiting...");
            return;
        }
        if (_experimentAnnotationsIds == null || _experimentAnnotationsIds.isEmpty())
        {
            getLogger().error("No experiment Ids were found. Exising...");
        }
        if (_experimentAnnotationsIds != null)
        {
            postMessage();
            setStatus(PipelineJob.TaskStatus.complete);
        }
    }

    private void postMessage()
    {
        getLogger().info(String.format("%sPosting to : %d message threads", _test ? "TEST MODE: " : "", _experimentAnnotationsIds.size()));

        int done = 0;

        AnnouncementService announcementSvc = AnnouncementService.get();
        Journal journal = JournalManager.getJournal(JournalManager.PANORAMA_PUBLIC);
        Container announcementsContainer = journal.getSupportContainer();

        List<Integer> experimentNotFound = new ArrayList<>();
        List<Integer> submissionNotFound = new ArrayList<>();
        List<Integer> announcementNotFound = new ArrayList<>();
        List<Integer> submitterNotFound = new ArrayList<>();

        Set<Integer> exptIds = new HashSet<>(_experimentAnnotationsIds);
        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            for (Integer experimentAnnotationsId : exptIds)
            {
                ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.get(experimentAnnotationsId);
                if (expAnnotations == null)
                {
                    getLogger().error("Could not find an experiment with Id: " + experimentAnnotationsId);
                    experimentNotFound.add(experimentAnnotationsId);
                    continue;
                }
                JournalSubmission submission = SubmissionManager.getSubmissionForExperiment(expAnnotations);
                if (submission == null || submission.getLatestSubmission() == null)
                {
                    getLogger().error("Could not find a submission request for experiment Id: " + experimentAnnotationsId);
                    submissionNotFound.add(experimentAnnotationsId);
                    continue;
                }

                Announcement announcement = announcementSvc.getAnnouncement(announcementsContainer, getUser(), submission.getAnnouncementId());
                if (announcement == null)
                {
                    getLogger().error("Could not find message thread for experiment Id: " + experimentAnnotationsId
                            + "; announcement Id: " + submission.getAnnouncementId() + " in the folder " + announcementsContainer.getPath());
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
                String title = !StringUtils.isBlank(_titlePrefix) ? _titlePrefix + " " + expAnnotations.getShortUrl().renderShortURL() : announcement.getTitle();
                if (!_test)
                {
                    String substituted = PanoramaPublicNotification.replaceLinkPlaceholders(_message, expAnnotations, announcement, announcementsContainer);

                    // Older message threads, pre March 2023, will not have the submitter or lab head on the notify list. Add them.
                    List<User> notifyList = new ArrayList<>();
                    notifyList.add(submitter);
                    if (expAnnotations.getLabHeadUser() != null)
                    {
                        notifyList.add(expAnnotations.getLabHeadUser());
                    }
                    PanoramaPublicNotification.postNotification(journal, submission.getJournalExperiment(), substituted, submitter, getUser(),
                            title, DiscussionService.StatusOption.Closed, notifyList);
                }

                done++;
                getLogger().info(String.format("%s to message thread for experiment Id %d.  Done: %d", _test ? "Would post" : "Posted", experimentAnnotationsId, done));

            }
            transaction.commit();
        }

        if (experimentNotFound.size() > 0)
        {
            getLogger().error("Experiments with the following ids could not be found: " + StringUtils.join(experimentNotFound, ", "));
        }
        if (submissionNotFound.size() > 0)
        {
            getLogger().error("Submission requests could not be found for the following experiment ids: " + StringUtils.join(submissionNotFound, ", "));
        }
        if (announcementNotFound.size() > 0)
        {
            getLogger().error("Support message threads were not be found for the following experiment ids: " + StringUtils.join(announcementNotFound, ", "));
        }
        if (submitterNotFound.size() > 0)
        {
            getLogger().error("Submitter user was not found for the following experiment ids: " + StringUtils.join(submissionNotFound, ", "));
        }

        getLogger().info("Done");
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Posts a message to announcement threads of the selected experiments";
    }
}
