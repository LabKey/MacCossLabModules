/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.panoramapublic.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.security.CopyTargetedMSExperimentRole;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * User: vsharma
 * Date: 2/26/14
 * Time: 12:49 PM
 */
public class JournalManager
{
    private static final Logger LOG = LogManager.getLogger(JournalManager.class);

    public static List<Journal> getJournals()
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal()).getArrayList(Journal.class);
    }

    public static Journal getJournal(String name)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal(),
                                 new SimpleFilter(FieldKey.fromParts("Name"), name),
                                 null).getObject(Journal.class);
    }

    public static Journal getJournal(int journalId)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal()).getObject(journalId, Journal.class);
    }

    public static boolean isJournalProject(Container project)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal(),
                new SimpleFilter(FieldKey.fromParts("project"), project),
                null).exists();
    }

    public static List<Journal> getJournalsForExperiment(int expAnnotationsId)
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT (j.*) FROM ");
        sql.append(PanoramaPublicManager.getTableInfoJournal(), "j");
        sql.append(" , ");
        sql.append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je");
        sql.append(" WHERE ");
        sql.append(" j.Id = je.JournalId ");
        sql.append(" AND je.ExperimentAnnotationsId = ? ");
        sql.add(expAnnotationsId);

        return new SqlSelector(PanoramaPublicManager.getSchema(), sql).getArrayList(Journal.class);
    }

    public static List<ExperimentAnnotations> getExperimentsForJournal(int journalId)
    {
        SQLFragment sql = new SQLFragment("SELECT e.* FROM ");
        sql.append(PanoramaPublicManager.getTableInfoExperimentAnnotations(), "e");
        sql.append(" , ");
        sql.append(PanoramaPublicManager.getTableInfoJournalExperiment(), "je");
        sql.append(" WHERE ");
        sql.append(" e.Id = je.ExperimentAnnotationsId ");
        sql.append(" AND je.JournalId = ? ");
        sql.add(journalId);

        return new SqlSelector(PanoramaPublicManager.getSchema(), sql).getArrayList(ExperimentAnnotations.class);
    }

//    public static JournalExperiment getJournalExperiment(int id)
//    {
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), null, null).getObject(id, JournalExperiment.class);
//    }
//
//    public static List<JournalExperiment> getJournalExperiment(ExperimentAnnotations expAnnotations)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId());
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
//    }
//
//    public static JournalExperiment getJournalExperiment(ExperimentAnnotations expAnnotations, Journal journal)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId());
//        filter.addCondition(FieldKey.fromParts("JournalId"), journal.getId());
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @param journalId
//     * @return list of older submitted requests that have a version, ordered by the version in descending order.
//     * The last submitted request, copied or not, does not have a version
//     */
//    public static @NotNull ArrayList<JournalExperiment> getPreviousVersions(int experimentAnnotationsId, int journalId)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
//        filter.addCondition(FieldKey.fromParts("JournalId"), journalId);
//        filter.addCondition(FieldKey.fromParts("Version"), null, CompareType.NONBLANK);
//        Sort sort = new Sort();
//        sort.appendSortColumn(FieldKey.fromParts("Version"), Sort.SortDirection.DESC, false);
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
//    }

//    public static JournalExperiment getJournalExperimentForCopiedExpt(ExperimentAnnotations experimentAnnotations)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), experimentAnnotations.getId());
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
//    }

