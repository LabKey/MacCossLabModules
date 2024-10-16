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
package org.labkey.panoramapublic.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PasswordRule;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.PanoramaPublicSymlinkManager;
import org.labkey.panoramapublic.datacite.DataCiteException;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.datacite.Doi;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeServiceException;
import org.labkey.panoramapublic.query.CatalogEntryManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.SubmissionManager;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterRole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: vsharma
 * Date: 8/28/2014
 * Time: 7:28 AM
 */
public class CopyExperimentFinalTask extends PipelineJob.Task<CopyExperimentFinalTask.Factory>
{
    private CopyExperimentFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        CopyExperimentJobSupport support = job.getJobSupport(CopyExperimentJobSupport.class);
        try
        {
            job.getLogger().info("");
            job.getLogger().info("Finishing up experiment copy.");
            finishUp(job, support);
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t);
        }

        return new RecordedActionSet();
    }

    private void finishUp(PipelineJob job, CopyExperimentJobSupport jobSupport) throws Exception
    {
        Container container = job.getContainer();
        User user = job.getUser();

        Logger log = job.getLogger();
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            // Get the ExperimentAnnotations in the source container
            ExperimentAnnotations sourceExperiment = jobSupport.getExpAnnotations();
            // Get the submission request
            JournalSubmission js = SubmissionManager.getJournalSubmission(sourceExperiment.getId(), jobSupport.getJournal().getId(), sourceExperiment.getContainer());
            if (js == null)
            {
                throw new PipelineJobException("Could not find a submission request for experiment Id " + sourceExperiment.getId() +" and journal Id " + jobSupport.getJournal().getId()
                        + " in the folder '" + sourceExperiment.getContainer() + "'");
            }
            Submission latestCopiedSubmission = js.getLatestCopiedSubmission();
            // If there is a previous copy of this data on Panorama Public get it.  Remove the short url from the previous copy so that it
            // can be assigned to the new copy.
            ExperimentAnnotations previousCopy = getPreviousCopyRemoveShortUrl(latestCopiedSubmission, user);

            // Update the row in panoramapublic.ExperimentAnnotations - set the shortURL and version
            ExperimentAnnotations targetExperiment = updateExperimentAnnotations(container, sourceExperiment, js, user, log);

            // If there is a Panorama Public data catalog entry associated with the previous copy of the experiment, move it to the
            // new container.
            moveCatalogEntry(previousCopy, targetExperiment, user);

            Submission currentSubmission = js.getLatestSubmission();
            if (currentSubmission == null)
            {
                throw new PipelineJobException("Could not find a current submission request");
            }

            // Update the permissions on the source container and the new Panorama Public container
            updatePermissions(UserManager.getUser(currentSubmission.getCreatedBy()), targetExperiment, sourceExperiment, previousCopy, jobSupport.getJournal(), user, log);

            // Update the DataFileUrl in exp.data and FilePathRoot in exp.experimentRun to point to the files in the Panorama Public container file root
            updateDataPathsAndRawDataTab(targetExperiment, user, log);


            // Assign a reviewer account if one was requested
            Pair<User, String> reviewer = assignReviewer(js, targetExperiment, previousCopy, jobSupport, currentSubmission.isKeepPrivate(), user, log);

            // Hide the Data Pipeline tab
            log.info("Hiding the Data Pipeline tab.");
            hideDataPipelineTab(targetExperiment.getContainer());

            js = updateSubmissionAndDeletePreviousCopy(js, currentSubmission, latestCopiedSubmission, targetExperiment, previousCopy, jobSupport, user, log);

            alignSymlinks(job, jobSupport);

            cleanupExportDirectory(user, jobSupport.getExportDir());

            verifySymlinks(sourceExperiment.getContainer(), container, true);
            if (null != previousCopy)
            {
                verifySymlinks(previousCopy.getContainer(), container, false);
            }

            // Assign the ProteomeXchange ID and DOI at the end so that we don't we don't create these identifiers again in case the task has to be rerun due a previous error.
            assignExternalIdentifiers(targetExperiment, previousCopy, jobSupport, log);
            targetExperiment = ExperimentAnnotationsManager.save(targetExperiment, user);
            if (previousCopy != null)
            {
                ExperimentAnnotationsManager.save(previousCopy, user);
            }

            // Create notifications. Do this at the end after everything else is done.
            PanoramaPublicNotification.notifyCopied(sourceExperiment, targetExperiment, jobSupport.getJournal(), js.getJournalExperiment(), currentSubmission,
                    reviewer.first, reviewer.second, user, previousCopy != null /*This is a re-copy if previousCopy exists*/);

            transaction.commit();
        }
    }

    /**
     * Verify symlinks created during the copy process are valid symlinks and point to the target container.
     * @param source    The copied experiment source container
     * @param target    The target container on Panorma Public
     * @param matchingPath  If true the target files will have the same relative path in the target container
     */
    private void verifySymlinks(Container source, Container target, boolean matchingPath)
    {
        FileContentService fcs = FileContentService.get();
        if (fcs != null)
        {
            Path targetRoot = fcs.getFileRootPath(target);
            if (null != targetRoot)
            {
                Path targetFileRoot = Path.of(targetRoot.toString(), File.separator);
                PanoramaPublicSymlinkManager.get().handleContainerSymlinks(source, null, (sourceFile, targetFile, c, u) -> {

                    // valid path
                    if (!FileUtil.isFileAndExists(targetFile))
                    {
                        throw new IllegalStateException("Symlink " + sourceFile + " points to an invalid target " + targetFile);
                    }

                    // we don't want symlinks pointing at other symlinks
                    if (Files.isSymbolicLink(targetFile))
                    {
                        throw new IllegalStateException("Symlink " + sourceFile + " points to another symlink " + targetFile);
                    }

                    // Check if symlink target is in the target container
                    if (!targetFile.startsWith(targetFileRoot))
                    {
                        throw new IllegalStateException("Symlink " + sourceFile + " points to " + targetFile + " which is outside the target container " + target.getPath());
                    }

                    // Symlink targets should have exact same relative path as the source file. Previous versions will not
                    // necessarily have same path so don't check.
                    if (matchingPath)
                    {
                        String targetFileRelative = targetFile.toString().substring(targetFileRoot.toString().length());
                        if (!sourceFile.toString().endsWith(targetFileRelative))
                        {
                            throw new IllegalStateException("Symlink " + sourceFile + " points to " + targetFile + " which is not at the same path as the source file " + sourceFile);
                        }
                    }
                });
            }
        }
    }

    private void cleanupExportDirectory(User user, File directory)
    {
        List<? extends ExpData> datas = ExperimentService.get().getExpDatasUnderPath(directory.toPath(), null, true);
        for (ExpData data : datas)
        {
            data.delete(user);
        }
        FileUtil.deleteDir(directory);
    }

    // After a full file copy is done re-align the experiment project's symlinks to the newest version of the public project
    private void alignSymlinks(PipelineJob job, CopyExperimentJobSupport jobSupport)
    {
        if (jobSupport.getPreviousVersionName() != null)
    {
        FileContentService fcs = FileContentService.get();
        if (fcs != null)
        {
                PanoramaPublicSymlinkManager.get().fireSymlinkUpdateContainer(jobSupport.getPreviousVersionName(),
                        fcs.getFileRoot(job.getContainer()).getPath(), job.getContainer(), job.getUser());
            }
        }
    }

    private void moveCatalogEntry(ExperimentAnnotations previousCopy, ExperimentAnnotations targetExperiment, User user) throws PipelineJobException
    {
        if (previousCopy == null)
        {
            return;
        }
        try
        {
            CatalogEntryManager.moveEntry(previousCopy, targetExperiment, user);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(String.format("Could not move Panorama Public catalog entry from the previous copy of the data in folder '%s' to the new folder '%s'.",
                    previousCopy.getContainer().getPath(), targetExperiment.getContainer().getPath()), e);
        }
    }

    private JournalSubmission updateSubmissionAndDeletePreviousCopy(JournalSubmission js, Submission currentSubmission, Submission lastCopiedSubmission,
                                                                    ExperimentAnnotations targetExperiment, ExperimentAnnotations previousCopy,
                                                                    CopyExperimentJobSupport jobSupport, User user, Logger log) throws PipelineJobException
    {
        if (lastCopiedSubmission != null)
        {
            if (jobSupport.deletePreviousCopy())
            {
                lastCopiedSubmission.setCopiedExperimentId(null);
                SubmissionManager.updateSubmission(lastCopiedSubmission, user);
            }
            else
            {
                // Change the shortAccessUrl for the existing copy of the experiment
                updateLastCopiedExperiment(previousCopy, js, jobSupport.getJournal(), user, log);
            }
        }

        // Update the row in the Submission table -- set the 'copied' timestamp, copiedExperimentId
        log.info("Updating Submission. Setting copiedExperimentId to " + targetExperiment.getId());
        currentSubmission.setCopied(new Date());
        currentSubmission.setCopiedExperimentId(targetExperiment.getId());
        SubmissionManager.updateSubmission(currentSubmission, user);

        // Delete the previous copy
        if (previousCopy != null && jobSupport.deletePreviousCopy())
        {
            log.info("Deleting old folder " + previousCopy.getContainer().getPath());
            Container oldContainer = previousCopy.getContainer();
            try
            {
                ContainerManager.deleteAll(oldContainer, user); // Delete folder and any subfolders
            }
            catch(Exception e)
            {
                // Log exception so that the admin doing the copy can review.
                log.error("Error deleting previous copy of the data in folder " + oldContainer.getPath(), e);
            }
        }

        return SubmissionManager.getJournalSubmission(js.getJournalExperimentId(), jobSupport.getExpAnnotations().getContainer()); // return the updated submission request
    }

    private Pair<User, String> assignReviewer(JournalSubmission js, ExperimentAnnotations targetExperiment, ExperimentAnnotations previousCopy,
                                              CopyExperimentJobSupport jobSupport, boolean keepPrivate, User user, Logger log) throws PipelineJobException
    {
        if (keepPrivate)
        {
            if (previousCopy == null || previousCopy.isPublic())
            {
                ReviewerAndPassword reviewerAndPassword;
                try
                {
                    reviewerAndPassword = createReviewerAccount(jobSupport.getReviewerEmailPrefix(), user, log);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new PipelineJobException("Invalid email for reviewer", e);
                }
                catch (SecurityManager.UserManagementException e)
                {
                    throw new PipelineJobException("Error creating a new account for reviewer", e);
                }
                User reviewer = reviewerAndPassword.getReviewer();
                assignReader(reviewer, targetExperiment.getContainer(), user);
                js.getJournalExperiment().setReviewer(reviewer.getUserId());
                SubmissionManager.updateJournalExperiment(js.getJournalExperiment(), user);

                // Add reviewer to the "Reviewers" group
                // This group already exists in the Panorama Public project on panoramaweb.org.
                // CONSIDER: Make this configurable through the Panorama Public admin console.
                addToGroup(reviewer, "Reviewers", targetExperiment.getContainer().getProject(), log);

                return new Pair<>(reviewer, reviewerAndPassword.getPassword());
            }
        }
        else
        {
            // Assign Site:Guests to reader role
            log.info("Making folder public.");
            assignReader(SecurityManager.getGroup(Group.groupGuests), targetExperiment.getContainer(), user);
        }
        return new Pair<>(null, null);
    }

    private void updateDataPathsAndRawDataTab(ExperimentAnnotations targetExperiment, User user, Logger log)
    {
        FileContentService service = FileContentService.get();
        if (service != null)
        {
            // If there is a "Raw Data" tab in the folder and/or one of its subfolders, fix the configuration of the
            // Files webpart in the tab.
            // TODO: Do we need this? Looks like this was added when some source folders (e.g. from MacCoss lab) were on S3 and
            // we had to update the file root property on the Files webpart in the Raw Data tab in the Panorama Public copy.
            log.info("Updating the 'Raw Data' tab configuration");
            updateRawDataTabConfig(targetExperiment.getContainer(), service, user);
        }
    }

    private void assignDoi(CopyExperimentJobSupport jobSupport, Logger log, ExperimentAnnotations targetExperiment, ExperimentAnnotations previousCopy) throws PipelineJobException
    {
        if (jobSupport.assignDoi())
        {
            if (previousCopy != null && previousCopy.getDoi() != null)
            {
                log.info("Copying DOI from the previous copy of the data.");
                targetExperiment.setDoi(previousCopy.getDoi());
                log.info("Removing DOI from the previous copy");
                previousCopy.setDoi(null);
            }
            else
            {
                log.info("Assigning a DOI.");
                try
                {
                    assignDoi(targetExperiment, jobSupport.useDataCiteTestApi());
                    log.info("Assigned DOI: " + targetExperiment.getDoi());
                }
                catch(DataCiteException e)
                {
                    throw new PipelineJobException("Could not assign a DOI.", e);
                }
            }
        }
    }

    private void assignPxId(CopyExperimentJobSupport jobSupport, Logger log, ExperimentAnnotations targetExperiment, ExperimentAnnotations previousCopy) throws PipelineJobException
    {
        if (jobSupport.assignPxId() // We can get isPxidRequested from the Submission row but sometimes we may have to override that setting.
                                   // This can happen, e.g. if the user submitted .wiff files without any matching .wiff.scan files because their instrument
                                   // was setup to capture everything in the .wiff files (Paulovich lab).
                                   // Let the admin who is copying the data make the decision.
        )
        {
            if (previousCopy != null && previousCopy.getPxid() != null)
            {
                log.info("Copying ProteomeXchange ID from the previous copy of the data.");
                targetExperiment.setPxid(previousCopy.getPxid());
            }
            else
            {
                log.info("Assigning a ProteomeXchange ID.");
                try
                {
                    assignPxId(targetExperiment, jobSupport.usePxTestDb());
                    log.info("Assigned ProteomeXchange ID: " + targetExperiment.getPxid());
                }
                catch(ProteomeXchangeServiceException e)
                {
                    throw new PipelineJobException("Could not get a ProteomeXchange ID.", e);
                }
            }
        }
    }

    private ExperimentAnnotations getPreviousCopyRemoveShortUrl(Submission latestCopiedSubmission, User user) throws PipelineJobException
    {
        if (latestCopiedSubmission != null)
        {
            ExperimentAnnotations previousCopy = ExperimentAnnotationsManager.get(latestCopiedSubmission.getCopiedExperimentId());
            if (previousCopy == null)
            {
                throw new PipelineJobException("Could not find an entry for the previous copy of the experiment.  " +
                        "Previous experiment ID " + latestCopiedSubmission.getCopiedExperimentId());
            }
            previousCopy.setShortUrl(null); // Set the shortUrl to null so we don't get a unique constraint violation when assigning this url to the new copy
            return ExperimentAnnotationsManager.save(previousCopy, user);
        }
        return null;
    }

    @NotNull
    private ExperimentAnnotations updateExperimentAnnotations(Container targetContainer, ExperimentAnnotations sourceExperiment, JournalSubmission js,
                                                              User user, Logger log) throws PipelineJobException
    {
        log.info("Updating TargetedMS experiment entry in target folder " + targetContainer.getPath());
        ExperimentAnnotations targetExperiment = ExperimentAnnotationsManager.getExperimentInContainer(targetContainer);
        if (targetExperiment == null)
        {
            throw new PipelineJobException("ExperimentAnnotations row does not exist in target folder: '" + targetContainer.getPath() + "'");
        }

        targetExperiment.setShortUrl(js.getShortAccessUrl());
        Integer currentVersion = ExperimentAnnotationsManager.getMaxVersionForExperiment(sourceExperiment.getId());
        int version =  currentVersion == null ? 1 : currentVersion + 1;
        log.info("Setting version on new experiment to " + version);
        targetExperiment.setDataVersion(version);

        targetExperiment = ExperimentAnnotationsManager.save(targetExperiment, user);

        // Update the target of the short access URL to the journal's copy of the experiment.
        log.info("Updating permanent link to point to the new copy of the data.");
        try
        {
            SubmissionManager.updateAccessUrlTarget(js.getShortAccessUrl(), targetExperiment, user);
        }
        catch (ValidationException e)
        {
            throw new PipelineJobException("Error updating the target of the permanent link '" + js.getShortAccessUrl().getShortURL() + "' to '"
                    + targetExperiment.getContainer().getPath() + "'", e);
        }

        return targetExperiment;
    }


    private void assignExternalIdentifiers(ExperimentAnnotations targetExperiment, ExperimentAnnotations previousCopy, CopyExperimentJobSupport jobSupport,
                                           Logger log) throws PipelineJobException
    {
        assignPxId(jobSupport, log, targetExperiment, previousCopy);
        assignDoi(jobSupport, log, targetExperiment, previousCopy);
    }

    private void updatePermissions(User formSubmitter, ExperimentAnnotations targetExperiment, ExperimentAnnotations sourceExperiment, ExperimentAnnotations previousCopy,
                                   Journal journal, User pipelineJobUser, Logger log)
    {
        // Remove the copy permissions given to the journal.
        log.info("Removing copy permissions given to " + journal.getName());
        Group journalGroup = SecurityManager.getGroup(journal.getLabkeyGroupId());
        JournalManager.removeJournalPermissions(sourceExperiment, journalGroup, pipelineJobUser);

        // Give read permissions to the authors (all users that are folder admins)
        log.info("Adding read permissions to all users that are folder admins in the source folder.");
        Set<User> allReaders = new HashSet<>(getUsersWithRole(sourceExperiment.getContainer(), RoleManager.getRole(FolderAdminRole.class)));

        // If the submitter and lab head user are members of a permission group in the source folder, they will not
        // be included in the set of users returned by getUsersWithRole(). So add them here
        allReaders.add(formSubmitter);  // User that submitted the form. Can be different from the user selected as the data submitter
        allReaders.add(targetExperiment.getSubmitterUser());
        allReaders.add(targetExperiment.getLabHeadUser());

        if (previousCopy != null)
        {
            // Users that had read access to the previous copy should be given read access to the new copy. This will include the reviewer
            // account if one was created for the previous copy.
            log.info("Adding read permissions to all users that had read access to previous copy.");
            allReaders.addAll(getUsersWithRole(previousCopy.getContainer(), RoleManager.getRole(ReaderRole.class)));
        }

        Container target = targetExperiment.getContainer();
        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(target, target.getPolicy());
        allReaders.stream().filter(Objects::nonNull).forEach(u ->
        {
            log.info("Assigning " + ReaderRole.class.getSimpleName() + " to " + u.getEmail());
            newPolicy.addRoleAssignment(u, ReaderRole.class);
        });


        // Assign the PanoramaPublicSubmitterRole so that the submitter or lab head is able to make the copied folder public, and add publication information.
        assignPanoramaPublicSubmitterRole(newPolicy, log, targetExperiment.getSubmitterUser(), targetExperiment.getLabHeadUser(),
                formSubmitter); // User that submitted the form. Can be different from the user selected as the data submitter

        SecurityPolicyManager.savePolicy(newPolicy, pipelineJobUser);

        addToSubmittersGroup(target.getProject(), log, targetExperiment.getSubmitterUser(), targetExperiment.getLabHeadUser(), formSubmitter);
    }

    private void assignPanoramaPublicSubmitterRole(MutableSecurityPolicy policy, Logger log, User... users)
    {
        Arrays.stream(users).filter(Objects::nonNull).collect(Collectors.toSet()).forEach(user -> {
            log.info("Assigning " + PanoramaPublicSubmitterRole.class.getSimpleName() + " to " + user.getEmail());
            policy.addRoleAssignment(user, PanoramaPublicSubmitterRole.class, false);
        });
    }

    private void addToSubmittersGroup(Container project, Logger log, User... users)
    {
        String groupName = "Panorama Public Submitters";
        Group group = getGroup(groupName, project, log);
        if (group != null)
        {
            Arrays.stream(users).filter(Objects::nonNull).collect(Collectors.toSet()).forEach(user -> {
                // This group already exists in the Panorama Public project on panoramaweb.org.
                // CONSIDER: Make this configurable through the Panorama Public admin console.
                addToGroup(user, group, log);
            });
        }
    }

    private void addToGroup(User user, String groupName, Container project, Logger log)
    {
        Group group = getGroup(groupName, project, log);
        if (group != null)
        {
            addToGroup(user, group, log);
        }
    }

    private void addToGroup(User user, Group group, Logger log)
    {
        try
        {
            if (SecurityManager.getGroupMembers(group, MemberType.ACTIVE_USERS).contains(user))
            {
                log.info("User " + user.getEmail() + " is already a member of group " + group.getName());
            }
            else
            {
                log.info("Adding user " + user.getEmail() + " to group " + group.getName());
                SecurityManager.addMember(group, user);
            }
        }
        catch (InvalidGroupMembershipException e)
        {
            log.warn("Unable to add user " + user.getEmail() + " to group " + group.getName(), e);
        }
    }

    private Group getGroup(String groupName, Container project, Logger log)
    {
        Integer groupId = SecurityManager.getGroupId(project, groupName, false);
        Group group = null;
        if (groupId != null)
        {
            group = SecurityManager.getGroup(groupId);
        }
        if (group == null)
        {
            log.warn("Did not find a security group with name " + groupName + " in the project " + project.getName());
        }
        return group;
    }

    private Set<User> getUsersWithRole(Container container, Role role)
    {
        SecurityPolicy securityPolicy = container.getPolicy();
        return securityPolicy.getAssignments().stream()
                .filter(r -> r.getRole().equals(role)
                        && UserManager.getUser(r.getUserId()) != null) // Ignore user groups
                .map(r -> UserManager.getUser(r.getUserId())).collect(Collectors.toSet());

    }

    private ReviewerAndPassword createReviewerAccount(String reviewerEmailPrefix, User user, Logger log) throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
    {
        if(StringUtils.isBlank(reviewerEmailPrefix))
        {
            reviewerEmailPrefix = PanoramaPublicController.PANORAMA_REVIEWER_PREFIX;
        }

        String domain = "@proteinms.net"; // TODO: configure this in admin settings

        ValidEmail email = new ValidEmail(reviewerEmailPrefix + domain);
        int num = 1;
        while(UserManager.getUser(email) != null)
        {
            email = new ValidEmail(reviewerEmailPrefix + num + domain);
            num++;
        }

        log.info("Creating a reviewer account.");
        SecurityManager.NewUserStatus newUser = SecurityManager.addUser(email, user, true);
        log.info("Created reviewer with email: " + newUser.getUser().getEmail());

        log.info("Generating password.");
        String password = createPassword(newUser.getUser());
        SecurityManager.setPassword(email, password);
        log.info("Set reviewer password successfully.");

        return new ReviewerAndPassword(newUser.getUser(), password);
    }

    private void assignReader(UserPrincipal reader, Container target, User pipelineJobUser)
    {
        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(target, target.getPolicy());
        newPolicy.addRoleAssignment(reader, ReaderRole.class);
        SecurityPolicyManager.savePolicy(newPolicy, pipelineJobUser);
    }

    private static class ReviewerAndPassword
    {
        private final User _reviewer;
        private final String _password;

        public ReviewerAndPassword(@NotNull User reviewer, @NotNull String password)
        {
            _reviewer = reviewer;
            _password = password;
        }

        public User getReviewer()
        {
            return _reviewer;
        }

        public String getPassword()
        {
            return _password;
        }
    }

    private static String createPassword(User user)
    {
        String password;
        do {
            password = PasswordGenerator.generate();
        } while (!PasswordRule.Strong.isValidForLogin(password, user, null));

        return password;
    }

    private static class PasswordGenerator
    {
        private static final List<Character> LOWERCASE = "abcdefghijklmnopqrstuvwxyz".chars().mapToObj(c -> (char)c).collect(Collectors.toList());
        private static final List<Character> UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".chars().mapToObj(c -> (char)c).collect(Collectors.toList());
        private static final List<Character> DIGITS = "0123456789".chars().mapToObj(c -> (char)c).collect(Collectors.toList());
        private static final List<Character> SYMBOLS = "!@#$%^&*+=?".chars().mapToObj(c -> (char)c).collect(Collectors.toList());

        private static final int PASSWORD_LEN = 14;

        public static String generate()
        {
            SecureRandom random = new SecureRandom();

            List<Character> passwordChars = new ArrayList<>(PASSWORD_LEN);
            List<Character> allChars = new ArrayList<>();

            // Initialize the list with all possible characters
            allChars.addAll(LOWERCASE);
            allChars.addAll(UPPERCASE);
            allChars.addAll(DIGITS);
            allChars.addAll(SYMBOLS);

            // Ensure that there is at least one character from each character category.
            addChar(LOWERCASE, passwordChars, allChars, random);
            addChar(UPPERCASE, passwordChars, allChars, random);
            addChar(DIGITS, passwordChars, allChars, random);
            addChar(SYMBOLS, passwordChars, allChars, random);

            // Shuffle the list of remaining characters
            Collections.shuffle(allChars);

            // Add more characters until we are at the desired password length
            while (passwordChars.size() < PASSWORD_LEN)
            {
                addChar(allChars, passwordChars, allChars, random);
            }

            Collections.shuffle(passwordChars);

            return passwordChars.stream().map(String::valueOf).collect(Collectors.joining());
        }

        /**
         * Pick a random character from the given character category, add it to the list of password characters, and
         * remove it from the list of all available characters to ensure character uniqueness in the password.
         */
        private static void addChar(List<Character> categoryChars, List<Character> passwordChars, List<Character> allChars, SecureRandom random)
        {
            int randomIdx = random.nextInt(0, categoryChars.size());
            Character selected = categoryChars.get(randomIdx);
            passwordChars.add(selected);
            allChars.remove(selected); // Remove from the list of all available chars so that we have unique characters in the password
        }
    }

    private void assignPxId(ExperimentAnnotations targetExpt, boolean useTestDb) throws ProteomeXchangeServiceException
    {
        PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(ProteomeXchangeService.PX_CREDENTIALS, false);
        if (map != null)
        {
            String user = map.get(ProteomeXchangeService.PX_USER);
            String password = map.get(ProteomeXchangeService.PX_PASSWORD);
            String pxId = ProteomeXchangeService.getPxId(useTestDb, user, password);
            targetExpt.setPxid(pxId);
        }
        else
        {
            throw new ProteomeXchangeServiceException("Could not find ProteomeXchange credentials");
        }
    }

    private void assignDoi(ExperimentAnnotations targetExpt, boolean useTestDb) throws DataCiteException
    {
        Doi doi = DataCiteService.create(useTestDb, targetExpt);
        targetExpt.setDoi(doi.getDoi());
    }

    private void updateRawDataTabConfig(Container c, FileContentService service, User user)
    {
        Set<Container> children = ContainerManager.getAllChildren(c); // Includes parent
        for(Container child: children)
        {
            updateRawDataTab(child, service, user);
        }
    }

    private void hideDataPipelineTab(Container c)
    {
        Set<Container> children = ContainerManager.getAllChildren(c); // Includes parent
        for(Container child: children)
        {
            Portal.hidePage(child, "Data Pipeline");
        }
    }

    private void updateRawDataTab(Container c, FileContentService service, User user)
    {
        List<Portal.WebPart> rawDataTabParts = Portal.getEditableParts(c, TargetedMSService.RAW_FILES_TAB);
        if(rawDataTabParts.size() == 0)
        {
            return; // Nothing to do if there is no "Raw Data" tab.
        }
        for(Portal.WebPart wp: rawDataTabParts)
        {
            if(FilesWebPart.PART_NAME.equals(wp.getName()))
            {
                PanoramaPublicController.configureRawDataTab(wp, c, service);
                Portal.updatePart(user, wp);
            }
        }
    }

    private void updateLastCopiedExperiment(ExperimentAnnotations previousCopy, JournalSubmission js, Journal journal, User user, Logger log) throws PipelineJobException
    {
        if (previousCopy.getDataVersion() == null)
        {
            throw new PipelineJobException("Version is not set on the last copied experiment; Id: " + previousCopy.getId());
        }

        // Append a version to the original short URL
        String versionedShortUrl = js.getShortAccessUrl().getShortURL() + "_v" + previousCopy.getDataVersion();
        log.info("Creating a new versioned URL for the previous copy of the data: " + versionedShortUrl);
        ShortURLService shortUrlService = ShortURLService.get();
        ShortURLRecord shortURLRecord = shortUrlService.resolveShortURL(versionedShortUrl);
        if (shortURLRecord != null)
        {
            throw new PipelineJobException("Error appending a version to the short URL.  The short URL is already in use " + "'" + versionedShortUrl + "'");
        }
        // Save the new short access URL
        ActionURL projectUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(previousCopy.getContainer());
        ShortURLRecord newShortUrl;
        try
        {
            newShortUrl = JournalManager.saveShortURL(projectUrl, versionedShortUrl, journal, user);
        }
        catch (ValidationException e)
        {
            throw new PipelineJobException("Error saving shortUrl '" + versionedShortUrl + "'", e);
        }

        log.info("Setting the permanent link on the previous copy to " + newShortUrl.getShortURL());
        previousCopy.setShortUrl(newShortUrl);

        ExperimentAnnotationsManager.save(previousCopy, user);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(CopyExperimentFinalTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new CopyExperimentFinalTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "FINISH EXPERIMENT COPY";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
