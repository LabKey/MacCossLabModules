package org.labkey.panoramapublic.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.Submission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SubmissionManager
{
    public static Submission getSubmission(int id)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), null, null).getObject(id, Submission.class);
    }

    public static Submission getSubmissionForCopiedExperiment(int copiedExperimentId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), copiedExperimentId);
        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), filter, null).getObject(Submission.class);
    }

    /**
     * @param journalExperimentId
     * @return a list of submissions for the given journalExperimentId, sorted descending by the create date.
     */
    private static List<Submission> getSubmissions(int journalExperimentId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JournalExperimentId"), journalExperimentId);
        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC, true);
        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), filter, sort).getArrayList(Submission.class);
    }

//    /**
//     * @param journalExperimentId
//     * @return a list of submission requests for the given journalExperimentId that were copied to a journal
//     * project (PanoramaPublic on PanoramaWeb). The list is sorted by the copied date, in descending order.
//     */
//    public static @NotNull List<Submission> getCopiedSubmissions(int journalExperimentId)
//    {
//        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("JournalExperimentId"), journalExperimentId);
//        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), null, CompareType.NONBLANK);
//        Sort sort = new Sort();
//        sort.insertSortColumn(FieldKey.fromParts("Copied"), Sort.SortDirection.DESC);
//        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), filter, sort).getArrayList(Submission.class);
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return a list of submission requests, for an experiment with the given experimentAnnotationsId, that were
//     * copied to a journal project (PanoramaPublic on PanoramaWeb). The list is sorted by the copied date, in descending order.
//     */
//    public static @NotNull List<Submission> getCopiedSubmissionsForExperiment(int experimentAnnotationsId)
//    {
//        SQLFragment sql = new SQLFragment("SELECT s.* FROM ")
//                .append(PanoramaPublicManager.getTableInfoSubmission(), "s")
//                .append(" INNER JOIN ").append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je")
//                .append(" ON s.journalexperimentid = je.id")
//                .append(" WHERE s.experimentannotationsid = ?").add(experimentAnnotationsId)
//                .append(" AND s.copiedexperimentid IS NOT NULL")
//                .append(" ORDER BY s.copied DESC");
//        return new SqlSelector(PanoramaPublicManager.getSchema().getScope(), sql).getArrayList(Submission.class);
////        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("JournalExperimentId"), journalExperimentId);
////        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), null, CompareType.NONBLANK);
////        Sort sort = new Sort();
////        sort.insertSortColumn(FieldKey.fromParts("Copied"), Sort.SortDirection.DESC);
////        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), filter, sort).getArrayList(Submission.class);
//    }
//
//    public static ExperimentAnnotations getSourceExperimentForJournalCopy(ExperimentAnnotations journalCopy)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), journalCopy.getId());
//        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
//        return je != null ? ExperimentAnnotationsManager.get(je.getExperimentAnnotationsId()) : null;
//    }

    public static void deleteSubmission(@NotNull Submission submission, @NotNull User user)
    {
        // TODO
//        if(submission.wasCopied())
//        {
//            // Do not delete a row in the Submission table if the data has already been copied
//            return;
//        }

        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("Id"), submission.getId());
            Table.delete(PanoramaPublicManager.getTableInfoSubmission(), filter);

            JournalSubmission js = getJournalSubmission(submission.getJournalExperimentId());
            if (js != null && js.getSubmissions().size() == 0)
            {
                // Delete the row in JournalExperiment if there are no rows for it in the Submission table
                filter = new SimpleFilter();
                filter.addCondition(FieldKey.fromParts("Id"), js.getJournalExperimentId());
                Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(), filter);

                // Try to delete the short copy URL. Since we just deleted the entry in table JournalExperiment
                // that references this URL we should not get a foreign key constraint error.
                JournalManager.tryDeleteShortUrl(js.getShortCopyUrl(), user);
                // Try to delete the short access URL if it is no longer referenced in the ExperimentAnnotations table.
                if (ExperimentAnnotationsManager.getExperimentForShortUrl(js.getShortAccessUrl()) == null)
                {
                    JournalManager.tryDeleteShortUrl(js.getShortAccessUrl(), user);
                }
            }
            transaction.commit();
        }
    }

    public static JournalSubmission getJournalSubmission(int id)
    {
        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), null, null).getObject(id, JournalExperiment.class);
        return new JournalSubmission(je, getSubmissions(je.getId()));
    }

    public static @Nullable JournalSubmission getJournalSubmission(int expeAnnotationsId, int journalId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("experimentAnnotationsId"), expeAnnotationsId);
        filter.addCondition(FieldKey.fromParts("journalId"), journalId);
        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
        return je == null ? null : new JournalSubmission(je, getSubmissions(je.getId()));
    }

    public static List<JournalSubmission> getAllJournalSubmissions(ExperimentAnnotations expAnnotations)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId());
        List<JournalExperiment> jeList = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
        List<JournalSubmission> jsList = new ArrayList<>();
        for (JournalExperiment je : jeList)
        {
            jsList.add(new JournalSubmission(je, getSubmissions(je.getId())));
        }
        return jsList;
    }

    public static @Nullable JournalSubmission getNewestJournalSubmission(ExperimentAnnotations expAnnotations)
    {
        return getAllJournalSubmissions(expAnnotations).stream().sorted(Comparator.comparing(JournalSubmission::getCreated).reversed()).findFirst().orElse(null);
    }

    public static Submission saveSubmission(Submission submission, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoSubmission(), submission);
    }

    public static Submission updateSubmission(Submission submission, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoSubmission(), submission, submission.getId());
    }

    public static void updateSubmissionUrl(Submission submission, ExperimentAnnotations expAnnotations, Journal journal, String shortAccessUrl, User user) throws ValidationException
    {
        // Save the new short access URL
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
        ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
        ShortURLRecord accessUrlRecord = JournalManager.saveShortURL(accessUrl, shortAccessUrl, journalGroup, user);
        submission.setShortAccessUrl(accessUrlRecord);

        updateSubmission(submission, user);
    }

    public static @Nullable JournalSubmission getSubmissionForJournalCopy(ExperimentAnnotations journalCopy)
    {
        Submission submission = getSubmissionForCopiedExperiment(journalCopy.getExperimentId());
        if(submission != null)
        {
            JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment()).getObject(submission.getJournalExperimentId(), JournalExperiment.class);
            return new JournalSubmission(je, getSubmissions(je.getId()));
        }
        return null;
    }

    public static JournalSubmission saveNewJournalSubmission(JournalExperiment je, Submission submission, User user)
    {
        je = Table.insert(user, PanoramaPublicManager.getTableInfoJournalExperiment(), je);
        submission.setJournalExperimentId(je.getId());
        submission = Table.insert(user, PanoramaPublicManager.getTableInfoSubmission(), submission);
        return new JournalSubmission(je, Collections.singletonList(submission));
    }

    public static JournalExperiment updateJournalExperiment(JournalExperiment journalExperiment, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoJournalExperiment(), journalExperiment, journalExperiment.getId());
    }
}