//    public static List<JournalExperiment> getJournalExperiments(Integer experimentAnnotationsId, Integer journalId)
//    {
//        if(experimentAnnotationsId == null || journalId == null)
//            return null;
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
//        filter.addCondition(FieldKey.fromParts("JournalId"), journalId);
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return the row for the last submission request for the given experimentAnnotationsId.  This will return null if
//     * the experiment has not yet been submitted.
//     */
//    public static JournalExperiment getLastSubmission(int experimentAnnotationsId)
//    {
//        // Get the JournalExperiment entries for the experiment (sorted by date created, descending)
//        List<JournalExperiment> jeList = getJournalExperiments(experimentAnnotationsId);
//        return jeList.size() > 0 ? jeList.get(0) : null;
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return the row for the last submission request for the given experimentAnnotationsId that was copied to
//     * a journal project (PanoramaPublic on PanoramaWeb).  This will return null if the experiment was submitted
//     * but not yet copied, and there are no previous copied submission requests.
//     */
//    public static @Nullable JournalExperiment getLastCopiedSubmission(int experimentAnnotationsId)
//    {
//        List<JournalExperiment> copiedSubmissions = getCopiedSubmissions(experimentAnnotationsId);
//        return copiedSubmissions.size() > 0 ? copiedSubmissions.get(0) : null;
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return a list of submission requests for the given experimentAnnotationsId that were copied to a journal
//     * project (PanoramaPublic on PanoramaWeb). The list is sorted by the copied date, in descending order.
//     */
//    public static @NotNull List<JournalExperiment> getCopiedSubmissions(int experimentAnnotationsId)
//    {
//        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
//        filter.addCondition(FieldKey.fromParts("Copied"), null, CompareType.NONBLANK);
//        Sort sort = new Sort();
//        sort.insertSortColumn(FieldKey.fromParts("Copied"), Sort.SortDirection.DESC);
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, sort).getArrayList(JournalExperiment.class);
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return the row for the latest submission request for the given experimentAnnotationsId to a journal project
//     * (Panorama Public on PanoramaWeb) that has not yet been copied. This will return null if there is no pending request.
//     */
//    @Nullable
//    public static JournalExperiment getCurrentSubmission(int experimentAnnotationsId)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
//        filter.addCondition(FieldKey.fromParts("Copied"), null, CompareType.ISBLANK);
//        Sort sort = new Sort();
//        sort.appendSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC, false);
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, sort).setMaxRows(1).getObject(JournalExperiment.class);
//    }

//    /**
//     * @param experimentAnnotationsId
//     * @return a list of submissions for an experiment with the given id, sorted descending by the create date.
//     */
//    public static List<JournalExperiment> getJournalExperiments(int experimentAnnotationsId)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
//        Sort sort = new Sort();
//        sort.appendSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC, true);
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, sort).getArrayList(JournalExperiment.class);
//    }

//    public static ExperimentAnnotations getSubmittedExperiment(ExperimentAnnotations exptAnnotations)
//    {
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition(FieldKey.fromParts("CopiedExperimentId"), exptAnnotations.getId());
//        JournalExperiment je = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
//        return je != null ? ExperimentAnnotationsManager.get(je.getExperimentAnnotationsId()) : null;
//    }

//    public static JournalExperiment getLastPublishedRecord(int experimentAnnotationsId) // TODO
//    {
//        // Get the JournalExperiment entries for the experiment (sorted by date created, descending)
//        List<JournalExperiment> jeList = getJournalExperiments(experimentAnnotationsId);
//        return jeList.size() > 0 ? jeList.get(0) : null;
//    }

    public static String getExperimentShortUrl(ExperimentAnnotations expAnnotations)
    {
        if(expAnnotations.isJournalCopy())
        {
            if(expAnnotations.getShortUrl() != null)
            {
                return expAnnotations.getShortUrl().renderShortURL();
            }
        }
        else
        {
            // Return the short access URL of the most recent JournalExperiment record
            // On panoramaweb.org jeList will have only one entry, since we only have one 'journal' (Panorama Public)
            // and an experiment can be published to a 'journal' only once.
            JournalSubmission js = SubmissionManager.getNewestJournalSubmission(expAnnotations);
            return js == null ? null : js.getShortAccessUrl().renderShortURL();
        }
        return null;
    }

    public static List<JournalExperiment> getRecordsForShortUrl(ShortURLRecord shortUrl)
    {
        SimpleFilter.OrClause or = new SimpleFilter.OrClause();
        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("shortAccessUrl"), CompareType.EQUAL, shortUrl));
        or.addClause(new CompareType.EqualsCompareClause(FieldKey.fromParts("shortCopyUrl"), CompareType.EQUAL, shortUrl));

        SimpleFilter filter = new SimpleFilter();
        filter.addClause(or);
        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
    }

    public static boolean userHasCopyAccess(ExperimentAnnotations experimentAnnotations, Journal journal, User user)
    {
        if(user.hasSiteAdminPermission())
        {
            return true;
        }
        if(!user.isInGroup(journal.getLabkeyGroupId()))
        {
            return false;
        }
        if(SubmissionManager.getAllJournalSubmissions(experimentAnnotations).size() == 0)
        {
            return false;
        }
        // The user may no longer have export permissions on the folder, e.g. if the journal has already copied the folder.
        return experimentAnnotations.getContainer().hasPermission(user, FolderExportPermission.class);
    }

    public static void saveJournal(Journal journal, User user)
    {
        Table.insert(user, PanoramaPublicManager.getTableInfoJournal(), journal);
    }

    public static void updateJournal(Journal journal, User user)
    {
        Table.update(user, PanoramaPublicManager.getTableInfoJournal(), journal, journal.getId());
    }

    public static void beforeDeleteTargetedMSExperiment(ExperimentAnnotations expAnnotations, User user)
    {
        List<Journal> journals = getJournalsForExperiment(expAnnotations.getId());

        for(Journal journal: journals)
        {
            removeJournalAccess(expAnnotations, journal, user);
        }
    }

    public static void delete(Journal journal, User user)
    {
        // Get a list of experiments this journal has access to.
        List<ExperimentAnnotations> expAnnotations = getExperimentsForJournal(journal.getId());
        for(ExperimentAnnotations expAnnotation: expAnnotations)
        {
            removeJournalAccess(expAnnotation, journal, user);
        }
        Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(),
                new SimpleFilter(FieldKey.fromParts("journalId"), journal.getId()));

        Table.delete(PanoramaPublicManager.getTableInfoJournal(), new SimpleFilter(FieldKey.fromParts("id"), journal.getId()));
    }

