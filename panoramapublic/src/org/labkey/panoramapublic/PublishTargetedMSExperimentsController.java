/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.targetedms;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartView;
import org.labkey.targetedms.model.ExperimentAnnotations;
import org.labkey.targetedms.model.Journal;
import org.labkey.targetedms.model.JournalExperiment;
import org.labkey.targetedms.pipeline.CopyExperimentPipelineJob;
import org.labkey.targetedms.query.ExperimentAnnotationsManager;
import org.labkey.targetedms.query.JournalManager;
import org.labkey.targetedms.view.expannotations.TargetedMSExperimentsWebPart;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: vsharma
 * Date: 8/5/2014
 * Time: 8:39 AM
 */
public class PublishTargetedMSExperimentsController extends SpringActionController
{
    public static Class[] getActions()
    {
        Class[] innerClasses = PublishTargetedMSExperimentsController.class.getDeclaredClasses();
        List<Class> actionClasses = new ArrayList<>();
        for (Class innerClass : innerClasses)
            if (Controller.class.isAssignableFrom(innerClass) && !Modifier.isAbstract(innerClass.getModifiers()))
                actionClasses.add(innerClass);

        Class[] toReturn = new Class[actionClasses.size()];
        return actionClasses.toArray(toReturn);
    }


    // ------------------------------------------------------------------------
    // BEGIN Actions for journal groups.
    // ------------------------------------------------------------------------
    @RequiresSiteAdmin
    public static class JournalGroupsAdminViewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            QuerySettings qSettings = new QuerySettings(getViewContext(), "Journals", "Journal");
            QueryView qView = new QueryView(new TargetedMSSchema(getUser(), getContainer()), qSettings, null);
            qView.setShowDetailsColumn(true);
            DetailsURL detailsUrl = new DetailsURL(new ActionURL(JournalGroupDetailsAction.class, getContainer()), Collections.singletonMap("id", "id"));
            qView.setDetailsURL(detailsUrl.toString());
            qView.setFrame(WebPartView.FrameType.NONE);

