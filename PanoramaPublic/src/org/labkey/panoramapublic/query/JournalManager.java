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
package org.labkey.targetedms.query;

import org.apache.log4j.Logger;
import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
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
import org.labkey.targetedms.PublishTargetedMSExperimentsController;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.model.Journal;
import org.labkey.targetedms.model.JournalExperiment;
import org.labkey.targetedms.security.CopyTargetedMSExperimentRole;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * User: vsharma
 * Date: 2/26/14
 * Time: 12:49 PM
 */
public class JournalManager
{
    private static final Logger LOG = Logger.getLogger(JournalManager.class);

    public static List<Journal> getJournals()
    {
        return new TableSelector(TargetedMSManager.getTableInfoJournal()).getArrayList(Journal.class);
    }

    public static Journal getJournal(String name)
    {
        return new TableSelector(TargetedMSManager.getTableInfoJournal(),
                                 new SimpleFilter(FieldKey.fromParts("Name"), name),
                                 null).getObject(Journal.class);
    }

    public static Journal getJournal(int journalId)
    {
        return new TableSelector(TargetedMSManager.getTableInfoJournal()).getObject(journalId, Journal.class);
    }

    public static boolean isJournalProject(Container project)
    {
        return new TableSelector(TargetedMSManager.getTableInfoJournal(),
                new SimpleFilter(FieldKey.fromParts("project"), project),
                null).exists();
    }

