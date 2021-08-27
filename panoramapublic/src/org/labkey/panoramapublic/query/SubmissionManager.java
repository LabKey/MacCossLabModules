package org.labkey.panoramapublic.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.DataLicense;
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
    private static final Logger LOG = LogManager.getLogger(SubmissionManager.class);

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

    public static Submission saveSubmission(Submission submission, User user)
    {
        return Table.insert(user, PanoramaPublicManager.getTableInfoSubmission(), submission);
    }

    public static Submission updateSubmission(Submission submission, User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoSubmission(), submission, submission.getId());
    }

    /**
     * Deletes a row in the Submission table.  If this is the only row for the submission's journalExperimentId
     * then the row in the JournalExperiment table is also deleted along with the short URLs associated with the
     * submission request.
     */
    public static void deleteSubmission(@NotNull Submission submission, @NotNull User user)
    {
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            Table.delete(PanoramaPublicManager.getTableInfoSubmission(), submission.getId());

            JournalSubmission js = getJournalSubmission(submission.getJournalExperimentId());
            if (js != null && js.getSubmissions().size() == 0)
            {
                deleteJournalExperiment(js.getJournalExperiment(), user);
            }
            transaction.commit();
        }
    }

    private static void deleteJournalExperiment(@NotNull JournalExperiment je, @NotNull User user)
    {
        Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(), je.getId());

        // Try to delete the short copy URL. Since we just deleted the entry in table JournalExperiment
        // that references this URL we should not get a foreign key constraint error.
        JournalManager.tryDeleteShortUrl(je.getShortCopyUrl(), user);
        // Try to delete the short access URL if it is no longer referenced in the ExperimentAnnotations table.
        if (ExperimentAnnotationsManager.getExperimentForShortUrl(je.getShortAccessUrl()) == null)
        {
            JournalManager.tryDeleteShortUrl(je.getShortAccessUrl(), user);
        }
    }

    public static @Nullable JournalSubmission getJournalSubmission(int id)
    {
        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), null, null).getObject(id, JournalExperiment.class);
        return getJournalSubmission(je);
    }

    public static @Nullable JournalSubmission getJournalSubmission(int expeAnnotationsId, int journalId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("experimentAnnotationsId"), expeAnnotationsId);
        filter.addCondition(FieldKey.fromParts("journalId"), journalId);
        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
        return getJournalSubmission(je);
    }

    private static JournalSubmission getJournalSubmission(JournalExperiment je)
    {
        return je == null ? null : new JournalSubmission(je, getSubmissions(je.getId()));
    }

    /**
     * Returns a list of JournalSubmission objects for the given experiment, one per journal.
     */
    public static List<JournalSubmission> getAllJournalSubmissions(@NotNull ExperimentAnnotations expAnnotations)
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

    /**
     * Returns a JournalSubmission object corresponding to the newest row in the JournalExperiment table, for the given experiment.
     * The code allows for an experiment to be submitted to more than one 'journal' projects, but on PanoramaWeb we have only
     * one 'journal' - "Panorama Public" so the newest JournalSubmission will also be the only one.
     */
    public static @Nullable JournalSubmission getNewestJournalSubmission(@NotNull ExperimentAnnotations expAnnotations)
    {
        return getAllJournalSubmissions(expAnnotations).stream().max(Comparator.comparing(JournalSubmission::getCreated)).orElse(null);
    }

    /**
     * Returns a JournalSubmission object where the copiedExperimentId of one of the rows in the Submission is the same as the id of the given experiment.
     */
    public static @Nullable JournalSubmission getSubmissionForJournalCopy(@NotNull ExperimentAnnotations journalCopy)
    {
        Submission submission = getSubmissionForCopiedExperiment(journalCopy.getId());
        if(submission != null)
        {
            JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment()).getObject(submission.getJournalExperimentId(), JournalExperiment.class);
            return getJournalSubmission(je);
        }
        return null;
    }

    public static @NotNull JournalSubmission createNewSubmission(@NotNull PanoramaPublicController.PanoramaPublicRequest request,
                                                                 @NotNull ShortURLRecord accessUrl, @NotNull ShortURLRecord copyUrl, @NotNull User user)
    {
        // Add a new row in the JournalExperiment table.
        JournalExperiment je = new JournalExperiment();
        je.setJournalId(request.getJournal().getId());
        je.setExperimentAnnotationsId(request.getExperimentAnnotations().getId());
        je.setShortAccessUrl(accessUrl);
        je.setShortCopyUrl(copyUrl);

        // Create a new row in the Submission table
        Submission s = new Submission();
        s.setPxidRequested(request.isGetPxid());
        s.setIncompletePxSubmission(request.isIncompletePxSubmission());
        s.setKeepPrivate(request.isKeepPrivate());
        s.setLabHeadName(request.getLabHeadName());
        s.setLabHeadEmail(request.getLabHeadEmail());
        s.setLabHeadAffiliation(request.getLabHeadAffiliation());
        s.setDataLicense(DataLicense.resolveLicense(request.getDataLicense()));

        je = Table.insert(user, PanoramaPublicManager.getTableInfoJournalExperiment(), je);
        s.setJournalExperimentId(je.getId());
        s = Table.insert(user, PanoramaPublicManager.getTableInfoSubmission(), s);
        return new JournalSubmission(je, Collections.singletonList(s));
    }

    public static @NotNull JournalExperiment updateJournalExperiment(@NotNull JournalExperiment journalExperiment, @NotNull User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoJournalExperiment(), journalExperiment, journalExperiment.getId());
    }

