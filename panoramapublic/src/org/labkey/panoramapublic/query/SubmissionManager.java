package org.labkey.panoramapublic.query;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SubmissionManager
{
    private static final Logger LOG = LogHelper.getLogger(SubmissionManager.class, "Panorama Public submissions");

    /**
     * @param id database row id of the submission
     * @param container of the experiment that the submission belongs to
     * @return a Submission object with the given database row id, that belongs to an experiment in the given container
     */
    public static Submission getSubmission(int id, Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT s.* FROM ").append(PanoramaPublicManager.getTableInfoSubmission(), "s")
                .append(" INNER JOIN ").append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je")
                .append(" ON s.JournalExperimentId = je.Id ")
                .append(" INNER JOIN ").append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "e")
                .append(" ON je.experimentAnnotationsId = e.Id ")
                .append(" WHERE s.id = ? ").add(id)
                .append(" AND e.Container = ? ").add(container);
        return new SqlSelector(PanoramaPublicManager.getSchema().getScope(), sql).getObject(Submission.class);
    }

    /**
     * @param copiedExperimentId Id of the experiment copied to a journal project
     * @return the data license in the submission request associated with a journal copy of an experiment
     */
    public static DataLicense getDataLicenseForCopiedExperiment(int copiedExperimentId)
    {
        Submission submission = SubmissionManager.getSubmissionForCopiedExperiment(copiedExperimentId);
        return submission != null ? submission.getDataLicense() : null;
    }

    /**
     * @param copiedExperimentId Id of an experiment copied to a journal project
     * @return the Submission associated with the given copiedExperimentId
     */
    private static Submission getSubmissionForCopiedExperiment(int copiedExperimentId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), copiedExperimentId);
        return new TableSelector(PanoramaPublicManager.getTableInfoSubmission(), filter, null).getObject(Submission.class);
    }

    /**
     * @return a list of submissions for the given journalExperimentId, sorted descending by the create date.
     */
    public static List<Submission> getSubmissionsNewestFirst(int journalExperimentId)
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
     * Deletes a row in the Submission table.  If this is the only non-obsolete submission for the journalExperimentId
     * then the row in the JournalExperiment table is also deleted along with the short URLs associated with the
     * submission request.
     */
    public static void deleteSubmission(@NotNull Submission submission, @NotNull User user)
    {
        try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            deleteSubmission(submission.getId());

            List<Submission> allSubmissions = getSubmissionsNewestFirst(submission.getJournalExperimentId());
            allSubmissions.removeIf(Submission::isObsolete);

            if (allSubmissions.size() == 0)
            {
                // Delete the JournalExperiment if there are no submissions left after removing any obsolete ones
                JournalExperiment je = getJournalExperiment(submission.getJournalExperimentId());
                if (je != null)
                {
                    deleteSubmissionsForJournalExperiment(je.getId());
                    deleteJournalExperiment(je, user);
                }
            }

            transaction.commit();
        }
    }

    private static void deleteSubmission(int submissionId)
    {
        Table.delete(PanoramaPublicManager.getTableInfoSubmission(), submissionId);
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

    private static @Nullable JournalSubmission getJournalSubmission(int id)
    {
        return getJournalSubmission(getJournalExperiment(id));
    }

    private static JournalExperiment getJournalExperiment(int id)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), null, null).getObject(id, JournalExperiment.class);
    }

    public static @Nullable JournalSubmission getJournalSubmission(int id, @NotNull Container container)
    {
        return getJournalSubmission(container, new SQLFragment(" je.id = ? ").add(id));
    }

    public static @Nullable JournalSubmission getJournalSubmission(int expeAnnotationsId, int journalId, @NotNull Container container)
    {
        SQLFragment filter = new SQLFragment(" je.experimentAnnotationsId = ? AND je.journalId = ? ").add(expeAnnotationsId).add(journalId);
        return getJournalSubmission(container, filter);
    }

    private static JournalSubmission getJournalSubmission(@NotNull Container container, SQLFragment filterSql)
    {
        SQLFragment sql = new SQLFragment("SELECT je.* FROM ").append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je")
                .append(" INNER JOIN ").append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "e")
                .append(" ON je.experimentAnnotationsId = e.Id ")
                .append(" WHERE ").append(filterSql)
                .append(" AND e.Container = ?").add(container);
        return getJournalSubmission(new SqlSelector(PanoramaPublicManager.getSchema().getScope(), sql).getObject(JournalExperiment.class));
    }

    private static JournalSubmission getJournalSubmission(JournalExperiment je)
    {
        return je == null ? null : new JournalSubmission(je);
    }

    /**
     * Returns a list of JournalSubmission objects for the given experiment, one per journal. On PanoramaWeb the only
     * journal is "Panorama Public".
     */
    public static List<JournalSubmission> getAllJournalSubmissions(@NotNull ExperimentAnnotations expAnnotations)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId());
        List<JournalExperiment> jeList = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
        return jeList.stream().map(JournalSubmission::new).collect(Collectors.toList());
    }

    /**
     * Returns a JournalSubmission object corresponding to the newest row in the JournalExperiment table for the given experiment.
     * The code allows for an experiment to be submitted to more than one 'journal' projects, but on PanoramaWeb we have only
     * one 'journal' - "Panorama Public" so the newest JournalSubmission will also be the only one.
     */
    public static @Nullable JournalSubmission getNewestJournalSubmission(@NotNull ExperimentAnnotations expAnnotations)
    {
        return getAllJournalSubmissions(expAnnotations).stream().max(Comparator.comparing(JournalSubmission::getCreated)).orElse(null);
    }

    /**
     * Returns a JournalSubmission object where the copiedExperimentId of one of the rows in the Submission table is the
     * same as the id of the given experiment.
     */
    public static @Nullable JournalSubmission getSubmissionForJournalCopy(@NotNull ExperimentAnnotations journalCopy)
    {
        Submission submission = getSubmissionForCopiedExperiment(journalCopy.getId());
        if (submission != null)
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
        Table.insert(user, PanoramaPublicManager.getTableInfoSubmission(), s);
        return new JournalSubmission(je);
    }

    public static @NotNull JournalExperiment updateJournalExperiment(@NotNull JournalExperiment journalExperiment, @NotNull User user)
    {
        return Table.update(user, PanoramaPublicManager.getTableInfoJournalExperiment(), journalExperiment, journalExperiment.getId());
    }

    public static void updateAccessUrlTarget(@NotNull ShortURLRecord shortAccessUrlRecord, @NotNull ExperimentAnnotations targetExperiment, User user) throws ValidationException
    {
        ActionURL targetUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(targetExperiment.getContainer());

        ensureEditorRole(shortAccessUrlRecord, user);

        ShortURLService shortURLService = ShortURLService.get();
        shortURLService.saveShortURL(shortAccessUrlRecord.getShortURL(), targetUrl, user);
    }

    private static void ensureEditorRole(@NotNull ShortURLRecord shortUrl, User user)
    {
        // If the user is not the one that created the shortUrl then we need to add this user as an editor to the record's SecurityPolicy.
        // Examples: A Panorama Public admin making a copy of user's data will need permission to update the target of the shortUrl.
        //           A folder admin updating the submission request made by another submitter may want to change the shortUrl. They
        //           will need permission to delete the old shortUrl.
        MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortUrl));
        boolean isEditor = policy.getAssignedRoles(user).stream().anyMatch(r -> r instanceof EditorRole);
        if (!isEditor)
        {
            policy.addRoleAssignment(user, EditorRole.class);
            SecurityPolicyManager.savePolicy(policy);
        }
    }

    /**
     * Sets new short access and short copy URLs for the submission request. Deletes the old short URLs.
     * @throws ValidationException if one of the URLs is invalid (contains slashes, etc.)
     */
    public static void updateShortUrls(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je,
                                                   String newShortAccessUrl, @Nullable String newShortCopyUrl,
                                                   User user) throws ValidationException
    {
        ShortURLRecord oldAccessUrl = je.getShortAccessUrl();
        ShortURLRecord oldCopyUrl = je.getShortCopyUrl();

        ShortURLService shortURLService = ShortURLService.get();
        try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            if (!newShortAccessUrl.equals(oldAccessUrl.getShortURL()))
            {
                // Save the new short access URL
                ActionURL fullAccessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
                ShortURLRecord accessUrlRecord = JournalManager.saveShortURL(fullAccessUrl, newShortAccessUrl, journal, user);
                je.setShortAccessUrl(accessUrlRecord);

                updateJournalExperiment(je, user); // Save with the new URL before deleting the old short URL

                // Delete the old one
                deleteShortUrl(oldAccessUrl, user, shortURLService);
            }

            if (newShortCopyUrl != null && !newShortCopyUrl.equals(oldCopyUrl.getShortURL()))
            {
                // Save the new short copy URL.
                ActionURL fullCopyUrl = PanoramaPublicController.getCopyExperimentURL(expAnnotations.getId(), journal.getId(), expAnnotations.getContainer());
                ShortURLRecord copyUrlRecord = JournalManager.saveShortURL(fullCopyUrl, newShortCopyUrl, null, user);
                je.setShortCopyUrl(copyUrlRecord);

                updateJournalExperiment(je, user); // Save with the new URL before deleting the old short URL

                // Delete the old one
                deleteShortUrl(oldCopyUrl, user, shortURLService);
            }

            transaction.commit();
        }
    }

    private static void deleteShortUrl(ShortURLRecord shortUrl, User user, ShortURLService shortURLService) throws ValidationException
    {
        ensureEditorRole(shortUrl, user);
        shortURLService.deleteShortURL(shortUrl, user);
    }

    /**
     * @return a list of JournalExperiment objects that have the given shortUrl as the shortAccessUrl or the shortCopyUrl.
     */
    public static List<JournalExperiment> getJournalExperimentsWithShortUrl(ShortURLRecord shortUrl)
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
     * 1. Has > 1 rows in the Submission table:
     *    - Set the copiedExperimentId to null for the row in the Submission table associated with the given experiment.
     * 2. Only one row in the Submission table:
     *    a. If the source experiment is no longer in the database
     *       - Delete the row in the Submission and JournalExperiment tables.
     *        This will also delete the short URLs associated with the submission request.
     *    b. If the source experiment exists
     *       - Update the row in the Submission table to look like the experiment was submitted but not copied.
     *       - Change the target of the access URL to point to the source experiment.
     */
    public static void beforeCopiedExperimentDeleted(@NotNull ExperimentAnnotations expAnnotations, User user)
    {
        Submission submission = getSubmissionForCopiedExperiment(expAnnotations.getId());
        if (submission == null)
        {
            return;
        }
        JournalSubmission js = getJournalSubmission(submission.getJournalExperimentId());
        if (js == null)
        {
            return;
        }
        List<Submission> copiedSubmissions = js.getCopiedSubmissions();

        if (copiedSubmissions.size() > 1)
        {
            // There is more than one copy of the experiment on Panorama Public. This copy of the data is about to be deleted.
            // We do not want to delete the corresponding row in the Submission table. Instead we will set the
            // copiedExperimentId to null so that we don't get a FK violation.
            submission.setCopiedExperimentId(null);
            updateSubmission(submission, user);
        }
        else if (copiedSubmissions.size() == 1 && copiedSubmissions.get(0).getId() == submission.getId())
        {
            // This is the only copy of the data on Panorama Public.  The Panorama Public admin must have a really good reason
            // for deleting this experiment.
            ExperimentAnnotations sourceExperiment = ExperimentAnnotationsManager.get(js.getExperimentAnnotationsId());
            if (sourceExperiment == null)
            {
                // The source experiment no longer exists and this is the only submission copied to Panorama Public.
                // We can delete the row in the JournalExperiment and corresponding Submission table rows.
                // This will also delete:
                // - obsolete submissions (submissions were copied but the journal copy no longer exists)
                // - short URLs associated with this submission request
                deleteSubmission(submission, user);
            }
            else
            {
                // The source experiment still exists so we will have to reset everything to make it look like the the
                // experiment was submitted but not copied.
                submission.setCopiedExperimentId(null);
                submission.setCopied(null);
                updateSubmission(submission, user);

                expAnnotations.setShortUrl(null);

                try
                {
                    // Change the target of the access URL to point to the source experiment
                    updateAccessUrlTarget(js.getShortAccessUrl(), sourceExperiment, user);
                }
                catch (ValidationException e)
                {
                    // ValidationException can be thrown by ShortURLService.saveShortURL() if the URL is invalid (contains slashes, etc)
                    // We are updating an existing short URL that must be valid so we don't expect to see this exception. Log an error to the server log if it happens.
                    LOG.error("There was an error updating the target of the short access URL: " + js.getShortAccessUrl().getShortURL()
                            + "to: '" + sourceExperiment.getContainer().getPath() + "'", e);
                }
            }
        }
    }

    /**
     * This method should be called before an experiment (this is not a journal copy) is deleted.  If the experiment was
     * submitted to the given journal, but not copied to the journal project, the row in the JournalExperiment table and
     * all corresponding rows in the Submission table will be deleted.
     */
    public static void beforeSubmittedExperimentDeleted(@NotNull ExperimentAnnotations expAnnotations, @NotNull Journal journal, @NotNull User user)
    {
        JournalSubmission js = getJournalSubmission(expAnnotations.getId(), journal.getId(), expAnnotations.getContainer());
        if (js != null)
        {
            if (js.getCopiedSubmissions().size() == 0)
            {
                // Experiment was submitted but not yet copied so we can delete the rows in the Submission and JournalExperiment tables.
                try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                {
                    deleteSubmissionsForJournalExperiment(js.getJournalExperimentId());
                    deleteJournalExperiment(js.getJournalExperiment(), user);
                    transaction.commit();
                }
            }
        }
    }

    private static void deleteSubmissionsForJournalExperiment(int journalExperimentId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JournalExperimentId"), journalExperimentId);
        Table.delete(PanoramaPublicManager.getTableInfoSubmission(), filter);
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