    public static List<Journal> getJournalsForExperiment(int expAnnotationsId)
    {
        SQLFragment sql = new SQLFragment("SELECT j.* FROM ");
        sql.append(TargetedMSManager.getTableInfoJournal(), "j");
        sql.append(" , ");
        sql.append(TargetedMSManager.getTableInfoJournalExperiment(), "je");
        sql.append(" WHERE ");
        sql.append(" j.Id = je.JournalId ");
        sql.append(" AND je.ExperimentAnnotationsId = ? ");
        sql.add(expAnnotationsId);

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Journal.class);
    }

    private static List<ExperimentAnnotations> getExperimentsForJournal(int journalId)
    {
        SQLFragment sql = new SQLFragment("SELECT e.* FROM ");
        sql.append(TargetedMSManager.getTableInfoExperimentAnnotations(), "e");
        sql.append(" , ");
        sql.append(TargetedMSManager.getTableInfoJournalExperiment(), "je");
        sql.append(" WHERE ");
        sql.append(" e.Id = je.ExperimentAnnotationsId ");
        sql.append(" AND je.JournalId = ? ");
        sql.add(journalId);

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(ExperimentAnnotations.class);
    }

    public static JournalExperiment getJournalExperiment(int experimentAnnotationsId, int journalId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
        filter.addCondition(FieldKey.fromParts("JournalId"), journalId);
        return new TableSelector(TargetedMSManager.getTableInfoJournalExperiment(), filter, null).getObject(JournalExperiment.class);
    }

    private static List<JournalExperiment> getJournalExperiments(int experimentAnnotationsId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experimentAnnotationsId);
        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC, true);
        return new TableSelector(TargetedMSManager.getTableInfoJournalExperiment(), filter, sort).getArrayList(JournalExperiment.class);
    }

    public static JournalExperiment getLastPublishedRecord(int experimentAnnotationsId)
    {
        // Get the JournalExperiment entries for the experiment (sorted by date created, descending)
        List<JournalExperiment> jeList = getJournalExperiments(experimentAnnotationsId);
        return jeList.size() > 0 ? jeList.get(0) : null;
    }

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
            JournalExperiment je = JournalManager.getLastPublishedRecord(expAnnotations.getId());
            return je == null ? null : je.getShortAccessUrl().renderShortURL();
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
        return new TableSelector(TargetedMSManager.getTableInfoJournalExperiment(), filter, null).getArrayList(JournalExperiment.class);
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
        JournalExperiment je = getJournalExperiment(experimentAnnotations, journal);
        if(je == null)
        {
            return false;
        }
        // The user may no longer have export permissions on the folder, e.g. if the journal has already copied the folder.
        return experimentAnnotations.getContainer().hasPermission(user, FolderExportPermission.class);
    }

    public static void saveJournal(Journal journal, User user)
    {
        Table.insert(user, TargetedMSManager.getTableInfoJournal(), journal);
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
        Table.delete(TargetedMSManager.getTableInfoJournalExperiment(),
                new SimpleFilter(FieldKey.fromParts("journalId"), journal.getId()));

        Table.delete(TargetedMSManager.getTableInfoJournal(), new SimpleFilter(FieldKey.fromParts("id"), journal.getId()));
    }

    public static JournalExperiment saveJournalExperiment(Journal journal, ExperimentAnnotations experiment, ShortURLRecord shortAccessUrl, ShortURLRecord shortCopyUrl,
                                                          boolean getPxid, boolean keepPrivate, User user)
    {
        JournalExperiment je = new JournalExperiment();
        je.setJournalId(journal.getId());
        je.setExperimentAnnotationsId(experiment.getId());
        je.setShortAccessUrl(shortAccessUrl);
        je.setShortCopyUrl(shortCopyUrl);
        je.setPxidRequested(getPxid);
        je.setKeepPrivate(keepPrivate);
        Table.insert(user, TargetedMSManager.getTableInfoJournalExperiment(), je);
        return je;
    }

    public static void updateJournalExperiment(JournalExperiment journalExperiment, User user)
    {
        Map<String, Object> pkVals = new HashMap<>();
        pkVals.put("experimentAnnotationsId", journalExperiment.getExperimentAnnotationsId());
        pkVals.put("journalId", journalExperiment.getJournalId());
        Table.update(user, TargetedMSManager.getTableInfoJournalExperiment(), journalExperiment, pkVals);
    }

    public static boolean journalHasAccess(Journal journal, ExperimentAnnotations experiment)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JournalId"), journal.getId());
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), experiment.getId());

        Integer journalId = new TableSelector(TargetedMSManager.getTableInfoJournalExperiment(),
                Collections.singleton("JournalId"),
                filter, null).getObject(Integer.class);

        return (journalId != null);
    }

    public static void updateAccessUrl(ExperimentAnnotations targetExperiment, JournalExperiment sourceJournalExp, User user) throws ValidationException
    {
        ShortURLRecord shortAccessUrlRecord = sourceJournalExp.getShortAccessUrl();
        ActionURL targetUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(targetExperiment.getContainer());

        ShortURLService shortURLService = ShortURLService.get();
        shortAccessUrlRecord = shortURLService.saveShortURL(shortAccessUrlRecord.getShortURL(), targetUrl, user);

        sourceJournalExp.setShortAccessUrl(shortAccessUrlRecord);

        Map<String, Integer> pkVals = new HashMap<>();
        pkVals.put("JournalId", sourceJournalExp.getJournalId());
        pkVals.put("ExperimentAnnotationsId", sourceJournalExp.getExperimentAnnotationsId());
        Table.update(user, TargetedMSManager.getTableInfoJournalExperiment(), sourceJournalExp, pkVals);
    }

    public static JournalExperiment getJournalExperiment(ExperimentAnnotations experiment, Journal journal)
    {
        return getJournalExperiment(experiment.getId(), journal.getId());
    }

    public static JournalExperiment addJournalAccess(ExperimentAnnotations exptAnnotations, Journal journal,
                                                     String shortAccessUrl, String shortCopyUrl, boolean getPxid, boolean keepPrivate, User user) throws ValidationException
    {
        try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            JournalExperiment je = setupJournalAccess(exptAnnotations, journal, shortAccessUrl, shortCopyUrl, getPxid, keepPrivate, user);

            transaction.commit();

            return je;
        }
    }

    private static JournalExperiment setupJournalAccess(ExperimentAnnotations exptAnnotations, Journal journal, String shortAccessUrl, String shortCopyUrl, boolean getPxid, boolean keepPrivate, User user) throws ValidationException
    {
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());

        // Grant the journal group read and copy access to the source folder and subfolders
        addJournalPermissions(exptAnnotations, journalGroup, user);

        // Save the short access URL
        ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(exptAnnotations.getContainer());
        ShortURLRecord accessUrlRecord = saveShortURL(accessUrl, shortAccessUrl, journalGroup, user);

        // Save the short copy URL.
        ActionURL copyUrl = PublishTargetedMSExperimentsController.getCopyExperimentURL(exptAnnotations.getId(), journal.getId(), exptAnnotations.getContainer());
        ShortURLRecord copyUrlRecord = saveShortURL(copyUrl, shortCopyUrl, null, user);

        // Add an entry in the targetedms.JournalExperiment table.
        JournalExperiment je = JournalManager.saveJournalExperiment(journal, exptAnnotations,
                                                                    accessUrlRecord,
                                                                    copyUrlRecord,
                                                                    getPxid,
                                                                    keepPrivate,
                                                                    user);
        return je;
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
        if(oldPolicy.hasPermission(journalGroup, FolderExportPermission.class))
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
        if(!oldPolicy.hasPermission(journalGroup, FolderExportPermission.class))
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

    public static void removeJournalPermissions(ExperimentAnnotations exptAnnotations, UserPrincipal journalGroup, User user)
    {
        changeJournalPermissions(exptAnnotations, journalGroup, user, false);
    }

    private static ShortURLRecord saveShortURL(ActionURL longURL, String shortUrl, Group journalGroup, User user) throws ValidationException
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

    public static void deleteJournalAccess(ExperimentAnnotations exptAnnotations, Journal journal, User user)
    {
        try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
        {
            removeJournalAccess(exptAnnotations, journal, user);

            transaction.commit();
        }
    }

    private static void removeJournalAccess(ExperimentAnnotations expAnnotations, Journal journal, User user)
    {
        JournalExperiment je = getJournalExperiment(expAnnotations, journal);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JournalId"), journal.getId());
        filter.addCondition(FieldKey.fromParts("ExperimentAnnotationsId"), expAnnotations.getId());
        Table.delete(TargetedMSManager.getTableInfoJournalExperiment(), filter);

        // Try to delete the short copy URL. Since we just deleted the entry in table JournalExperiment
        // that references this URL we should not get a foreign key constraint error.
        tryDeleteShortUrl(je.getShortCopyUrl(), user);
        // Try to delete the short access URL only if the experiment has not yet been copied (accessURL points to journal's folder after copy)
        // OR the access url is no longer referenced in the ExperimentAnnotations table.
        if(je.getCopied() == null || ExperimentAnnotationsManager.getExperimentForShortUrl(je.getShortAccessUrl()) == null)
        {
            tryDeleteShortUrl(je.getShortAccessUrl(), user);
        }



        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
        removeJournalPermissions(expAnnotations, journalGroup, user);
    }

    static void tryDeleteShortUrl(ShortURLRecord shortUrl, User user)
    {
        ShortURLService shortURLService = ShortURLService.get();
        try
        {
            shortURLService.deleteShortURL(shortUrl, user);
        }
        catch(UnauthorizedException e)
        {
            LOG.error("User " + user.getEmail() + " (" + user.getUserId() + ") is not authorized to delete the shortUrlL: " + shortUrl.getShortURL(), e);
        }
        catch(ValidationException e)
        {
            LOG.error("Cannot delete the shortUrl: " + shortUrl.getShortURL(), e);
        }
    }

    public static void updateJournalExperimentUrls(ExperimentAnnotations expAnnotations, Journal journal, JournalExperiment je, String shortAccessUrl, String shortCopyUrl, User user) throws ValidationException
    {
        ShortURLRecord oldAccessUrl = je.getShortAccessUrl();
        ShortURLRecord oldCopyUrl = je.getShortCopyUrl();

        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());

        ShortURLService shortURLService = ShortURLService.get();
        if (!shortAccessUrl.equalsIgnoreCase(oldAccessUrl.getShortURL()))
        {
            // Save the new short access URL
            ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer());
            ShortURLRecord accessUrlRecord = saveShortURL(accessUrl, shortAccessUrl, journalGroup, user);
            je.setShortAccessUrl(accessUrlRecord);
        }

        if (!shortCopyUrl.equalsIgnoreCase(oldCopyUrl.getShortURL()))
        {
            // Save the new short copy URL.
            ActionURL copyUrl = PublishTargetedMSExperimentsController.getCopyExperimentURL(expAnnotations.getId(), journal.getId(), expAnnotations.getContainer());
            ShortURLRecord copyUrlRecord = saveShortURL(copyUrl, shortCopyUrl, null, user);
            je.setShortCopyUrl(copyUrlRecord);

        }

        updateJournalExperiment(je, user);

        // Delete the old short URLs
        if (!shortAccessUrl.equalsIgnoreCase(oldAccessUrl.getShortURL()))
        {
            shortURLService.deleteShortURL(oldAccessUrl, user);
        }

        if (!shortCopyUrl.equalsIgnoreCase(oldCopyUrl.getShortURL()))
        {
            shortURLService.deleteShortURL(oldCopyUrl, user);
        }
    }

    public static void deleteProjectJournal(Container c, User user)
    {
        if(c.isProject())
        {
            // Journals are only associated with 'project' containers
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Project"), c.getEntityId());
            Journal journal = new TableSelector(TargetedMSManager.getTableInfoJournal(), filter, null).getObject(Journal.class);
            if (journal != null)
            {
                delete(journal, user);
            }
        }
    }
}