//    private static JournalExperiment saveJournalExperiment(JournalExperiment je, User user)
//    {
//        Table.insert(user, PanoramaPublicManager.getTableInfoJournalExperiment(), je);
//        return je;
//    }

//    private static JournalExperiment updateJournalExperiment(JournalExperiment journalExperiment, User user)
//    {
//        return Table.update(user, PanoramaPublicManager.getTableInfoJournalExperiment(), journalExperiment, journalExperiment.getId());
//    }

    public static boolean journalHasAccess(Journal journal, ExperimentAnnotations experiment)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JournalId"), journal.getId());
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experiment.getId());

        Integer journalId = new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment(),
                Collections.singleton("JournalId"),
                filter, null).getObject(Integer.class);

        return (journalId != null);
    }

    public static void updateAccessUrl(ExperimentAnnotations targetExperiment, JournalExperiment sourceJournalExp, User user) throws ValidationException
    {
        ShortURLRecord shortAccessUrlRecord = sourceJournalExp.getShortAccessUrl();
        ActionURL targetUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(targetExperiment.getContainer());

        if(shortAccessUrlRecord != null)
        {
            // If the user is not the one that created this shortUrl (e.g. the experiment is being resubmitted by a different lab member)
            // then we need to add this user as an editor to the record's SecurityPolicy.
            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortAccessUrlRecord));
            if (!policy.getOwnPermissions(user).contains(UpdatePermission.class))
            {
                policy.addRoleAssignment(user, EditorRole.class);
                SecurityPolicyManager.savePolicy(policy);
            }
        }

        ShortURLService shortURLService = ShortURLService.get();
        shortAccessUrlRecord = shortURLService.saveShortURL(shortAccessUrlRecord.getShortURL(), targetUrl, user);

        sourceJournalExp.setShortAccessUrl(shortAccessUrlRecord);

        SubmissionManager.updateJournalExperiment(sourceJournalExp, user);
    }

    public static JournalSubmission setupJournalAccess(PanoramaPublicController.PanoramaPublicRequest request, User user) throws ValidationException
    {
        Journal journal = request.getJournal();
        ExperimentAnnotations exptAnnotations = request.getExperimentAnnotations();

        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());

        // Grant the journal group read and copy access to the source folder and subfolders
        addJournalPermissions(exptAnnotations, journalGroup, user);

        // Save the short access URL
        ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(exptAnnotations.getContainer());
        ShortURLRecord accessUrlRecord = saveShortURL(accessUrl, request.getShortAccessUrl(), journalGroup, user);

        // Save the short copy URL.
        ActionURL copyUrl = PanoramaPublicController.getCopyExperimentURL(exptAnnotations.getId(), journal.getId(), exptAnnotations.getContainer());
        ShortURLRecord copyUrlRecord = saveShortURL(copyUrl, request.getShortCopyUrl(), null, user);

        // Add an entry in the panoramapublic.JournalExperiment table.
        JournalExperiment je = new JournalExperiment();
        je.setJournalId(journal.getId());
        je.setExperimentAnnotationsId(exptAnnotations.getId());
        je.setShortAccessUrl(accessUrlRecord);
        je.setShortCopyUrl(copyUrlRecord);

        // Create a new row in the Submission table
        Submission s = new Submission();
        s.setPxidRequested(request.isGetPxid());
        s.setIncompletePxSubmission(request.isIncompletePxSubmission());
        s.setKeepPrivate(request.isKeepPrivate());
        s.setLabHeadName(request.getLabHeadName());
        s.setLabHeadEmail(request.getLabHeadEmail());
        s.setLabHeadAffiliation(request.getLabHeadAffiliation());
        s.setDataLicense(DataLicense.resolveLicense(request.getDataLicense()));
        s.setShortAccessUrl(je.getShortAccessUrl());

        return SubmissionManager.saveNewJournalSubmission(je, s, user);
    }

    private static void changeJournalPermissions(ExperimentAnnotations exptAnnotations, UserPrincipal journalGroup, User user, boolean add)
    {
        Set<Container> containers = ExperimentAnnotationsManager.getExperimentFolders(exptAnnotations, user);

        for(Container folder: containers)
        {
            if(add)
            {
                addPermission(folder, journalGroup);
            }
            else
            {
                removePermission(folder, journalGroup);
            }
        }
    }

    private static void addPermission(Container folder, UserPrincipal journalGroup)
    {
        SecurityPolicy oldPolicy = folder.getPolicy();
        if (oldPolicy.getOwnPermissions(journalGroup).contains(FolderExportPermission.class))
            return;
        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(folder, oldPolicy);

        Role folderAdminRole = RoleManager.getRole(FolderAdminRole.class);
        SortedSet<RoleAssignment> roles = oldPolicy.getAssignments();
        boolean hasFolderAdmin = false;
        for(RoleAssignment role: roles)
        {
            if(role.getRole().equals(folderAdminRole))
            {
                hasFolderAdmin = true;
                break;
            }
        }
        if(!hasFolderAdmin)
        {
            // If no folder admin role was found (as can be the case for folders with permissions inherited from a parent folder)
            // assign folder admin role to the project administrator(s)
            Role projectAdminRole = RoleManager.getRole(ProjectAdminRole.class);
            for(RoleAssignment role: roles)
            {
                if(role.getRole().equals(projectAdminRole))
                {
                    newPolicy.addRoleAssignment(UserManager.getUser(role.getUserId()), FolderAdminRole.class);
                }
            }
        }
        newPolicy.addRoleAssignment(journalGroup, CopyTargetedMSExperimentRole.class, false);
        SecurityPolicyManager.savePolicy(newPolicy);
    }

    private static void removePermission(Container folder, UserPrincipal journalGroup)
    {
        SecurityPolicy oldPolicy = folder.getPolicy();
        if (!oldPolicy.getOwnPermissions(journalGroup).contains(FolderExportPermission.class))
            return;
        List<Role> roles = oldPolicy.getAssignedRoles(journalGroup);

        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(folder, oldPolicy);
        newPolicy.clearAssignedRoles(journalGroup);
        for(Role role: roles)
        {
            if(!(role instanceof CopyTargetedMSExperimentRole))
            {
                newPolicy.addRoleAssignment(journalGroup, role);
            }
        }
        SecurityPolicyManager.savePolicy(newPolicy);
    }

    public static void addJournalPermissions(ExperimentAnnotations exptAnnotations, UserPrincipal journalGroup, User user)
    {
        changeJournalPermissions(exptAnnotations, journalGroup, user, true);
    }

    public static void removeJournalPermissions(ExperimentAnnotations exptAnnotations, Journal journal, User user)
    {
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
        changeJournalPermissions(exptAnnotations, journalGroup, user, false);
    }

    public static void removeJournalPermissions(ExperimentAnnotations exptAnnotations, UserPrincipal journalGroup, User user)
    {
        changeJournalPermissions(exptAnnotations, journalGroup, user, false);
    }

    static ShortURLRecord saveShortURL(ActionURL longURL, String shortUrl, Group journalGroup, User user) throws ValidationException
    {
        ShortURLService shortUrlService = ShortURLService.get();
        ShortURLRecord shortAccessURLRecord;
        try
        {
            shortAccessURLRecord = shortUrlService.saveShortURL(shortUrl, longURL, user);
        }
        catch(UnauthorizedException e)
        {
            throw new ValidationException("Error saving link \"" + shortUrl + "\". It may already be in use. Error message was: " + e.getMessage());
        }

        if(journalGroup != null)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortAccessURLRecord));
            // Add a role assignment to let another group manage the URL. This grants permission to the journal
            // to change where the URL redirects you to after they copy the data
            policy.addRoleAssignment(journalGroup, EditorRole.class);
            SecurityPolicyManager.savePolicy(policy);
        }
        return shortAccessURLRecord;
    }

    public static void removeJournalAccess(ExperimentAnnotations expAnnotations, Journal journal, User user)
    {
        JournalSubmission je = SubmissionManager.getJournalSubmission(expAnnotations.getId(), journal.getId());
        Submission submission = je.getNewestSubmission();
        if(submission != null)
        {
            SubmissionManager.deleteSubmission(submission, user);
        }
//
//        if(je != null && je.getCopiedExperimentId() == null)
//        {
//            // This experiment has not yet been copied to Panorama Public so we can delete the row in JournalExperiment
//            SimpleFilter filter = new SimpleFilter();
//            filter.addCondition(FieldKey.fromParts("Id"), je.getId());
//            Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(), filter);
//
//            // Try to delete the short copy URL. Since we just deleted the entry in table JournalExperiment
//            // that references this URL we should not get a foreign key constraint error.
//            tryDeleteShortUrl(je.getShortCopyUrl(), user);
//            // Try to delete the short access URL only if the experiment has not yet been copied (accessURL points to journal's folder after copy)
//            // OR the access url is no longer referenced in the ExperimentAnnotations table.
//            if(je.getCopied() == null || ExperimentAnnotationsManager.getExperimentForShortUrl(je.getShortAccessUrl()) == null)
//            {
//                tryDeleteShortUrl(je.getShortAccessUrl(), user);
//            }
//        }
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
        removeJournalPermissions(expAnnotations, journalGroup, user);
    }