//    public static void updateSubmissionUrl(Submission submission, ExperimentAnnotations expAnnotations, Journal journal, String shortAccessUrl, User user) throws ValidationException
//    {
//        // Save the new short access URL
//        ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
//        ShortURLRecord accessUrlRecord = JournalManager.saveShortURL(accessUrl, shortAccessUrl, journal, user);
//        submission.setShortAccessUrl(accessUrlRecord);
//
//        updateSubmission(submission, user);
//    }

    public static void updateAccessUrlTarget(ExperimentAnnotations targetExperiment, JournalExperiment sourceJournalExp, User user) throws ValidationException
    {
        ShortURLRecord shortAccessUrlRecord = sourceJournalExp.getShortAccessUrl();
        ActionURL targetUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(targetExperiment.getContainer());

        if(shortAccessUrlRecord != null)
        {
            // If the user is not the one that created this shortUrl then we need to add this user as an editor to the record's SecurityPolicy.
            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortAccessUrlRecord));
            if (!policy.getOwnPermissions(user).contains(UpdatePermission.class))
            {
                policy.addRoleAssignment(user, EditorRole.class);
                SecurityPolicyManager.savePolicy(policy);
            }

            ShortURLService shortURLService = ShortURLService.get();
            shortURLService.saveShortURL(shortAccessUrlRecord.getShortURL(), targetUrl, user);
        }
