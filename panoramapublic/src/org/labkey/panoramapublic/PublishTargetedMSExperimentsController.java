/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
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
import org.labkey.api.data.NormalContainerType;
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
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
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
    private static final Logger LOG = Logger.getLogger(PublishTargetedMSExperimentsController.class);

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
    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public static class JournalGroupsAdminViewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            QuerySettings qSettings = new QuerySettings(getViewContext(), "Journals", "Journal");
            QueryView qView = new QueryView(new TargetedMSSchema(getUser(), getContainer()), qSettings, null);
            qView.setShowDetailsColumn(true);
            DetailsURL detailsUrl = new DetailsURL(new ActionURL(JournalGroupDetailsAction.class, getContainer()), Collections.singletonMap("id", "id"));
            qView.setDetailsURL(detailsUrl.toString());
            qView.setFrame(WebPartView.FrameType.NONE);

            VBox view = new VBox();
            view.addView(new HtmlView("<div style=\"margin:5px;\">Journal groups are used in conjunction with the \"publication protocol\" implemented for the targetedms module. " +
                    "The goal of the publication protocol is to provide a mechanism for journals to copy data associated with a manuscript from the author's  project " +
                    " on a Panorama server to the journal's project. " +
                    "Creating a new journal group via this admin console does the following:<ol>" +
                    "<li>Creates a project for the journal with the appropriate web parts added</li>" +
                    "<li>Creates a new security group for members of the journal</li>" +
                    "<li>Create an entry in the Journal table of the targetedms schema that links the journal  to the project</li></ol></div>"));

            if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
            {
                ActionURL newJournalUrl = new ActionURL(CreateJournalGroupAction.class, getContainer());
                view.addView(new HtmlView("<div><a href=\"" + newJournalUrl + "\">Create a new journal group </a></div>"));
            }

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

    @RequiresPermission(AdminOperationsPermission.class)
    public static class CreateJournalGroupAction extends FormViewAction<CreateJournalGroupForm>
    {
        private Journal _journal;

        @Override
        public void validateCommand(CreateJournalGroupForm target, Errors errors) {}


        @Override
        public ModelAndView getView(CreateJournalGroupForm form, boolean reshow, BindException errors)
        {
            JspView view = new JspView<>("/org/labkey/targetedms/view/publish/createJournalGroup.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Create New Journal Group");
            return view;
        }

        @Override
        public boolean handlePost(CreateJournalGroupForm form, BindException errors)
        {
            // Validate the group name.
            String message = UserManager.validGroupName(form.getGroupName(), PrincipalType.GROUP);
            if(message != null)
            {
                errors.addError(new LabKeyError(message));
            }
            else
            {
                Integer groupId = org.labkey.api.security.SecurityManager.getGroupId(null, form.getGroupName(), null, false);
                if (groupId != null)
                {
                    errors.addError(new LabKeyError("Group with name " + form.getGroupName() + " already exists."));
                }
            }

            // Validate the project name.
            StringBuilder error = new StringBuilder();
            if (Container.isLegalName(form.getProjectName(), true, error))
            {
                if (ContainerManager.getRoot().getChild(form.getProjectName()) != null)
                {
                    errors.addError(new LabKeyError("Project name " + form.getProjectName() + " already exists."));
                }
            }
            else if(error.length() > 0)
            {
                errors.addError(new LabKeyError(error.toString()));
            }

            // Validate the journal name
            if(StringUtils.isBlank(form.getJournalName()))
            {
                errors.addError(new LabKeyError("Journal name cannot be blank."));
            }
            else
            {
                if(JournalManager.getJournal(form.getJournalName()) != null)
                {
                    errors.addError(new LabKeyError("Journal with name " + form.getJournalName() + " already exists"));
                }
            }

            if(errors.getErrorCount() > 0)
            {
                return false;
            }

            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                // Create the project.
                Container container = ContainerManager.createContainer(ContainerManager.getRoot(), form.getProjectName(), null, null, NormalContainerType.NAME, getUser());
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

    @RequiresPermission(AdminOperationsPermission.class)
    public static class DeleteJournalGroupAction extends ConfirmAction<JournalForm>
    {
        @Override
        public ModelAndView getConfirmView(JournalForm form, BindException errors)
        {
            return FormPage.getView(PublishTargetedMSExperimentsController.class, form, "view/publish/deleteJournal.jsp");
        }

        @Override
        public boolean handlePost(JournalForm form, BindException errors)
        {
            Journal journal = form.lookupJournal();

            if(journal == null)
            {
                errors.addError(new LabKeyError("No journal found for journal ID  " + form.getId()));
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

    @RequiresPermission(AdminPermission.class)
    public static class JournalGroupDetailsAction extends SimpleViewAction<JournalForm>
    {
        @Override
        public ModelAndView getView(JournalForm form, BindException errors)
        {
            Journal journal = form.lookupJournal();
            if(journal == null)
            {
                errors.addError(new LabKeyError("No journal found for journal ID  " + form.getId()));
                return new SimpleErrorView(errors, true);
            }

            DataRegion journalDetails = new DataRegion();
            journalDetails.setColumns(TargetedMSManager.getTableInfoJournal().getColumns("Name", "LabkeyGroupId", "Project", "Created", "CreatedBy"));

            ButtonBar buttonBar = new ButtonBar();
            buttonBar.setStyle(ButtonBar.Style.separateButtons);
            if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
            {
                ActionURL url = new ActionURL(DeleteJournalGroupAction.class, getViewContext().getContainer());
                ActionButton deleteJournalButton = new ActionButton(url, "Delete");
                deleteJournalButton.setActionType(ActionButton.Action.GET);
                buttonBar.add(deleteJournalButton);
            }
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
        public ModelAndView getView(CopyExperimentForm form, boolean reshow, BindException errors)
        {
            validateAction(form);

            JspView view = new JspView("/org/labkey/targetedms/view/publish/copyExperimentForm.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Copy Targeted MS Experiment");
            return view;
        }

        private void validateAction(CopyExperimentForm form)
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
        public boolean handlePost(CopyExperimentForm form, BindException errors)
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
            if(!Container.isLegalName(destinationFolder, parentContainer.isRoot(), errMessages))
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
            Container target = ContainerManager.createContainer(parentContainer, destinationFolder, null, null, NormalContainerType.NAME, getUser());

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
    public static class ViewPublishExperimentFormAction extends SimpleViewAction<PublishExperimentForm>
    {
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        @Override
        public ModelAndView getView(PublishExperimentForm form, BindException errors)
        {
            ExperimentAnnotations exptAnnotations = form.lookupExperiment();
            if(exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with id " + form.getId());
            }

            return getPublishFormView(form, exptAnnotations, errors, !form.isUpdate());
        }
    }

    private static final int RANDOM_URL_SIZE = 6;
    private static JspView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors, boolean newForm)
    {
        PublishExperimentFormBean bean = new PublishExperimentFormBean();
        bean.setForm(form);
        bean.setJournalList(JournalManager.getJournals());
        bean.setExperimentAnnotations(exptAnnotations);

        if(newForm)
        {
            form.setShortAccessUrl(generateRandomUrl(RANDOM_URL_SIZE));
            List<Journal> journals = JournalManager.getJournals();
            if (journals.size() == 0)
            {
                throw new NotFoundException("Could not find any journals.");
            }
            form.setJournalId(journals.get(0).getId()); // This is "Panorama Public" on panoramaweb.org
        }
        else
        {
            JournalExperiment journalExperiment = JournalManager.getJournalExperiment(exptAnnotations.getId(), form.getJournalId());
            if(journalExperiment == null)
            {
                throw new NotFoundException("Could not find an entry in JournalExperiment for experiment ID " + exptAnnotations.getId() + " and journal ID " + form.getJournalId());
            }
            form.setShortAccessUrl(journalExperiment.getShortAccessUrl().getShortURL());
            form.setJournalId(journalExperiment.getJournalId());
        }

        JspView view = new JspView("/org/labkey/targetedms/view/publish/publishExperimentForm.jsp", bean, errors);
        view.setFrame(WebPartView.FrameType.PORTAL);
        view.setTitle("Submission Request to " + form.lookupJournal().getName());
        return view;
    }

    private static String generateRandomUrl(int length)
    {
        ShortURLService shortUrlService = ShortURLService.get();
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

    @RequiresPermission(AdminPermission.class)
    public static class PublishExperimentAction extends JournalExperimentAction
    {
        @Override
        public void validateForm(PublishExperimentForm form, Errors errors)
        {
            validateJournal(errors, _experimentAnnotations, _journal);

            // Cannot publish if this is not an "Experimental data" folder.
            TargetedMSModule.FolderType folderType = TargetedMSModule.getFolderType(_experimentAnnotations.getContainer());
            if(folderType != TargetedMSModule.FolderType.Experiment)
            {
                errors.reject(ERROR_MSG,"Only Targeted MS folders of type \"Experimental data\" can be submitted to " + _journal.getName() + ".");
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

            if (_experimentAnnotations.isIncludeSubfolders())
            {
                // Make sure that there is only one experiment in the container tree rooted at this folder.
                List<ExperimentAnnotations> expAnnotations = ExperimentAnnotationsManager.getAllExperiments(_experimentAnnotations.getContainer(), getUser());
                if(expAnnotations.size() > 1)
                {
                    errors.reject(ERROR_MSG, "There are multiple experiments in this folder and its subfolders. " +
                            "The experiment you want to submit should be the only experiment defined in a folder and its subfolders.");
                }
            }
        }

        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            String journal = _journal.getName();
            StringBuilder html = new StringBuilder();
            html.append("You are giving access to ").append(journal).append(" to make a copy of your data. ");
            if(form.isKeepPrivate())
            {
                html.append("Your data on ").append(journal).append(" will be kept private and a reviewer account will be provided to you. ");
            }
            else
            {
                html.append("Your data on ").append(journal).append(" will be made public. ");
            }
            html.append("Are you sure you want to continue?");
            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Submission Request to " + journal);
            return view;
        }

        public ModelAndView getSuccessView(PublishExperimentForm form)
        {
            String journal = _journal.getName();
            ActionURL returnUrl = TargetedMSController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), getContainer());
            StringBuilder html = new StringBuilder();
            html.append("Thank you for submitting your data to ").append(journal).append("!");
            html.append(" We will send you a confirmation email once your data has been copied to ").append(journal).append(". This can take up to a week.");
            if(form.isKeepPrivate())
            {
                html.append(" Your data on ").append(journal).append(" will be kept private and reviewer account details will be included in the confirmation email. ");
            }
            html.append("<br><br>");
            html.append("<a href=" + returnUrl.getEncodedLocalURIString() + "><span class=\"labkey-button\">Back to Experiment Details</span></a>");
            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Request Submitted to " + journal);
            return view;
        }

        public ModelAndView getFailView(PublishExperimentForm form, BindException errors)
        {
            return getPublishFormView(form, _experimentAnnotations, errors, false);
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            // Create a short copy URL.
            assignShortCopyUrl(form);

            JournalExperiment je;
            try
            {
                je = JournalManager.addJournalAccess(_experimentAnnotations, _journal, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
            }
            catch(ValidationException | UnauthorizedException  e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            // Email contact person for the journal
            sendNotification(form, _experimentAnnotations, _journal, je, getUser(), getContainer(), false);

            return true;
        }

        protected void assignShortCopyUrl(PublishExperimentForm form)
        {
            ShortURLService shortUrlService = ShortURLService.get();
            String baseUrl = form.getShortAccessUrl() + "_";
            while(true)
            {
                String random = RandomStringUtils.randomAlphanumeric(RANDOM_URL_SIZE);
                ShortURLRecord shortURLRecord = shortUrlService.resolveShortURL(baseUrl + random);
                if(shortURLRecord == null)
                {
                    form.setShortCopyUrl(baseUrl + random);
                    break;
                }
            }
        }

        void validateJournal(Errors errors, ExperimentAnnotations experiment, Journal journal)
        {
            if(JournalManager.journalHasAccess(journal, experiment))
            {
                errors.reject(ERROR_MSG, journal.getName() + "\" already has access to this experiment. Please select another publication target." );
            }
        }

        void validateShortAccessUrl(PublishExperimentForm form, Errors errors)
        {
            validateShortUrl(form.getShortAccessUrl(), errors);
        }

        private void validateShortUrl(String shortUrl, Errors errors)
        {
            ShortURLService shortUrlService = ShortURLService.get();
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
        private boolean _keepPrivate = false;

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

        public boolean isKeepPrivate()
        {
            return _keepPrivate;
        }

        public void setKeepPrivate(boolean keepPrivate)
        {
            _keepPrivate = keepPrivate;
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

        public void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), form.getJournalId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG,"Could not find an entry in JournalExperiment for experiment ID " + _experimentAnnotations.getId() + " and journal ID " + form.getJournalId());
            }

            // If this experiment has already been copied by the journal, don't allow editing.
            if(_journalExperiment.getCopied() != null)
            {
                errors.reject(ERROR_MSG, "This experiment has already been copied by " + _journal.getName() + ". You cannot change the access URL anymore." );
            }

            if(errors.getErrorCount() > 0)
            {
                return;
            }

            super.validateForm(form, errors);
        }

        protected void validateJournal(Errors errors, ExperimentAnnotations experiment, Journal journal)
        {
            Journal oldJournal = JournalManager.getJournal(_journalExperiment.getJournalId());
            if(oldJournal != null && (!oldJournal.getId().equals(journal.getId())))
            {
                super.validateJournal(errors, experiment, journal);
            }
        }

        protected void validateShortAccessUrl(PublishExperimentForm form, Errors errors)
        {
            ShortURLRecord accessUrlRecord = _journalExperiment.getShortAccessUrl();
            if(!accessUrlRecord.getShortURL().equals(form.getShortAccessUrl()))
            {
                super.validateShortAccessUrl(form, errors);
            }
        }

        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            StringBuilder html = new StringBuilder();
            String journal = _journal.getName();
            html.append("Are you sure you want to update your submission request to " + journal + "? ");
            html.append("<br><br>");
            html.append("The new short access link is: " + AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/" + form.getShortAccessUrl() + ShortURLRecord.URL_SUFFIX);
            html.append("<br>");
            if(form.isKeepPrivate())
            {
                html.append("Your data on ").append(journal).append(" will be kept private and a reviewer account will be provided to you. ");
            }
            else
            {
                html.append("Your data on ").append(journal).append(" will be made public. ");
            }

            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Update Submission Request to " + journal);
            return view;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            if(!_journalExperiment.getShortAccessUrl().getShortURL().equalsIgnoreCase(form.getShortAccessUrl()))
            {
                // Change the short copy URL to match the access URL.
                assignShortCopyUrl(form);

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    JournalManager.updateJournalExperimentUrls(_experimentAnnotations, _journal, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                    transaction.commit();
                }
            }

            sendNotification(form, _experimentAnnotations, _journal, _journalExperiment, getUser(), getContainer(), true);

            return true;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for updating an entry in targetedms.JournalExperiment table
    // ------------------------------------------------------------------------

    private static void sendNotification(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, Journal journal, JournalExperiment journalExperiment,
                                         User user, Container container, boolean updated)
    {
        // Email contact person for the journal
        String journalEmail = LookAndFeelProperties.getInstance(journal.getProject()).getSystemEmailAddress();
        String panoramaAdminEmail = LookAndFeelProperties.getInstance(ContainerManager.getHomeContainer()).getSystemEmailAddress();
        try
        {
            MailHelper.ViewMessage m = MailHelper.createMessage(panoramaAdminEmail, journalEmail);
            m.setSubject(String.format("Access to copy an experiment on Panorama (ID: %s)%s", exptAnnotations.getId(), (updated ? " (**UPDATED**)" : "")));

            StringBuilder text = new StringBuilder("You have been given access to copy an experiment on Panorama.\n\n");
            text.append("ExperimentID: ").append(exptAnnotations.getId());
            text.append("\n\n");
            String containerUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(exptAnnotations.getContainer()).getURIString();
            text.append("The current location of the data is:\n").append(containerUrl);
            text.append("\n\n");
            text.append("The access URL is:\n").append(journalExperiment.getShortAccessUrl().renderShortURL());
            text.append("\n\n");
            text.append("Use the following link to copy the data:\n");
            text.append(journalExperiment.getShortCopyUrl().renderShortURL()).append("\n\n");
            if(form.isKeepPrivate())
            {
                text.append("\n");
                text.append("***Keep data private. Reviewer account requested.***");
                text.append("\n\n");
            }

            text.append("Thank you,\n\nPanorama team");
            m.setText(text.toString());
            MailHelper.send(m, user, container);
        }
        catch (Exception e)
        {
            LOG.error("Failed to send notification email to journal.", e);
        }
    }

    private static void sendDeleteNotification(ExperimentAnnotations exptAnnotations, Journal journal, User user, Container container)
    {
        // Email contact person for the journal
        String journalEmail = LookAndFeelProperties.getInstance(journal.getProject()).getSystemEmailAddress();
        String panoramaAdminEmail = LookAndFeelProperties.getInstance(ContainerManager.getHomeContainer()).getSystemEmailAddress();
        try
        {
            MailHelper.ViewMessage m = MailHelper.createMessage(panoramaAdminEmail, journalEmail);
            m.setSubject(String.format("Publish request for experiment DELETED (ID: %s)", exptAnnotations.getId()));

            StringBuilder text = new StringBuilder("Request to publish to ").append(journal.getName()).append(" has been deleted.\n\n");
            text.append("ExperimentID: ").append(exptAnnotations.getId());
            text.append("\n\n");
            String containerUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(exptAnnotations.getContainer()).getURIString();
            text.append("The current location of the data is:\n").append(containerUrl);
            text.append("\n\n");

            text.append("Thank you,\n\nPanorama team");
            m.setText(text.toString());
            MailHelper.send(m, user, container);
        }
        catch (Exception e)
        {
            LOG.error("Failed to send notification email to journal.", e);
        }
    }

    // ------------------------------------------------------------------------
    // BEGIN Action for deleting an entry in targetedms.JournalExperiment table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteJournalExperimentAction extends JournalExperimentAction
    {
        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            StringBuilder html = new StringBuilder();
            html.append("Are you sure you want to cancel your submission request to ").append(_journal.getName());
            html.append("?");
            html.append("<br><br>");
            html.append("Experiment: ").append(_experimentAnnotations.getTitle());
            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Cancel Submission Request to " + _journal.getName());
            return view;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors)
        {
            JournalManager.deleteJournalAccess(_experimentAnnotations, _journal, getUser());
            sendDeleteNotification(_experimentAnnotations, _journal, getUser(), getContainer());
            return true;
        }

        @Override
        public void validateForm(PublishExperimentForm form, Errors errors)
        {
            JournalExperiment je = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(je == null)
            {
                throw new NotFoundException("Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
            }

            if(je.getCopied() != null)
            {
                errors.reject(ERROR_MSG, "The experiment has already been copied by the journal. Unable to delete short access and copy URLs.");
            }
        }

        @Override
        public String getConfirmText()
        {
            return "Yes";
        }

        @Override
        public String getCancelText()
        {
            return "No";
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
    public static class RepublishJournalExperimentAction extends JournalExperimentAction
    {
        private JournalExperiment _journalExperiment;

        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            StringBuilder html = new StringBuilder();
            html.append("Experiment: ").append(_experimentAnnotations.getTitle());
            html.append("<br><br>");
            html.append("This experiment has already been copied by ").append(_journal.getName());
            html.append(". If you click OK the existing copy on ").append(_journal.getName()).append(" will be deleted and a request will be sent to make a new copy.");
            html.append("<br/>");
            html.append("Are you sure you want to continue?");
            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Resubmit to " + _journal.getName());
            return view;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            // Remember the existing location of the copy in the journal's project
            String journalFolderUrl = AppProps.getInstance().getBaseServerUrl() +  _journalExperiment.getShortAccessUrl().getFullURL();

            try(DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
            {
                Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(_journal.getLabkeyGroupId());
                JournalManager.addJournalPermissions(_experimentAnnotations, journalGroup, getUser());

                _journalExperiment.setCopied(null);
                JournalManager.updateJournalExperiment(_journalExperiment, getUser());

                // Reset the access URL to point to the author's folder
                JournalManager.updateAccessUrl(_experimentAnnotations, _journalExperiment, getUser());

                // Remove shortAccessURL from the existing copy of the experiment in the journal's project
                ExperimentAnnotationsManager.removeShortUrl(_journalExperiment.getExperimentAnnotationsId(),
                                                            _journalExperiment.getShortAccessUrl(), getUser());

                transaction.commit();
            }

            // Email contact person for the journal
            String journalEmail = LookAndFeelProperties.getInstance(_journal.getProject()).getSystemEmailAddress();
            String panoramaAdminEmail = LookAndFeelProperties.getInstance(ContainerManager.getHomeContainer()).getSystemEmailAddress();

            try
            {
                MailHelper.ViewMessage m = MailHelper.createMessage(panoramaAdminEmail, journalEmail);
                m.setSubject(String.format("Request to republish an experiment on Panorama (ID: %s)", _experimentAnnotations.getId()));
                StringBuilder text = new StringBuilder("You have received a request to republish an experiment on Panorama.\n\n");
                text.append("Experiment ID: ").append(_experimentAnnotations.getId()).append("\n\n");
                text.append("The current journal folder is: \n").append(journalFolderUrl);
                text.append("\n\n");
                String containerUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_experimentAnnotations.getContainer()).getURIString();
                text.append("The user folder is:\n").append(containerUrl);
                text.append("\n\n");
                text.append("The access URL is:\n").append(_journalExperiment.getShortAccessUrl().renderShortURL());
                text.append("\n\n");
                text.append("Use the following link to copy the data:\n");
                text.append(_journalExperiment.getShortCopyUrl().renderShortURL()).append("\n\n");
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

        @Override
        public void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG,"Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
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

    @RequiresPermission(AdminPermission.class)
    public abstract static class JournalExperimentAction extends ConfirmAction<PublishExperimentForm>
    {
        protected ExperimentAnnotations _experimentAnnotations;
        protected Journal _journal;

        public abstract void validateForm(PublishExperimentForm form, Errors errors);

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG,"Could not find experiment with Id " + form.getId());
            }

            TargetedMSController.ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find a journal with Id " + form.getJournalId());
            }

            if(errors.getErrorCount() > 0)
            {
                return;
            }

            validateForm(form, errors);
        }
    }

    public static ActionURL getCopyExperimentURL(int experimentAnnotationsId, int journalId, Container container)
    {
        ActionURL result = new ActionURL(PublishTargetedMSExperimentsController.CopyExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        return result;
    }

    public static ActionURL getPublishExperimentURL(int experimentAnnotationsId, Container container)
    {
        ActionURL result = new ActionURL(ViewPublishExperimentFormAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }

    public static ActionURL getRePublishExperimentURL(int experimentAnnotationsId, int journalId, Container container)
    {
        ActionURL result = new ActionURL(RepublishJournalExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        return result;
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                new JournalGroupDetailsAction(),
                new PublishExperimentAction(),
                new UpdateJournalExperimentAction(),
                new DeleteJournalExperimentAction(),
                new RepublishJournalExperimentAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                new CreateJournalGroupAction(),
                new DeleteJournalGroupAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(ContainerManager.getRoot(), user,
                new JournalGroupsAdminViewAction()
            );
        }
    }
}