//    public static void removeJournalAccess(JournalExperiment je, Submission submission, ExperimentAnnotations expAnnotations, Journal journal, User user)
//    {
//        if(je != null && je.getCopiedExperimentId() == null)
//        {
//            // This experiment has not yet been copied to Panorama Public so we can delete the row in JournalExperiment
//            SimpleFilter filter = new SimpleFilter();
//            filter.addCondition(FieldKey.fromParts("Id"), je.getId());
//            Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(), filter);
//
//            // Try to delete the short copy URL. Since we just deleted the entry in table JournalExperiment
//            // that references this URL we should not get a foreign key constraint error.
//            tryDeleteShortUrl(je.getShortCopyUrl(), user);
//            // Try to delete the short access URL only if the experiment has not yet been copied (accessURL points to journal's folder after copy)
//            // OR the access url is no longer referenced in the ExperimentAnnotations table.
//            if(je.getCopied() == null || ExperimentAnnotationsManager.getExperimentForShortUrl(je.getShortAccessUrl()) == null)
//            {
//                tryDeleteShortUrl(je.getShortAccessUrl(), user);
//            }
//        }
//
//        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
//        removeJournalPermissions(expAnnotations, journalGroup, user);
//    }

    static void tryDeleteShortUrl(ShortURLRecord shortUrl, User user)
    {
        if(shortUrl == null)
        {
            return;
        }

        ShortURLService shortURLService = ShortURLService.get();
        try
        {
            shortURLService.deleteShortURL(shortUrl, user);
        }
        // Log infos not errors. If this shortUrl is still associated with an experiment in a Panorama Public project
        // TargetedMSListener.canDelete(ShortURLRecord shortUrl) will returns errors, and the url will not be deleted.
        // The url is eventually deleted, but the errors logged could cause the PanoramaPublicTest to fail while trying to
        // delete the Panorama Public project if it still contains an experiment.
        catch(UnauthorizedException e)
        {
            LOG.info("User " + user.getEmail() + " (" + user.getUserId() + ") is not authorized to delete the shortUrl: " + shortUrl.getShortURL() + ". Error was: " + e.getMessage());
        }
        catch(ValidationException e)
        {
            LOG.info("Cannot delete the shortUrl: " + shortUrl.getShortURL() + ". Error was: " + e.getMessage());
        }
    }

    public static void deleteRowForJournalCopy(ExperimentAnnotations journalCopy)
    {
        Table.delete(PanoramaPublicManager.getTableInfoJournalExperiment(),
                new SimpleFilter().addCondition(FieldKey.fromParts("CopiedExperimentId"), journalCopy.getId()));
    }