//        shortAccessUrlRecord = shortURLService.saveShortURL(shortAccessUrlRecord.getShortURL(), targetUrl, user);
//
//        sourceJournalExp.setShortAccessUrl(shortAccessUrlRecord);
//        updateJournalExperiment(sourceJournalExp, user);
    }

    /**
     * Sets new short access and short copy URLs for the submission request. Deletes the old short URLs.
     * @throws ValidationException if one of the URLs is invalid (contains slashes, etc.)
     */
    public static void updateShortUrls(ExperimentAnnotations expAnnotations, Journal journal, JournalSubmission js,
                                                   String newShortAccessUrl, @Nullable String newShortCopyUrl,
                                                   User user) throws ValidationException
    {
        ShortURLRecord oldAccessUrl = js.getShortAccessUrl();
        ShortURLRecord oldCopyUrl = js.getShortCopyUrl();

        ShortURLService shortURLService = ShortURLService.get();
        try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            if (!newShortAccessUrl.equals(oldAccessUrl.getShortURL()))
            {
                // Save the new short access URL
                ActionURL fullAccessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
                ShortURLRecord accessUrlRecord = JournalManager.saveShortURL(fullAccessUrl, newShortAccessUrl, journal, user);
                js.getJournalExperiment().setShortAccessUrl(accessUrlRecord);

                // Delete the old one
                shortURLService.deleteShortURL(oldAccessUrl, user);
            }

            if (newShortCopyUrl != null && !newShortCopyUrl.equals(oldCopyUrl.getShortURL()))
            {
                // Save the new short copy URL.
                ActionURL fullCopyUrl = PanoramaPublicController.getCopyExperimentURL(expAnnotations.getId(), journal.getId(), expAnnotations.getContainer());
                ShortURLRecord copyUrlRecord = JournalManager.saveShortURL(fullCopyUrl, newShortCopyUrl, null, user);
                js.getJournalExperiment().setShortCopyUrl(copyUrlRecord);

                // Delete the old one
                shortURLService.deleteShortURL(oldCopyUrl, user);
            }

            updateJournalExperiment(js.getJournalExperiment(), user);

            transaction.commit();
        }
    }

    public static List<JournalExperiment> getJournalExperimentsForShortUrl(ShortURLRecord shortUrl)
    {
        SimpleFilter.OrClause or = new SimpleFilter.OrClause();
        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("shortAccessUrl"), CompareType.EQUAL, shortUrl));
        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("shortCopyUrl"), CompareType.EQUAL, shortUrl));

        SimpleFilter filter = new SimpleFilter();
        filter.addClause(or);
        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
    }

    /**
     * This method should be called before an experiment in a journal project (i.e. PanoramaPublic on PanoramaWeb) is deleted.
     * If the underlying submission request has
     * 1. Only one row in the Submission table:
     *    a. If the source experiment exists
     *       - Update the row in the Submission table to look like the experiment was submitted but not copied.
     *       - Change the target of the access URL to point to the source experiment.
     *    b. If the source experiment is no longer in the database
     *       - Delete the row in the Submission and JournalExperiment tables.
     *         This also deletes the short URLs associated with the submission request.
     * 2. Has > 1 rows in the Submission table:
     *    - Set the copiedExperimentId to null for the row in the Submission table associated with the given experiment.
     */
    public static void beforeCopiedExperimentDeleted(@NotNull ExperimentAnnotations expAnnotations, User user)
    {
        JournalSubmission js = SubmissionManager.getSubmissionForJournalCopy(expAnnotations);
        if (js != null)
        {
            Submission submission = js.getSubmissionForCopiedExperiment(expAnnotations.getId());
            if (submission != null)
            {
                if (js.getSubmissions().size() == 1)
                {
                    // This is the only row in the Submission table.  The journal admin must have a REALLY good reason to delete the journal copy.
                    ExperimentAnnotations sourceExperiment = ExperimentAnnotationsManager.get(js.getExperimentAnnotationsId());
                    if(sourceExperiment != null)
                    {
                        // The source experiment still exists so we will have to reset everything to make it look like the the experiment was submitted but not copied.
                        submission.setCopiedExperimentId(null);
                        submission.setVersion(null);
                        submission.setCopied(null);
                        updateSubmission(submission, user);

                        expAnnotations.setShortUrl(null);

                        try
                        {
                            updateAccessUrlTarget(sourceExperiment, js.getJournalExperiment(), user);
                        }
                        catch (ValidationException e)
                        {
                            // ValidationException can be thrown by ShortURLService.saveShortURL() if the URL is invalid (contains slashes, etc)
                            // We are updating an existing short URL so it should be valid and we don't expect to see this exception. Log an error to the server log if it happens.
                            LOG.error("There was an error updating the target of the short access URL: " + js.getShortAccessUrl().getShortURL()
                                    + "to: '" + sourceExperiment.getContainer().getPath() + "'", e);
                        }
                    }
                    else
                    {
                        // The source experiment no longer exists. We can delete the row in the JournalExperiment and Submission tables
                        // This will also delete the short URLs associated with this submission request.
                        deleteSubmission(submission, user);
                    }
                }
                else
                {
                    // The journal copy of this data is about to be deleted. We do not want to delete the corresponding row in the Submission table
                    // Instead we will set the copiedExperimentId and version to null.
                    submission.setCopiedExperimentId(null);
                    submission.setVersion(null);
                    updateSubmission(submission, user);
                }
            }
        }
    }

    /**
     * This method should be called before an experiment is deleted.  If the experiment was submitted to the given journal, but
     * not copied to the journal project, the row in the JournalExperiment table and matching rows in the Submission table will be deleted.
     */
    public static void beforeExperimentDeleted(@NotNull ExperimentAnnotations expAnnotations, @NotNull Journal journal, @NotNull User user)
    {
        JournalSubmission js = getJournalSubmission(expAnnotations.getId(), journal.getId());
        if(js != null)
        {
            if(js.getCopiedSubmissions().size() == 0)
            {
                // Experiment was submitted but not yet copied so we can delete the rows in the Submission and JournalExperiment tables.
                try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                {
                    for (Submission s : js.getSubmissions())
                    {
                        Table.delete(PanoramaPublicManager.getTableInfoSubmission(), s.getId());
                    }
                    deleteJournalExperiment(js.getJournalExperiment(), user);
                    transaction.commit();
                }
            }
        }
    }

    public static void deleteAllSubmissionsForJournal(Integer journalId)
    {
        // Delete all the rows in the Submission table
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(PanoramaPublicManager.getTableInfoSubmission(), "s")
                .append(" USING ").append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je")
                .append(" WHERE je.Id = s.JournalExperimentId ")
                .append(" AND je.JournalId = ? ").add(journalId);
        new SqlExecutor(PanoramaPublicManager.getSchema().getScope()).execute(sql);

       // Delete all the rows in the JournalExperiment table
       Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(), new SimpleFilter(FieldKey.fromParts("JournalId"), journalId));
    }
}
