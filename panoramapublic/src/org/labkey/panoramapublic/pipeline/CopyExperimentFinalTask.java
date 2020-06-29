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
import org.apache.log4j.Logger;
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
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RoleAssignment;
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
import org.labkey.api.view.Portal;
import org.labkey.panoramapublic.PanoramaPublicController;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicNotification;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeServiceException;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
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

            // Create a new entry in panoramapublic.ExperimentAnnotations and link it to the new experiment created during folder import.
            log.info("Creating a new TargetedMS experiment entry in panoramapublic.ExperimentAnnotations.");
            ExperimentAnnotations sourceExperiment = jobSupport.getExpAnnotations();
            JournalExperiment jExperiment = JournalManager.getJournalExperiment(sourceExperiment, jobSupport.getJournal());

            ExperimentAnnotations targetExperiment = new ExperimentAnnotations(sourceExperiment);
            targetExperiment.setExperimentId(experiment.getRowId());
            targetExperiment.setContainer(experiment.getContainer());
            targetExperiment.setJournalCopy(true);
            targetExperiment.setSourceExperimentId(sourceExperiment.getId());
            targetExperiment.setSourceExperimentPath(sourceExperiment.getContainer().getPath());
            targetExperiment.setShortUrl(jExperiment.getShortAccessUrl());

            ExperimentAnnotations previousCopy = null;
            if(jExperiment.getJournalExperimentId() != null)
            {
                previousCopy = ExperimentAnnotationsManager.get(jExperiment.getJournalExperimentId());
                if(previousCopy == null)
                {
                    throw new PipelineJobException("Could not find and entry for the previous copy of the experiment.  " +
                            "Previous experiment ID " + jExperiment.getJournalExperimentId());
                }
            }

            if(jobSupport.assignPxId() // We can get isPxidRequested from the JournalExperiment but sometimes we may have to override that settting.
                                       // This can happen, e.g. if some of the modifications do not have a Unimod ID and the user
                                       // was unable to do a PX submission.  In this case we might still want to get a PX ID.
                                       // Let the admin who is copying the data make the decision.
            )
            {
                if(previousCopy != null)
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

            targetExperiment = ExperimentAnnotationsManager.save(targetExperiment, user);

            // Update the target of the short access URL to the journal's copy of the experiment.
            log.info("Updating access URL to point to the new copy of the data.");
            JournalManager.updateAccessUrl(targetExperiment, jExperiment, user);

            // Update the JournalExperiment table -- set the 'copied' timestamp and the journalExperimentId
            log.info("Setting the 'copied' timestamp and journalExperimentId on the JournalExperiment table.");
            jExperiment.setCopied(new Date());
            jExperiment.setJournalExperimentId(targetExperiment.getId());
            JournalManager.updateJournalExperiment(jExperiment, user);

            // Remove the copy permissions given to the journal.
            log.info("Removing copy permissions given to journal.");
            Group journalGroup = SecurityManager.getGroup(jobSupport.getJournal().getLabkeyGroupId());
            JournalManager.removeJournalPermissions(jobSupport.getExpAnnotations(), journalGroup, user);

            // Give read permissions to the authors (all users that are folder admins)
            log.info("Adding read permissions to all users that are folder admins in the source container.");
            List<User> authors = getUsersWithRole(sourceExperiment.getContainer(), RoleManager.getRole(FolderAdminRole.class));

            Container target = experiment.getContainer();
            MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(target, target.getPolicy());
            for(User author: authors)
            {
                newPolicy.addRoleAssignment(author, ReaderRole.class);
            }

            if(previousCopy != null)
            {
                // Users that had read access to the previous copy should be given read access to the new copy. This will include the reviewer
                // account if one was created for the previous copy.
                List<User> previousCopyReaders = getUsersWithRole(previousCopy.getContainer(), RoleManager.getRole(ReaderRole.class));
                previousCopyReaders.forEach(u -> newPolicy.addRoleAssignment(u, ReaderRole.class));
            }

            SecurityPolicyManager.savePolicy(newPolicy);

            // We are only allowing 'Experimental Data' type folders to be submitted to Panorama Public.
            // If this changes we will have to get the value of the FOLDER_TYPE_PROPERTY on the source container and set it on the target container.
            log.info("Setting the TargetedMS folder type to 'Experimental Data'");
            updateFolderType(target, user);

            FileContentService service = FileContentService.get();
            if(service != null)
            {
                // If there is a "Raw Data" tab in the folder and/or one of its subfolders, fix the configuration of the
                // Files webpart in the tab.
                log.info("Updating the 'Raw Data' tab configuration");
                updateRawDataTabConfig(target, service, user);

                // DataFileUrl in exp.data and FilePathRoot in exp.experimentRun point to locations in the 'export' directory.
                // We are now copying all files from the source container to the target container file root. Update the paths
                // to point to locations in the target container file root, and delete the 'export' directory
                if(!updateDataPaths(target, service, user, job.getLogger()))
                {
                    throw new PipelineJobException("Unable to update all data file paths.");
                }
            }

            User reviewer = null;
            String reviewerPassword = null;
            if(jExperiment.isKeepPrivate())
            {
                if (previousCopy == null)
                {
                    reviewerPassword = createPassword();
                    reviewer = createReviewerAccount(jobSupport.getReviewerEmailPrefix(), reviewerPassword, user, log);
                    assignReader(reviewer, target);
                }
            }
            else
            {
                // Assign Site:Guests to reader role
                log.info("Making folder public.");
                assignReader(SecurityManager.getGroup(Group.groupGuests), target);
            }

            // Create notifications
            PanoramaPublicNotification.notifyCopied(sourceExperiment, targetExperiment, jobSupport.getJournal(), jExperiment,
                    reviewer, reviewerPassword, user, previousCopy != null /*This is a re-copy if previousCopy exists*/);

            postEmailNotification(jobSupport, user, log, sourceExperiment, jExperiment, targetExperiment, reviewer, reviewerPassword, previousCopy != null);

            // Delete the previous copy
            if(previousCopy != null && jobSupport.deletePreviousCopy())
            {
                log.info("Deleting old container " + previousCopy.getContainer().getPath());
                Container oldContainer = previousCopy.getContainer();
                ContainerManager.delete(oldContainer, user);
            }

            transaction.commit();
        }
    }

    private List<User> getUsersWithRole(Container container, Role role)
    {
        SecurityPolicy securityPolicy = container.getPolicy();
        List<User> users = new ArrayList<>();

        users.addAll(securityPolicy.getAssignments().stream()
                .filter(r -> r.getRole().equals(role)
                        && UserManager.getUser(r.getUserId()) != null) // Ignore user groups
                .map(r -> UserManager.getUser(r.getUserId()))
                .collect(Collectors.toList()));
        return users;
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

    private void postEmailNotification(CopyExperimentJobSupport jobSupport, User pipelineJobUser, Logger log, ExperimentAnnotations sourceExperiment,
                                       JournalExperiment jExperiment, ExperimentAnnotations targetExperiment,
                                       User reviewer, String reviewerPassword, boolean recopy)
    {
        // This is the user that was selected as the "Submitter" in the ExperimentAnnotations form, and will be used in the "Submitter" field
        // when announcing data on Panorama Public.
        User pxSubmitter = sourceExperiment.getSubmitterUser();

        // This is the user that clicked the "Submit" button.  Typically this is the same as the user above.
        // If not, send email to both
        User formSubmitter = UserManager.getUser(jExperiment.getCreatedBy());

        Set<String> toAddresses = new HashSet<>();
        if(pxSubmitter != null) toAddresses.add(pxSubmitter.getEmail());
        toAddresses.add(formSubmitter.getEmail());
        toAddresses.addAll(jobSupport.toEmailAddresses());

        String subject = String.format("Submission to %s: %s", jobSupport.getJournal().getName(), targetExperiment.getShortUrl().renderShortURL());
        String emailBody;
        if(recopy)
        {
            emailBody = PanoramaPublicNotification.getExperimentReCopiedEmailBody(sourceExperiment, targetExperiment, jExperiment, jobSupport.getJournal(),
                    PanoramaPublicNotification.getUserName(formSubmitter),
                    PanoramaPublicNotification.getUserName(pipelineJobUser));
        }
        else
        {
            emailBody = PanoramaPublicNotification.getExperimentCopiedEmailBody(sourceExperiment, targetExperiment, jExperiment, jobSupport.getJournal(),
                    reviewer, reviewerPassword,
                    PanoramaPublicNotification.getUserName(formSubmitter),
                    PanoramaPublicNotification.getUserName(pipelineJobUser));
        }

        if(jobSupport.emailSubmitter())
        {
            log.info("Emailing submitter.");
            try
            {
                PanoramaPublicNotification.sendEmailNotification(subject, emailBody, targetExperiment.getContainer(), pipelineJobUser, toAddresses);
                PanoramaPublicNotification.postEmailContents(subject, emailBody, toAddresses, pipelineJobUser, sourceExperiment, jExperiment, jobSupport.getJournal(), true);
            }
            catch (Exception e)
            {
                log.info("Could not send email to submitter. Error was: " + e.getMessage(), e);
                PanoramaPublicNotification.postEmailContentsWithError(subject, emailBody, toAddresses, pipelineJobUser, sourceExperiment, jExperiment, jobSupport.getJournal(), e.getMessage());
            }
        }
        else
        {
            // Post the email contents to the message board.
            PanoramaPublicNotification.postEmailContents(subject, emailBody, toAddresses, pipelineJobUser, sourceExperiment, jExperiment, jobSupport.getJournal(), false);
        }
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
                    logger.info("to: " + newDataPath.toUri().toString());
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

    private void updateFolderType(Container c, User user)
    {
        Set<Container> children = ContainerManager.getAllChildren(c); // Includes parent
        for(Container child: children)
        {
            PanoramaPublicManager.makePanoramaExperimentalDataFolder(child, user);
        }
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
            else if(data.getRowId() == skydDataId)
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

    private void updateRawDataTab(Container c, FileContentService service, User user)
    {
        List<Portal.WebPart> rawDataTabParts = Portal.getParts(c, TargetedMSService.RAW_FILES_TAB);
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

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(CopyExperimentFinalTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new CopyExperimentFinalTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "FINISH EXPERIMENT COPY";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
