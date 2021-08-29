package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.query.SubmissionManager;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class JournalSubmission
{
    private final JournalExperiment _journalExperiment;
    private List<Submission> _submissions;

    public JournalSubmission(@NotNull JournalExperiment journalExperiment)
    {
        _journalExperiment = journalExperiment;
    }

    public JournalExperiment getJournalExperiment()
    {
        return _journalExperiment;
    }

    public int getJournalExperimentId()
    {
        return _journalExperiment.getId();
    }

    public int getJournalId()
    {
        return _journalExperiment.getJournalId();
    }

    public int getExperimentAnnotationsId()
    {
        return _journalExperiment.getExperimentAnnotationsId();
    }

    public ShortURLRecord getShortAccessUrl()
    {
        return _journalExperiment.getShortAccessUrl();
    }

    public ShortURLRecord getShortCopyUrl()
    {
        return _journalExperiment.getShortCopyUrl();
    }

    public Date getCreated()
    {
        return _journalExperiment.getCreated();
    }

    public int getCreatedBy()
    {
        return _journalExperiment.getCreatedBy();
    }

    public Date getModified()
    {
        return _journalExperiment.getModified();
    }

    public int getModifiedBy()
    {
        return +_journalExperiment.getModifiedBy();
    }

    public Integer getAnnouncementId()
    {
        return _journalExperiment.getAnnouncementId();
    }

    public Integer getReviewerId()
    {
        return _journalExperiment.getReviewer();
    }

    private List<Submission> submissions()
    {
        if (_submissions == null)
        {
            List<Submission> allSubmissions = SubmissionManager.getSubmissionsNewestFirst(getJournalExperimentId());
            // Keep the non-obsolete submissions only. Obsolete submissions are the ones that are no longer associated
            // with an experiment copy in the journal project because the journal copy was deleted.
            _submissions = allSubmissions.stream().filter(s -> !s.isObsolete()).collect(Collectors.toList());
        }
        return _submissions;
    }

    /**
     * @return a list of submissions that have been copied to a journal project.
     */
    public @NotNull List<Submission> getCopiedSubmissions()
    {
        return submissions().stream().filter(Submission::hasCopy).collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return the most recent submission request.
     */
    public @Nullable Submission getLatestSubmission()
    {
        return submissions().size() > 0 ? submissions().get(0) : null;
    }

    /**
     * @return the most recent submission request if it has not yet been copied to a journal project or null if the most
     * recent submission request is not pending.
     */
    public @Nullable Submission getPendingSubmission()
    {
        Submission submission = getLatestSubmission();
        return submission != null && submission.isPending() ? submission : null;
    }

    public boolean hasPendingSubmission()
    {
        return getPendingSubmission() != null;
    }

    /**
     * @return the most recent submission request that was copied to a journal project or null if there are no copied
     * submission requests.
     */
    public @Nullable Submission getLatestCopiedSubmission()
    {
        List<Submission> copiedSubmissions = getCopiedSubmissions();
        return copiedSubmissions.size() > 0 ? copiedSubmissions.get(0) : null;
    }

    /**
     * @return a submission request corresponding to the given experiment id (i.e. copiedExperimentId for the submission
     * matches the given experiment id).
     */
    public @Nullable Submission getSubmissionForCopiedExperiment(int copiedExperimentId)
    {
        return submissions().stream().filter(s -> s.hasCopy() && s.getCopiedExperimentId() == copiedExperimentId).findFirst().orElse(null);
    }

    /**
     * @return true if the given experiment id corresponds to the latest copied submission.
     */
    public boolean isLatestExperimentCopy(int copiedExperimentId)
    {
        Submission lastCopied = getLatestCopiedSubmission();
        return lastCopied != null && copiedExperimentId == lastCopied.getCopiedExperimentId();
    }

    /**
     * @return true if the given submission id is for the most recent submission request.
     */
    public boolean isLatestSubmission(int submissionId)
    {
        Submission submission = getLatestSubmission();
        return submission != null && submission.getId() == submissionId;
    }
}
