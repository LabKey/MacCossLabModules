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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.MutableSecurityPolicy;
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
import org.labkey.api.targetedms.ITargetedMSRun;
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
import org.labkey.panoramapublic.datacite.DataCiteException;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.datacite.Doi;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.Submission;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeServiceException;
import org.labkey.panoramapublic.query.CatalogEntryManager;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.ModificationInfoManager;
import org.labkey.panoramapublic.query.SpecLibInfoManager;
import org.labkey.panoramapublic.query.SubmissionManager;
import org.labkey.panoramapublic.query.modification.ExperimentIsotopeModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentStructuralModInfo;
import org.labkey.panoramapublic.security.PanoramaPublicSubmitterRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
        // Get the experiment that was just created in the target folder as part of the folder import.
        Container container = job.getContainer();
        User user = job.getUser();
        List<? extends ExpExperiment> experiments = ExperimentService.get().getExperiments(container, user, false, false);
        if(experiments.size() == 0)
        {
            throw new PipelineJobException("No experiments found in the folder " + container.getPath());
        }
        else if (experiments.size() >  1)
        {
            throw new PipelineJobException("More than one experiment found in the folder " + container.getPath());
        }
        ExpExperiment experiment = experiments.get(0);

        // Get a list of all the ExpRuns imported to subfolders of this folder.
        int[] runRowIdsInSubfolders = getAllExpRunRowIdsInSubfolders(container);

        Logger log = job.getLogger();
        try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
        {
            if(runRowIdsInSubfolders.length > 0)
            {
                // The folder export and import process, creates a new experiment in exp.experiment.
                // However, only runs in the top-level folder are added to this experiment.
                // We will add to the experiment all the runs imported to subfolders.
                log.info("Adding runs imported in subfolders.");
                ExperimentAnnotationsManager.addSelectedRunsToExperiment(experiment, runRowIdsInSubfolders, user);
            }

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

            // Create a new row in panoramapublic.ExperimentAnnotations and link it to the new experiment created during folder import.
            ExperimentAnnotations targetExperiment = createNewExperimentAnnotations(experiment, sourceExperiment, js, previousCopy, jobSupport, user, log);

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


            // Copy any Spectral library information provided by the user in the source container
            copySpecLibInfos(sourceExperiment, targetExperiment, user);

            // Copy any modifications related information provided by the user in the source container
            copyModificationInfos(sourceExperiment, targetExperiment, user);

            // Assign a reviewer account if one was requested
            Pair<User, String> reviewer = assignReviewer(js, targetExperiment, previousCopy, jobSupport, currentSubmission.isKeepPrivate(), user, log);

            // Hide the Data Pipeline tab
            log.info("Hiding the Data Pipeline tab.");
            hideDataPipelineTab(targetExperiment.getContainer());

            js = updateSubmissionAndDeletePreviousCopy(js, currentSubmission, latestCopiedSubmission, targetExperiment, previousCopy, jobSupport, user, log);

            alignSymlinks(job, jobSupport);

            FileUtil.deleteDir(jobSupport.getExportDir());

            // Create notifications. Do this at the end after everything else is done.
            PanoramaPublicNotification.notifyCopied(sourceExperiment, targetExperiment, jobSupport.getJournal(), js.getJournalExperiment(), currentSubmission,
                    reviewer.first, reviewer.second, user, previousCopy != null /*This is a re-copy if previousCopy exists*/);

            transaction.commit();
        }
    }

    // After a full file copy is done re-align the experiment project's symlinks to the newest version of the public project
    private void alignSymlinks(PipelineJob job, CopyExperimentJobSupport jobSupport)
    {
        if (jobSupport.getPreviousVersionName() != null)
        {
            FileContentService fcs = FileContentService.get();
            if (fcs != null)
            {
                PanoramaPublicManager.get().fireSymlinkUpdateContainer(jobSupport.getPreviousVersionName(), fcs.getFileRoot(job.getContainer()).getPath());
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

    private void copySpecLibInfos(ExperimentAnnotations sourceExperiment, ExperimentAnnotations targetExperiment, User user)
    {
        List<SpecLibInfo> specLibInfos = SpecLibInfoManager.getForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (SpecLibInfo info: specLibInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            SpecLibInfoManager.save(info, user);
        }
    }

    private void copyModificationInfos(ExperimentAnnotations sourceExperiment, ExperimentAnnotations targetExperiment, User user)
    {
        List<ExperimentStructuralModInfo> strModInfos = ModificationInfoManager.getStructuralModInfosForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (ExperimentStructuralModInfo info: strModInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            ModificationInfoManager.saveStructuralModInfo(info, user);
        }

        List<ExperimentIsotopeModInfo> isotopeModInfos = ModificationInfoManager.getIsotopeModInfosForExperiment(sourceExperiment.getId(), sourceExperiment.getContainer());
        for (ExperimentIsotopeModInfo info: isotopeModInfos)
        {
            info.setId(0);
            info.setExperimentAnnotationsId(targetExperiment.getId());
            ModificationInfoManager.saveIsotopeModInfo(info, user);
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
                String reviewerPassword = createPassword();
                User reviewer;
                try
                {
                    reviewer = createReviewerAccount(jobSupport.getReviewerEmailPrefix(), reviewerPassword, user, log);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new PipelineJobException("Invalid email for reviewer", e);
                }
                catch (SecurityManager.UserManagementException e)
                {
                    throw new PipelineJobException("Error creating a new account for reviewer", e);
                }
                assignReader(reviewer, targetExperiment.getContainer());
                js.getJournalExperiment().setReviewer(reviewer.getUserId());
                SubmissionManager.updateJournalExperiment(js.getJournalExperiment(), user);

                // Add reviewer to the "Reviewers" group
                // This group already exists in the Panorama Public project on panoramaweb.org.
                // CONSIDER: Make this configurable through the Panorama Public admin console.
                addToGroup(reviewer, "Reviewers", targetExperiment.getContainer().getProject(), log);

                return new Pair<>(reviewer, reviewerPassword);
            }
        }
        else
        {
            // Assign Site:Guests to reader role
            log.info("Making folder public.");
            assignReader(SecurityManager.getGroup(Group.groupGuests), targetExperiment.getContainer());
        }
        return new Pair<>(null, null);
    }

    private void updateDataPathsAndRawDataTab(ExperimentAnnotations targetExperiment, User user, Logger log) throws BatchValidationException, PipelineJobException
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

            // DataFileUrl in exp.data and FilePathRoot in exp.experimentRun point to locations in the 'export' directory.
            // We are now copying all files from the source container to the target container file root. Update the paths
            // to point to locations in the target container file root, and delete the 'export' directory
            if (!updateDataPaths(targetExperiment.getContainer(), service, user, log))
            {
                throw new PipelineJobException("Unable to update all data file paths.");
            }
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
    private ExperimentAnnotations createNewExperimentAnnotations(ExpExperiment experiment, ExperimentAnnotations sourceExperiment, JournalSubmission js,
                                                                 ExperimentAnnotations previousCopy, CopyExperimentJobSupport jobSupport,
                                                                 User user, Logger log) throws PipelineJobException
    {
        log.info("Creating a new TargetedMS experiment entry in panoramapublic.ExperimentAnnotations.");
        ExperimentAnnotations targetExperiment = createExperimentCopy(sourceExperiment);
        targetExperiment.setExperimentId(experiment.getRowId());
        targetExperiment.setContainer(experiment.getContainer());
        targetExperiment.setSourceExperimentId(sourceExperiment.getId());
        targetExperiment.setSourceExperimentPath(sourceExperiment.getContainer().getPath());
        targetExperiment.setShortUrl(js.getShortAccessUrl());
        Integer currentVersion = ExperimentAnnotationsManager.getMaxVersionForExperiment(sourceExperiment.getId());
        int version =  currentVersion == null ? 1 : currentVersion + 1;
        log.info("Setting version on new experiment to " + version);
        targetExperiment.setDataVersion(version);

        assignPxId(jobSupport, log, targetExperiment, previousCopy);
        assignDoi(jobSupport, log, targetExperiment, previousCopy);

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

    private ExperimentAnnotations createExperimentCopy(ExperimentAnnotations source)
    {
        ExperimentAnnotations copy = new ExperimentAnnotations();
        copy.setTitle(source.getTitle());
        copy.setExperimentDescription(source.getExperimentDescription());
        copy.setSampleDescription(source.getSampleDescription());
        copy.setOrganism(source.getOrganism());
        copy.setInstrument(source.getInstrument());
        copy.setSpikeIn(source.getSpikeIn());
        copy.setCitation(source.getCitation());
        copy.setAbstract(source.getAbstract());
        copy.setPublicationLink(source.getPublicationLink());
        copy.setIncludeSubfolders(source.isIncludeSubfolders());
        copy.setKeywords(source.getKeywords());
        copy.setLabHead(source.getLabHead());
        copy.setSubmitter(source.getSubmitter());
        copy.setLabHeadAffiliation(source.getLabHeadAffiliation());
        copy.setSubmitterAffiliation(source.getSubmitterAffiliation());
        copy.setPubmedId(source.getPubmedId());
        return copy;
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
        List<User> sourceFolderAdmins = getUsersWithRole(sourceExperiment.getContainer(), RoleManager.getRole(FolderAdminRole.class));

        Container target = targetExperiment.getContainer();
        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(target, target.getPolicy());
        for (User folderAdmin: sourceFolderAdmins)
        {
            newPolicy.addRoleAssignment(folderAdmin, ReaderRole.class);
        }

        // Assign the PanoramaPublicSubmitterRole so that the submitter or lab head is able to make the copied folder public, and add publication information.
        assignPanoramaPublicSubmitterRole(newPolicy, log, targetExperiment.getSubmitterUser(), targetExperiment.getLabHeadUser(),
                formSubmitter); // User that submitted the form. Can be different from the user selected as the data submitter

        addToSubmittersGroup(target.getProject(), log, targetExperiment.getSubmitterUser(), targetExperiment.getLabHeadUser(), formSubmitter);


        if (previousCopy != null)
        {
            // Users that had read access to the previous copy should be given read access to the new copy. This will include the reviewer
            // account if one was created for the previous copy.
            log.info("Adding read permissions to all users that had read access to previous copy.");
            List<User> previousCopyReaders = getUsersWithRole(previousCopy.getContainer(), RoleManager.getRole(ReaderRole.class));
            previousCopyReaders.forEach(u -> newPolicy.addRoleAssignment(u, ReaderRole.class));
        }

        SecurityPolicyManager.savePolicy(newPolicy);
    }

    private void assignPanoramaPublicSubmitterRole(MutableSecurityPolicy policy, Logger log, User... users)
    {
        Arrays.stream(users).filter(Objects::nonNull).forEach(user -> {
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
            Arrays.stream(users).filter(Objects::nonNull).forEach(user -> {
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
            return;
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

    private List<User> getUsersWithRole(Container container, Role role)
    {
        SecurityPolicy securityPolicy = container.getPolicy();
        return securityPolicy.getAssignments().stream()
                .filter(r -> r.getRole().equals(role)
                        && UserManager.getUser(r.getUserId()) != null) // Ignore user groups
                .map(r -> UserManager.getUser(r.getUserId())).collect(Collectors.toList());

    }

    private User createReviewerAccount(String reviewerEmailPrefix, String password, User user, Logger log) throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
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
        SecurityManager.setPassword(email, password);

        log.info("Created reviewer with email: User " + newUser.getUser().getEmail());
        return newUser.getUser();
    }

    private void assignReader(UserPrincipal reader, Container target)
    {
        MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(target, target.getPolicy());
        newPolicy.addRoleAssignment(reader, ReaderRole.class);
        SecurityPolicyManager.savePolicy(newPolicy);
    }

    public static String createPassword()
    {
        return RandomStringUtils.randomAlphabetic(8);
    }

    private void assignPxId(ExperimentAnnotations targetExpt, boolean useTestDb) throws ProteomeXchangeServiceException
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(ProteomeXchangeService.PX_CREDENTIALS, false);
        if(map != null)
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

    private boolean updateDataPaths(Container target, FileContentService service, User user, Logger logger) throws BatchValidationException
    {
        List<ExpRun> allRuns = getAllExpRuns(target);

        for(ExpRun run: allRuns)
        {
            Path fileRootPath = service.getFileRootPath(run.getContainer(), FileContentService.ContentType.files);
            if(fileRootPath == null || !Files.exists(fileRootPath))
            {
                logger.error("File root path for container " + run.getContainer().getPath() + " does not exist: " + fileRootPath);
                return false;
            }

            List<ExpData> allData = new ArrayList<>(run.getAllDataUsedByRun());

            ITargetedMSRun tmsRun = PanoramaPublicManager.getRunByLsid(run.getLSID(), run.getContainer());
            if(tmsRun == null)
            {
                logger.error("Could not find a targetedms run for exprun: " + run.getLSID() + " in container " + run.getContainer());
                return false;
            }
            logger.info("Updating dataFileUrls for run " + tmsRun.getFileName() + "; targetedms run ID " + tmsRun.getId());

            // list returned by run.getAllDataUsedByRun() may not include rows in exp.data that do not have a corresponding row in exp.dataInput.
            // This is known to happen for entries for .skyd datas.
            if(!addMissingDatas(allData, tmsRun, logger))
            {
                return false;
            }

            for(ExpData data: allData)
            {
                String fileName = FileUtil.getFileName(data.getFilePath());
                Path newDataPath = fileRootPath.resolve(fileName);
                if(!Files.exists(newDataPath))
                {
                    // This may be the .skyd file which is inside the exploded parent directory
                    String parentDir = tmsRun.getBaseName();
                    newDataPath = fileRootPath.resolve(parentDir) // Name of the exploded directory
                                              .resolve(fileName); // Name of the file
                }
                if(Files.exists(newDataPath))
                {
                    logger.info("Updating dataFileUrl...");
                    logger.info("from: " + data.getDataFileURI().toString());
                    logger.info("to: " + newDataPath.toUri());
                    data.setDataFileURI(newDataPath.toUri());
                    data.save(user);
                }
                else
                {
                    logger.error("Data path does not exist: " + newDataPath);
                    return false;
                }
            }

            run.setFilePathRootPath(fileRootPath);
            run.save(user);
        }
        return true;
    }

    private boolean addMissingDatas(List<ExpData> allData, ITargetedMSRun tmsRun, Logger logger)
    {
        Integer skyZipDataId = tmsRun.getDataId();
        boolean skyZipDataIncluded = false;

        Integer skydDataId = tmsRun.getSkydDataId();
        boolean skydDataIncluded = false;

        if(skydDataId == null)
        {
            // Document may not have a skydDataId, for example, if it does not have any imported replicates. So no .skyd file
            logger.info("Targetedms run " + tmsRun.getId() + " in container " + tmsRun.getContainer() + " does not have a skydDataId." );
            skydDataIncluded = true; // Set to true so we don't try to add it later
        }

        for(ExpData data: allData)
        {
            if(data.getRowId() == skyZipDataId)
            {
                skyZipDataIncluded = true;
            }
            else if((skydDataId != null) && (data.getRowId() == skydDataId))
            {
                skydDataIncluded = true;
            }
        }

        if(!skyZipDataIncluded)
        {
            ExpData skyZipData = ExperimentService.get().getExpData(skyZipDataId);
            if(skyZipData == null)
            {
                logger.error("Could not find expdata for dataId (.sky.zip): " + tmsRun.getDataId() +" for runId" + tmsRun.getId() + " in container " + tmsRun.getContainer());
                return false;
            }
            logger.info("Adding ExpData for .sky.zip file");
            allData.add(skyZipData);
        }

        if(!skydDataIncluded)
        {
            ExpData skydData = ExperimentService.get().getExpData(tmsRun.getSkydDataId());
            if (skydData == null)
            {
                logger.error("Could not find expdata for skydDataId (.skyd): " + tmsRun.getDataId() + " for runId " + tmsRun.getId() + " in container " + tmsRun.getContainer());
                return false;
            }
            logger.info("Adding ExpData for .skyd file");
            allData.add(skydData);
        }
        return true;
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

    private List<ExpRun> getAllExpRuns(Container container)
    {
        Set<Container> children = ContainerManager.getAllChildren(container);
        ExperimentService expService = ExperimentService.get();
        List<ExpRun> allRuns = new ArrayList<>();

        for(Container child: children)
        {
            List<? extends ExpRun> runs = expService.getExpRuns(child, null, null);
            allRuns.addAll(runs);
        }
        return allRuns;
    }

    private static int[] getAllExpRunRowIdsInSubfolders(Container container)
    {
        Set<Container> children = ContainerManager.getAllChildren(container);
        ExperimentService expService = ExperimentService.get();
        List<Integer> expRunRowIds = new ArrayList<>();
        for(Container child: children)
        {
            if(container.equals(child))
            {
                continue;
            }
            List<? extends ExpRun> runs = expService.getExpRuns(child, null, null);
            for(ExpRun run: runs)
            {
                expRunRowIds.add(run.getRowId());
            }
        }
        int[] intIds = new int[expRunRowIds.size()];
        for(int i = 0; i < expRunRowIds.size(); i++)
        {
            intIds[i] = expRunRowIds.get(i);
        }
        return intIds;
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
        if (previousCopy.getDoi() != null)
        {
            log.info("Removing DOI from the previous copy");
            previousCopy.setDoi(null);
        }

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