//    public static JournalExperiment getRowForJournalCopy(ExperimentAnnotations journalCopy)
//    {
//        return new TableSelector(PanoramaPublicManager.getTableInfoJournalExperiment()
//                , new SimpleFilter().addCondition(FieldKey.fromParts("CopiedExperimentId"), journalCopy.getId())
//                , null).getObject(JournalExperiment.class);
//    }

    public static void updateJournalExperimentUrls(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission, String shortAccessUrl, String shortCopyUrl, User user) throws ValidationException
    {
        updateJournalExperimentUrls(expAnnotations, journal, je, submission, shortAccessUrl, shortCopyUrl, user, true);
    }

    public static void updateJournalExperimentUrls(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, Submission submission,
                                                   String shortAccessUrl, @Nullable String shortCopyUrl,
                                                   User user, boolean deleteOld) throws ValidationException
    {
        ShortURLRecord oldAccessUrl = je.getShortAccessUrl();
        ShortURLRecord oldCopyUrl = je.getShortCopyUrl();

        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());

        ShortURLService shortURLService = ShortURLService.get();
        if (!shortAccessUrl.equals(oldAccessUrl.getShortURL())) // TODO: replace with equals()
        {
            // Save the new short access URL
            ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
            ShortURLRecord accessUrlRecord = saveShortURL(accessUrl, shortAccessUrl, journalGroup, user);
            je.setShortAccessUrl(accessUrlRecord);
        }

        if (shortCopyUrl != null && !shortCopyUrl.equalsIgnoreCase(oldCopyUrl.getShortURL())) // TODO: replace with equals()
        {
            // Save the new short copy URL.
            ActionURL copyUrl = PanoramaPublicController.getCopyExperimentURL(expAnnotations.getId(), journal.getId(), expAnnotations.getContainer());
            ShortURLRecord copyUrlRecord = saveShortURL(copyUrl, shortCopyUrl, null, user);
            je.setShortCopyUrl(copyUrlRecord);

        }

        SubmissionManager.updateJournalExperiment(je, user);
        submission.setShortAccessUrl(je.getShortAccessUrl());
        SubmissionManager.updateSubmission(submission, user);

        if(deleteOld)
        {
            // Delete the old short URLs
            if (!shortAccessUrl.equalsIgnoreCase(oldAccessUrl.getShortURL()))
            {
                shortURLService.deleteShortURL(oldAccessUrl, user);
            }

            if (shortCopyUrl != null && !shortCopyUrl.equalsIgnoreCase(oldCopyUrl.getShortURL()))
            {
                shortURLService.deleteShortURL(oldCopyUrl, user);
            }
        }
    }

    public static void deleteProjectJournal(Container c, User user)
    {
        if(c.isProject())
        {
            // Journals are only associated with 'project' containers
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Project"), c.getEntityId());
            Journal journal = new TableSelector(PanoramaPublicManager.getTableInfoJournal(), filter, null).getObject(Journal.class);
            if (journal != null)
            {
                delete(journal, user);
            }
        }
    }

    public static User getJournalAdminUser(Journal journal)
    {
        Group group = SecurityManager.getGroup(journal.getLabkeyGroupId());
        if(group != null)
        {
            Set<User> grpMembers = SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS);
            if(grpMembers.size() != 0)
            {
                return grpMembers.stream().min(Comparator.comparing(User::getUserId)).orElse(null);
            }
        }
        return null;
    }
}
