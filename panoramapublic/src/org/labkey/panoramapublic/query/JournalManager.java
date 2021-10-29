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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
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
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
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
    private static final String PUBLIC_DATA_USER = "Public Data User";
    private static final String USER_ID = "User Id";
    private static final String USER_PASSWORD = "User Password";

    private static final Logger LOG = LogHelper.getLogger(JournalManager.class, "Messages about querying Journal (e.g. Panorama Public) information");

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

    public static @Nullable Journal getJournal(@NotNull Container project)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Project"), project.getEntityId());
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal(), filter, null).getObject(Journal.class);
    }

    public static boolean isJournalProject(Container project)
    {
        return new TableSelector(PanoramaPublicManager.getTableInfoJournal(),
                new SimpleFilter(FieldKey.fromParts("project"), project),
                null).exists();
    }

    public static List<Journal> getJournalsForExperiment(int expAnnotationsId)
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT j.* FROM ");
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
        // The user should have export permissions on the folder. FolderExportPermission given to the journal is revoked after the folder is copied to the journal project.
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

    public static void delete(Journal journal, User user)
    {
        // Get a list of experiments this journal has access to.
        List<ExperimentAnnotations> expAnnotations = getExperimentsForJournal(journal.getId());
        for(ExperimentAnnotations expAnnotation: expAnnotations)
        {
            removeJournalAccess(expAnnotation, journal, user);
        }
        SubmissionManager.deleteAllSubmissionsForJournal(journal.getId());

        Table.delete(PanoramaPublicManager.getTableInfoJournal(), new SimpleFilter(FieldKey.fromParts("id"), journal.getId()));
    }

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

    public static void setupJournalAccess(ExperimentAnnotations exptAnnotations, Journal journal, User user) throws ValidationException
    {
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());

        // Grant the journal group read and copy access to the source folder and subfolders
        addJournalPermissions(exptAnnotations, journalGroup, user);
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
                    User user = UserManager.getUser(role.getUserId());
                    if (user != null)
                    {
                        newPolicy.addRoleAssignment(user, FolderAdminRole.class);
                    }
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

    public static ShortURLRecord saveShortURL(ActionURL longURL, String shortUrl, @Nullable Journal journal, User user) throws ValidationException
    {
        Group journalGroup = journal != null ? org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId()) : null;
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
        Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(journal.getLabkeyGroupId());
        removeJournalPermissions(expAnnotations, journalGroup, user);
    }

    static void tryDeleteShortUrl(ShortURLRecord shortUrl, User user)
    {
        if (shortUrl == null)
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

    public static @Nullable PublicDataUser getPublicDataUser(@NotNull Journal journal)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(journal.getProject(), PUBLIC_DATA_USER, false);
        if(map != null && map.get(USER_ID) != null)
        {
            User user = UserManager.getUser(Integer.parseInt(map.get(USER_ID)));
            String password = map.get(USER_PASSWORD);
            if (user != null && password != null)
            {
                return new PublicDataUser(user, password);
            }
        }
        return null;
    }

    public static void savePublicDataUser(@NotNull Journal journal, @NotNull User user, @NotNull String password)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(journal.getProject(), PUBLIC_DATA_USER, true);
        map.put(USER_ID, String.valueOf(user.getUserId()));
        map.put(USER_PASSWORD, password);
        map.save();
    }

    public static final class PublicDataUser
    {
        private final User _user;
        private final String _password;

        public PublicDataUser(User user, String password)
        {
            _user = user;
            _password = password;
        }

        public User getUser()
        {
            return _user;
        }

        public String getEmail()
        {
            return _user.getEmail();
        }

        public String getPassword()
        {
            return _password;
        }
    }
}
