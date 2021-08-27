package org.labkey.panoramapublic.model;

import org.apache.commons.collections4.comparators.ReverseComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ShortURLRecord;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class JournalSubmission
{
    private final JournalExperiment _journalExperiment;
    private final List<Submission> _submissions;

    public JournalSubmission(@NotNull JournalExperiment journalExperiment, @NotNull List<Submission> submissions)
    {
        _journalExperiment = journalExperiment;
        _submissions = submissions;
        // Sort by date created; newest first
        _submissions.sort(new ReverseComparator(Comparator.comparing(Submission::getCreated)));
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

    public @NotNull List<Submission> getSubmissions()
    {
        return Collections.unmodifiableList(_submissions);
    }

    public @Nullable Submission getNewestSubmission()
    {
        return _submissions.size() > 0 ? _submissions.get(0) : null;
    }

    public @Nullable Submission getPendingSubmission()
    {
        return _submissions.stream().filter(s -> s.getCopiedExperimentId() == null).findFirst().orElse(null);
    }

    public boolean hasPendingSubmission()
    {
        return getPendingSubmission() != null;
    }

    public @NotNull List<Submission> getCopiedSubmissions()
    {
        return _submissions.stream().filter(s -> s.getCopiedExperimentId() != null).collect(Collectors.toList());
    }

    public @Nullable Submission getLastCopiedSubmission()
    {
        return _submissions.stream().filter(s -> s.getCopiedExperimentId() != null).findFirst().orElse(null);
    }

    public Submission getSubmissionForCopiedExperiment(int copiedExperimentId)
    {
        return _submissions.stream().filter(s -> s.getCopiedExperimentId() != null && s.getCopiedExperimentId() == copiedExperimentId).findFirst().orElse(null);
    }

    public int getNextVersion()
    {
        List<Submission> previousVersions = getCopiedSubmissions().stream().filter(s -> s.getVersion() != null).collect(Collectors.toList());
        return previousVersions.size() == 0 ? 1 : previousVersions.get(0).getVersion() + 1;
    }

    public boolean isLastCopiedSubmission(int copiedExperimentId)
    {
        Submission lastCopied = getLastCopiedSubmission();
        return lastCopied != null && copiedExperimentId == lastCopied.getCopiedExperimentId();
    }

    public boolean isNewestSubmission(int submissionId)
    {
        Submission submission = getNewestSubmission();
        return submission != null && submission.getId() == submissionId;
    }
}