            VBox view = new VBox();
            ActionURL newJournalUrl = new ActionURL(CreateJournalGroupAction.class, getContainer());
            view.addView(new HtmlView("<div><a href=\"" + newJournalUrl + "\">Create a new journal group </a></div>"));
            view.addView(qView);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Journal groups");
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Journal Groups");
            return root;
        }
    }

    @RequiresSiteAdmin
    public static class CreateJournalGroupAction extends FormViewAction<CreateJournalGroupForm>
    {
        private Journal _journal;

        @Override
        public void validateCommand(CreateJournalGroupForm target, Errors errors) {}


        @Override
        public ModelAndView getView(CreateJournalGroupForm form, boolean reshow, BindException errors) throws Exception
        {
            JspView view = new JspView("/org/labkey/targetedms/view/publish/createJournalGroup.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Create New Journal Group");
            return view;
        }

        @Override
        public boolean handlePost(CreateJournalGroupForm form, BindException errors) throws Exception
        {
            // Validate the group name.
            String message = UserManager.validGroupName(form.getGroupName(), PrincipalType.GROUP);
            if(message != null)
            {
                errors.addError(new LabkeyError(message));
            }
            else
            {
                Integer groupId = org.labkey.api.security.SecurityManager.getGroupId(null, form.getGroupName(), null, false);
                if (groupId != null)
                {
                    errors.addError(new LabkeyError("Group with name " + form.getGroupName() + " already exists."));
                }
            }

            // Validate the project name.
            StringBuilder error = new StringBuilder();
            if(Container.isLegalName(form.getProjectName(), error))
            {
                if(ContainerManager.getRoot().getChild(form.getProjectName()) != null)
                {
                    errors.addError(new LabkeyError("Project name " + form.getProjectName() + " already exists."));
                }
            }
            else if(error.length() > 0)
            {
                errors.addError(new LabkeyError(error.toString()));
            }

            // Validate the journal name
            if(StringUtils.isBlank(form.getJournalName()))
            {
                errors.addError(new LabkeyError("Journal name cannot be blank."));
            }
            else
            {
                if(JournalManager.getJournal(form.getJournalName()) != null)
                {
                    errors.addError(new LabkeyError("Journal with name " + form.getJournalName() + " already exists"));
                }
            }

            if(errors.getErrorCount() > 0)
            {
                return false;
            }

            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                // Create the project.
                Container container = ContainerManager.createContainer(ContainerManager.getRoot(), form.getProjectName(), null, null, Container.TYPE.normal, getUser());
                // Set the folder type to "Targeted MS".
                FolderType type = FolderTypeManager.get().getFolderType(TargetedMSFolderType.NAME);
                container.setFolderType(type, getUser());
                // Make this an "Experiment data" folder.
                Module targetedMSModule = ModuleLoader.getInstance().getModule(TargetedMSModule.NAME);
                ModuleProperty moduleProperty = targetedMSModule.getModuleProperties().get(TargetedMSModule.TARGETED_MS_FOLDER_TYPE);
                moduleProperty.saveValue(getUser(), container, TargetedMSModule.FolderType.Experiment.toString());
                // Display only the "Targeted MS Experiment List" webpart.
                Portal.WebPart webPart = Portal.getPortalPart(TargetedMSExperimentsWebPart.WEB_PART_NAME).createWebPart();
                List<Portal.WebPart> newWebParts = Collections.singletonList(webPart);
                Portal.saveParts(container, DefaultFolderType.DEFAULT_DASHBOARD, newWebParts);
                Portal.saveParts(container, Portal.DEFAULT_PORTAL_PAGE_ID, newWebParts); // this will remove the TARGETED_MS_SETUP

                // Add the permissions group
                // TODO: put in audit log as in CreateGroupAction?
                Group group = SecurityManager.createGroup(container, form.getGroupName());

                // Assign project admin role to the group.
                MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(container));
                policy.addRoleAssignment(group, ProjectAdminRole.class);
                SecurityPolicyManager.savePolicy(policy);

                // Add the journal
                _journal = new Journal();
                _journal.setName(form.getJournalName());
                _journal.setLabkeyGroupId(group.getUserId());
                _journal.setProject(container);
                JournalManager.saveJournal(_journal, getUser());

                transaction.commit();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(CreateJournalGroupForm newJournalGroupForm)
        {   if(_journal != null)
            {
                ActionURL url = new ActionURL(JournalGroupDetailsAction.class, getContainer());
                url.addParameter("id", _journal.getId());
                return url;
            }
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("Journal groups", new ActionURL(JournalGroupsAdminViewAction.class, getContainer()));
                root.addChild("Create New Journal Group");
            }
            return root;
        }
    }

    public static class CreateJournalGroupForm
    {
        private String _journalName;
        private String _groupName;
        private String _projectName;

        public String getJournalName()
        {
            return _journalName;
        }

        public void setJournalName(String journalName)
        {
            _journalName = journalName;
        }

        public String getGroupName()
        {
            return _groupName;
        }

        public void setGroupName(String groupName)
        {
            _groupName = groupName;
        }

        public String getProjectName()
        {
            return _projectName;
        }

        public void setProjectName(String projectName)
        {
            _projectName = projectName;
        }
    }

    @RequiresSiteAdmin
    public static class DeleteJournalGroupAction extends ConfirmAction<JournalForm>
    {
        @Override
        public ModelAndView getConfirmView(JournalForm form, BindException errors) throws Exception
        {
            return FormPage.getView(PublishTargetedMSExperimentsController.class, form, "view/publish/deleteJournal.jsp");
        }

        @Override
        public boolean handlePost(JournalForm form, BindException errors) throws Exception
        {
            Journal journal = form.lookupJournal();

            if(journal == null)
            {
                errors.addError(new LabkeyError("No journal found for journal ID  " + form.getId()));
                return false;
            }

            try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
            {
                // Delete the journal.
                JournalManager.delete(journal, getUser());

                // Delete the permissions group created for this journal.
                SecurityManager.deleteGroup(SecurityManager.getGroup(journal.getLabkeyGroupId()));

                // Delete the project created for this journal.
                // TODO: Should we keep the project around?
                ContainerManager.delete(journal.getProject(), getUser());

                transaction.commit();
            }

            return true;
        }

        @Override
        public void validateCommand(JournalForm form, Errors errors)
        {
            return;
        }

        @Override
        public URLHelper getSuccessURL(JournalForm form)
        {
            return new ActionURL(JournalGroupsAdminViewAction.class, getContainer());
        }
    }

    @RequiresSiteAdmin
    public static class JournalGroupDetailsAction extends SimpleViewAction<JournalForm>
    {
        @Override
        public ModelAndView getView(JournalForm form, BindException errors) throws Exception
        {
            Journal journal = form.lookupJournal();
            if(journal == null)
            {
                errors.addError(new LabkeyError("No journal found for journal ID  " + form.getId()));
                return new SimpleErrorView(errors, true);
            }

            DataRegion journalDetails = new DataRegion();
            journalDetails.setColumns(TargetedMSManager.getTableInfoJournal().getColumns("Name", "LabkeyGroupId", "Project", "Created", "CreatedBy"));

            ButtonBar buttonBar = new ButtonBar();
            buttonBar.setStyle(ButtonBar.Style.separateButtons);
            ActionURL url = new ActionURL(DeleteJournalGroupAction.class, getViewContext().getContainer());
            ActionButton deleteJournalButton = new ActionButton(url,"Delete");
            deleteJournalButton.setActionType(ActionButton.Action.GET);
            buttonBar.add(deleteJournalButton);
            journalDetails.setButtonBar(buttonBar);
            // Add the journal "id" in a hidden form field.
            journalDetails.addHiddenFormField("id", String.valueOf(journal.getId()));

            DetailsView detailsView = new DetailsView(journalDetails, form.getId());

            QuerySettings qSettings = new QuerySettings(getViewContext(), "Experiments", "JournalExperiment");
            List<FieldKey> columns = new ArrayList<>();
            columns.add(FieldKey.fromParts("ExperimentAnnotationsId"));
            columns.add(FieldKey.fromParts("ShortAccessUrl"));
            columns.add(FieldKey.fromParts("ShortCopyUrl"));
            columns.add(FieldKey.fromParts("Copied"));
            qSettings.setFieldKeys(columns);
            qSettings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("JournalId"), journal.getId()));
            qSettings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
            QueryView journalExperimentListView = new QueryView(new TargetedMSSchema(getUser(), getContainer()), qSettings, errors);
            // journalExperimentListView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            VBox view = new VBox();
            view.addView(detailsView);
            view.addView(new HtmlView("<div>This journal has access to the following Targeted MS experiments:</div>"));
            view.addView(journalExperimentListView);
            view.setTitle("Journal group details");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("Journal groups", new ActionURL(JournalGroupsAdminViewAction.class, getContainer()));
                root.addChild("Journal group details");
            }
            return root;
        }
    }

    public static class JournalForm extends IdForm
    {
        public Journal lookupJournal()
        {
            return JournalManager.getJournal(getId());
        }
    }

    public static class IdForm extends ViewForm
    {
        private int _id;

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    // ------------------------------------------------------------------------
    // END Actions for journal groups.
    // ------------------------------------------------------------------------


    // ------------------------------------------------------------------------
    // BEGIN Action for copying an experiment.
    // ------------------------------------------------------------------------
    @RequiresLogin
    public static class CopyExperimentAction extends FormViewAction<CopyExperimentForm>
    {
        private ActionURL _successURL;
        private ExperimentAnnotations _experiment;
        private Journal _journal;

        @Override
        public void validateCommand(CopyExperimentForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(CopyExperimentForm form, boolean reshow, BindException errors) throws Exception
        {
            validateAction(form);

            JspView view = new JspView("/org/labkey/targetedms/view/publish/copyExperimentForm.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Copy Targeted MS Experiment");
            return view;
        }

        private void validateAction(CopyExperimentForm form) throws ServletException
        {
            _experiment = form.lookupExperiment();
            if(_experiment == null)
            {
                throw new NotFoundException("Could not find experiment with id " + form.getId());
            }

            TargetedMSController.ensureCorrectContainer(getContainer(), _experiment.getContainer(), getViewContext());

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                throw new NotFoundException("Could not find journal with id " + form.getJournalId());
            }
            // User initiating the copy must be a member of a journal that was given access
            // to the experiment.
            if(!JournalManager.userHasCopyAccess(_experiment, _journal, getUser()))
            {
                throw new UnauthorizedException("You do not have permissions to copy this experiment.");
            }
        }

        @Override
        public boolean handlePost(CopyExperimentForm form, BindException errors) throws Exception
        {
            validateAction(form);

            Container parentContainer = form.lookupDestParentContainer();
            if(parentContainer == null)
            {
                errors.reject(ERROR_MSG, "Please select a parent folder.");
                return false;
            }
            if(!parentContainer.hasPermission(getUser(), InsertPermission.class))
            {
                errors.reject(ERROR_MSG, "You do not have permissions to create a new folder in " + parentContainer.getPath());
                return false;
            }
            String destinationFolder = form.getDestContainerName();
            StringBuilder errMessages = new StringBuilder();
            if(!Container.isLegalName(destinationFolder, errMessages))
            {
                errors.reject(ERROR_MSG, "Invalid destination folder name " + destinationFolder + ". " + errMessages.toString());
                return false;
            }
            if(ContainerManager.getForPath(parentContainer.getParsedPath().append(destinationFolder)) != null)
            {
                errors.reject(ERROR_MSG, "Destination folder " + destinationFolder + " already exists. Please enter another folder name."
                                         + errMessages.toString());
                return false;
            }

            // Create the new target container.
            Container target = ContainerManager.createContainer(parentContainer, destinationFolder, null, null, Container.TYPE.normal, getUser());

            try{
                PipeRoot root = PipelineService.get().findPipelineRoot(target);
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found for " + target.getPath());
                }
                ViewBackgroundInfo info = new ViewBackgroundInfo(target, getUser(), getViewContext().getActionURL());
                PipelineService.get().queueJob(new CopyExperimentPipelineJob(info, root, _experiment, _journal));

                _successURL = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(target);

                return true;
            }
            catch (PipelineValidationException e){
                return false;
            }
        }

        @Override
        public URLHelper getSuccessURL(CopyExperimentForm copyExperimentForm)
        {
            return _successURL;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CopyExperimentForm extends IdForm
    {
        private int _journalId;
        private String _destContainerName;
        private Integer _destParentContainerId;

        public int getJournalId()
        {
            return _journalId;
        }

        public void setJournalId(int journalId)
        {
            _journalId = journalId;
        }

        public ExperimentAnnotations lookupExperiment()
        {
            return ExperimentAnnotationsManager.get(getId());
        }

        public Journal lookupJournal()
        {
            return  JournalManager.getJournal(getJournalId());
        }

        public String getDestContainerName()
        {
            return _destContainerName;
        }

        public void setDestContainerName(String destContainerName)
        {
            _destContainerName = destContainerName;
        }

        public Integer getDestParentContainerId()
        {
            return _destParentContainerId;
        }

        public void setDestParentContainerId(Integer destParentContainerId)
        {
            _destParentContainerId = destParentContainerId;
        }

        public Container lookupDestParentContainer()
        {
            return _destParentContainerId != null ? ContainerManager.getForRowId(_destParentContainerId) : null;
        }

        public ActionURL getReturnActionURL()
        {
            ActionURL result;
            try
            {
                result = super.getReturnActionURL();
                if (result != null)
                {
                    return result;
                }
            }
            catch (Exception e)
            {
                // Bad URL -- fall through
            }

            // Bad or missing returnUrl -- go to expeirment annotation details
            Container c = HttpView.currentContext().getContainer();
            return TargetedMSController.getViewExperimentDetailsURL(getId(), c);
        }
    }

    // ------------------------------------------------------------------------
    // END Action for copying an experiment.
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for publishing an experiment (provide copy access to a journal)
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class PublishExperimentAction extends FormViewAction<PublishExperimentForm>
    {

        ExperimentAnnotations _exptAnnotations;
        Journal _journal;

        private static final int RANDOM_URL_SIZE = 6;
        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors) throws Exception
        {
            validate(form);

            PublishExperimentFormBean bean = new PublishExperimentFormBean();
            bean.setForm(form);
            bean.setJournalList(JournalManager.getJournals());
            bean.setExperimentAnnotations(_exptAnnotations);

            if(!reshow)
            {
                setInitialShortUrls(form);
            }

            JspView view = new JspView("/org/labkey/targetedms/view/publish/publishExperimentForm.jsp", bean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Publish Experiment");
            return view;
        }

        void setInitialShortUrls(PublishExperimentForm form)
        {
            form.setShortAccessUrl(generateRandomUrl(RANDOM_URL_SIZE));
            form.setShortCopyUrl(generateRandomUrl(RANDOM_URL_SIZE));
        }

        private String generateRandomUrl(int length)
        {
            ShortURLService shortUrlService = ServiceRegistry.get().getService(ShortURLService.class);
            while(true)
            {
                String random = RandomStringUtils.randomAlphanumeric(length);
                ShortURLRecord shortURLRecord = shortUrlService.resolveShortURL(random);
                if(shortURLRecord == null)
                {
                    return random;
                }
            }
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            if(!validateForm(form, errors))
            {
                return false;
            }

            if (_exptAnnotations.isIncludeSubfolders())
            {
                // Make sure that there is only one experiment in the container tree rooted at this folder.
                List<ExperimentAnnotations> expAnnotations = ExperimentAnnotationsManager.getAllExperiments(_exptAnnotations.getContainer(), getUser());
                if(expAnnotations.size() > 1)
                {
                    errors.reject(ERROR_MSG, "There are multiple experiments in this folder and its subfolders. " +
                            "The experiment you want to publish should be the only experiment defined in a folder and its subfolders.");
                    return false;
                }
            }

            JournalExperiment je;
            try
            {
                je = JournalManager.addJournalAccess(_exptAnnotations, _journal, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
            }
            catch(ValidationException | UnauthorizedException  e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            // Email contact person for the journal
            String journalEmail = LookAndFeelProperties.getInstance(_journal.getProject()).getSystemEmailAddress();
            String panoramaAdminEmail = LookAndFeelProperties.getInstance(ContainerManager.getHomeContainer()).getSystemEmailAddress();


            try
            {
                MailHelper.ViewMessage m = MailHelper.createMessage(panoramaAdminEmail, journalEmail);
                m.setSubject("Access to copy an experiment on Panorama");
                StringBuilder text = new StringBuilder("You have been given access to copy an experiment on Panorama.\n\n");
                text.append("The current location of the data is:\n").append(je.getShortAccessUrl().renderShortURL());
                text.append("\n\n");
                text.append("Use the following link to copy the data:\n");
                text.append(je.getShortCopyUrl().renderShortURL()).append("\n\n");
                text.append("Thank you,\n\nPanorama team");
                m.setText(text.toString());
                MailHelper.send(m, getUser(), getContainer());
            }
            catch (Exception e)
            {
                logger.error("Failed to send notification email to journal.", e);
            }

            return true;
        }

        void validate(PublishExperimentForm form) throws ServletException
        {
            _exptAnnotations = form.lookupExperiment();
            if(_exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with id " + form.getId());
            }

            TargetedMSController.ensureCorrectContainer(getContainer(), _exptAnnotations.getContainer(), getViewContext());

            // Cannot publish if this is not an "Experimental data" folder.
            TargetedMSModule.FolderType folderType = TargetedMSModule.getFolderType(_exptAnnotations.getContainer());
            if(folderType != TargetedMSModule.FolderType.Experiment)
            {
                throw new IllegalStateException("Only Targeted MS folders of type \"Experimental data\" can be published.");
            }

        }

        boolean validateForm(PublishExperimentForm form, BindException errors) throws ServletException
        {

            validate(form);

            // Validate the journal.
            _journal = form.lookupJournal();
            if(_journal != null)
            {
                validateJournal(errors, _exptAnnotations, _journal);
            }
            else
            {
                errors.reject(ERROR_MSG, "Please select a publication target.");
            }

            // Validate the short access url.
            if(!StringUtils.isBlank(form.getShortAccessUrl()))
            {
                validateShortAccessUrl(form, errors);
            }
            else
            {
                errors.reject(ERROR_MSG, "Please enter a short access URL.");
            }

            // Validate the short copy url.
            if(!StringUtils.isBlank(form.getShortCopyUrl()))
            {
                validateShortCopyUrl(form, errors);
            }
            else
            {
                errors.reject(ERROR_MSG, "Please enter a short copy URL.");
            }

            if(StringUtils.trim(form.getShortAccessUrl()).equalsIgnoreCase(StringUtils.trim(form.getShortCopyUrl())))
            {
                errors.reject(ERROR_MSG, "Access URL and copy URL cannot be the same.");
            }

            return errors.getErrorCount() == 0;
        }

        void validateJournal(BindException errors, ExperimentAnnotations experiment, Journal journal)
        {
            if(JournalManager.journalHasAccess(journal, experiment))
            {
                errors.reject(ERROR_MSG, journal.getName() + "\" already has access to this experiment. Please select another publication target." );
            }
        }

        void validateShortAccessUrl(PublishExperimentForm form, BindException errors)
        {
            validateShortUrl(form.getShortAccessUrl(), errors);
        }

        void validateShortCopyUrl(PublishExperimentForm form, BindException errors)
        {
            validateShortUrl(form.getShortCopyUrl(), errors);
        }

        protected void validateShortUrl(String shortUrl, BindException errors)
        {
            ShortURLService shortUrlService = ServiceRegistry.get(ShortURLService.class);
            try
            {
                shortUrl = shortUrlService.validateShortURL(shortUrl);

                ShortURLRecord shortURLRecord = shortUrlService.resolveShortURL(shortUrl);
                if(shortURLRecord != null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Short URL is already in use " + "'" + shortUrl + "'. "
                            + "Please use a different short URL.");
                }
            }
            catch(ValidationException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid short URL. " + e.getMessage());
            }
        }

        @Override
        public URLHelper getSuccessURL(PublishExperimentForm form)
        {
            return TargetedMSController.getViewExperimentDetailsURL(form.getId(), getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public static final class PublishExperimentFormBean
    {
        private PublishExperimentForm _form;
        private List<Journal> _journalList;
        private ExperimentAnnotations _experimentAnnotations;

        public PublishExperimentForm getForm()
        {
            return _form;
        }

        public void setForm(PublishExperimentForm form)
        {
            _form = form;
        }

        public List<Journal> getJournalList()
        {
            return _journalList;
        }

        public void setJournalList(List<Journal> journalList)
        {
            _journalList = journalList;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public void setExperimentAnnotations(ExperimentAnnotations experimentAnnotations)
        {
            _experimentAnnotations = experimentAnnotations;
        }
    }

    public static class PublishExperimentForm extends IdForm
    {
        private int _journalId;
        private String _shortAccessUrl;
        private String _shortCopyUrl;
        private boolean _update = false;

        public int getJournalId()
        {
            return _journalId;
        }

        public void setJournalId(int journalId)
        {
            _journalId = journalId;
        }

        public String getShortAccessUrl()
        {
            return _shortAccessUrl;
        }

        public void setShortAccessUrl(String shortAccessUrl)
        {
            _shortAccessUrl = shortAccessUrl;
        }

        public String getShortCopyUrl()
        {
            return _shortCopyUrl;
        }

        public void setShortCopyUrl(String shortCopyUrl)
        {
            _shortCopyUrl = shortCopyUrl;
        }

        public ExperimentAnnotations lookupExperiment()
        {
            return ExperimentAnnotationsManager.get(getId());
        }

        public Journal lookupJournal()
        {
            return JournalManager.getJournal(getJournalId());
        }

        public boolean isUpdate()
        {
            return _update;
        }

        public void setUpdate(boolean update)
        {
            _update = update;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for publishing an experiment (provide copy access to a journal)
    // ------------------------------------------------------------------------


    // ------------------------------------------------------------------------
    // BEGIN Action for updating an entry in targetedms.JournalExperiment table
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class UpdateJournalExperimentAction extends PublishExperimentAction
    {
        private JournalExperiment _journalExperiment;

        protected void validate(PublishExperimentForm form) throws ServletException
        {
            super.validate(form);

            _journalExperiment = JournalManager.getJournalExperiment(_exptAnnotations.getId(), form.getJournalId());
            if(_journalExperiment == null)
            {
                throw new NotFoundException("Could not find an entry in JournalExperiment for experiment ID " + _exptAnnotations.getId() + " and journal ID " + form.getJournalId());
            }
        }

        protected void setInitialShortUrls(PublishExperimentForm form)
        {
            form.setShortCopyUrl(_journalExperiment.getShortCopyUrl().getShortURL());
            form.setShortAccessUrl(_journalExperiment.getShortAccessUrl().getShortURL());
        }

        protected void validateJournal(BindException errors, ExperimentAnnotations experiment, Journal journal)
        {
            Journal oldJournal = JournalManager.getJournal(_journalExperiment.getJournalId());
            if(oldJournal != null && (!oldJournal.getId().equals(journal.getId())))
            {
                super.validateJournal(errors, experiment, journal);
            }
        }

        protected void validateShortAccessUrl(PublishExperimentForm form, BindException errors)
        {
            ShortURLRecord accessUrlRecord = _journalExperiment.getShortAccessUrl();
            if(!accessUrlRecord.getShortURL().equals(form.getShortAccessUrl()))
            {
                super.validateShortAccessUrl(form, errors);
            }
        }

        protected void validateShortCopyUrl(PublishExperimentForm form, BindException errors)
        {
            ShortURLRecord copyUrlRecord = _journalExperiment.getShortCopyUrl();
            if(!copyUrlRecord.getShortURL().equals(form.getShortCopyUrl()))
            {
                super.validateShortCopyUrl(form, errors);
            }
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            if(!validateForm(form, errors))
            {
                return false;
            }

            // If this experiment has already been copied by the journal, don't allow editing.
            if(_journalExperiment.getCopied() != null)
            {
                errors.reject(ERROR_MSG, "This experiment has already been copied by the journal. You cannot change the short URLs anymore." );
                return false;
            }

            if(_journalExperiment.getShortAccessUrl().getShortURL().equalsIgnoreCase(form.getShortAccessUrl()) &&
               _journalExperiment.getShortCopyUrl().getShortURL().equalsIgnoreCase(form.getShortCopyUrl()))
                return true;

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                JournalManager.updateJournalExperimentUrls(_exptAnnotations, _journal, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                transaction.commit();
            }

            return true;
        }
    }

    // ------------------------------------------------------------------------
    // END Action for updating an entry in targetedms.JournalExperiment table
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for deleting an entry in targetedms.JournalExperiment table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteJournalExperimentAction extends ConfirmAction<PublishExperimentForm>
    {

        private ExperimentAnnotations _experimentAnnotations;
        private Journal _journal;

        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors) throws Exception
        {
            validate(form, errors);

            TargetedMSController.ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());
            StringBuilder html = new StringBuilder();
            html.append("Are you sure you want to remove permissions given to the journal ");
            html.append("\'").append(_journal.getName()).append(" \'");
            html.append(" to copy the experiment ");
            html.append("\'").append(_experimentAnnotations.getTitle()).append(" \'");
            html.append("?");
            html.append(" This action will also delete the short access and copy URLs.");
            return new HtmlView(html.toString());
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            JournalManager.deleteJournalAccess(_experimentAnnotations, _journal, getUser());
            return true;
        }

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with Id " + form.getId());
            }

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                throw new NotFoundException("Could not find a journal with Id " + form.getJournalId());
            }

            JournalExperiment je = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(je == null)
            {
                throw new NotFoundException("Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
            }

            if(je.getCopied() != null)
            {
                errors.reject(ERROR_MSG, "The experiment has already been copied by the journal. Unlable to delete short access and copy URLs.");
            }
        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(PublishExperimentForm publishExperimentForm)
        {
            return TargetedMSController.getViewExperimentDetailsURL(publishExperimentForm.getId(), getContainer());
        }
    }

    // ------------------------------------------------------------------------
    // END Action for deleting an entry in targetedms.JournalExperiment table.
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for resetting an entry in targetedms.JournalExperiment table
    //       -- Set 'Copied' column to null.
    //       -- Give journal copy privilege again.
    //       -- Reset access URL to point to the author's data
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class ResetJournalExperimentAction extends ConfirmAction<PublishExperimentForm>
    {
        private ExperimentAnnotations _experimentAnnotations;
        private Journal _journal;
        private JournalExperiment _journalExperiment;

        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors) throws Exception
        {
            validate(form, errors);

            TargetedMSController.ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());
            StringBuilder html = new StringBuilder();
            html.append("The experiment '").append(_experimentAnnotations.getTitle()).append(" '");
            html.append(" has already been copied by the journal '").append(_journal.getName()).append("'.");
            html.append(" If you click 'Continue' the journal will be given copy access again.");
            html.append("<br/>");
            html.append("Are you sure you want to continue?");
            return new HtmlView(html.toString());
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
            {
                Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(_journal.getLabkeyGroupId());
                JournalManager.addJournalPermissions(_experimentAnnotations, journalGroup, getUser());

                _journalExperiment.setCopied(null);
                JournalManager.updateJournalExperiment(_journalExperiment, getUser());

                // Reset the access URL to point to the author's folder
                JournalManager.updateAccessUrl(_experimentAnnotations, _experimentAnnotations, _journal, getUser());

                transaction.commit();
            }

            return true;
        }

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with Id " + form.getId());
            }

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                throw new NotFoundException("Could not find a journal with Id " + form.getJournalId());
            }

            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(_journalExperiment == null)
            {
                throw new NotFoundException("Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
            }
        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(PublishExperimentForm publishExperimentForm)
        {
            return TargetedMSController.getViewExperimentDetailsURL(publishExperimentForm.getId(), getContainer());
        }
    }

    // ------------------------------------------------------------------------
    // END Action for resetting an entry in targetedms.JournalExperiment table
    // ------------------------------------------------------------------------


    public static ActionURL getCopyExperimentURL(int experimentAnnotationsId, int journalId, Container container)
    {
        ActionURL result = new ActionURL(PublishTargetedMSExperimentsController.CopyExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        return result;
    }

    public static ActionURL getPublishExperimentURL(int experimentAnnotationsId, Container container)
    {
        ActionURL result = new ActionURL(PublishExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }
}
