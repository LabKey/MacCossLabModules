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
package org.labkey.panoramapublic;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleStreamAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.LocalDirectory;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.Button;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.pipeline.AddPanoramaPublicModuleJob;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;
import org.labkey.panoramapublic.proteomexchange.NcbiUtils;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeServiceException;
import org.labkey.panoramapublic.proteomexchange.PsiInstrumentParser;
import org.labkey.panoramapublic.proteomexchange.PxException;
import org.labkey.panoramapublic.proteomexchange.PxHtmlWriter;
import org.labkey.panoramapublic.proteomexchange.PxXmlWriter;
import org.labkey.panoramapublic.proteomexchange.SubmissionDataStatus;
import org.labkey.panoramapublic.proteomexchange.SubmissionDataValidator;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.view.PanoramaPublicRunListView;
import org.labkey.panoramapublic.view.expannotations.ExperimentAnnotationsFormDataRegion;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentWebPart;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentsWebPart;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.LABEL;
import static org.labkey.api.util.DOM.LK.CHECKBOX;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.at;

/**
 * User: vsharma
 * Date: 8/5/2014
 * Time: 8:39 AM
 */
public class PanoramaPublicController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PanoramaPublicController.class);
    public static final String NAME = "panoramapublic";

    public PanoramaPublicController()
    {
        setActionResolver(_actionResolver);
    }

    private static final Logger LOG = Logger.getLogger(PanoramaPublicController.class);

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
            QueryView qView = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, null);
            qView.setShowDetailsColumn(true);
            DetailsURL detailsUrl = new DetailsURL(new ActionURL(JournalGroupDetailsAction.class, getContainer()), Collections.singletonMap("id", "id"));
            qView.setDetailsURL(detailsUrl);
            qView.setFrame(WebPartView.FrameType.NONE);

            VBox view = new VBox();
            view.addView(new HtmlView("<div style=\"margin:5px;\">Journal groups are used in conjunction with the \"publication protocol\" implemented for the panoramapublic module. " +
                    "The goal of the publication protocol is to provide a mechanism for journals to copy data associated with a manuscript from the author's  project " +
                    " on a Panorama server to the journal's project. " +
                    "Creating a new journal group via this admin console does the following:<ol>" +
                    "<li>Creates a project for the journal with the appropriate web parts added</li>" +
                    "<li>Creates a new security group for members of the journal</li>" +
                    "<li>Create an entry in the Journal table of the panoramapublic schema that links the journal  to the project</li></ol></div>"));

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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Journal Groups");
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
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/publish/createJournalGroup.jsp", form, errors);
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

                // Enable the PanoramaPublic module in this project folder
                Set<Module> activeModules = new HashSet<>(container.getActiveModules());
                activeModules.add(ModuleLoader.getInstance().getModule(PanoramaPublicModule.class));
                container.setActiveModules(activeModules);

                // Set the folder type to "Targeted MS".
                FolderType type = FolderTypeManager.get().getFolderType(TargetedMSService.FOLDER_TYPE_NAME);
                container.setFolderType(type, getUser());
                // Make this an "Experiment data" folder.
                Module targetedMSModule = ModuleLoader.getInstance().getModule(TargetedMSService.MODULE_NAME);
                ModuleProperty moduleProperty = targetedMSModule.getModuleProperties().get(TargetedMSService.FOLDER_TYPE_PROP_NAME);
                moduleProperty.saveValue(getUser(), container, TargetedMSService.FolderType.Experiment.toString());
                // Display only the "Targeted MS Experiment List" webpart.
                Portal.WebPart webPart = Portal.getPortalPart(TargetedMSExperimentsWebPart.WEB_PART_NAME).createWebPart();
                List<Portal.WebPart> newWebParts = Collections.singletonList(webPart);
                Portal.saveParts(container, DefaultFolderType.DEFAULT_DASHBOARD, newWebParts);
                Portal.saveParts(container, Portal.DEFAULT_PORTAL_PAGE_ID, newWebParts); // this will remove the TARGETED_MS_SETUP

                // Add the permissions group
                Group group = SecurityManager.createGroup(container, form.getGroupName());
                writeToAuditLog(group);

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

        private void writeToAuditLog(Group newGroup)
        {
            GroupAuditProvider.GroupAuditEvent event = new GroupAuditProvider.GroupAuditEvent(getContainer().getId(), "A new security group named " + newGroup.getName() + " was created by the " + PanoramaPublicModule.NAME + " module.");
            event.setGroup(newGroup.getUserId());
            AuditLogService.get().addEvent(getUser(), event);
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
        public void addNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("Journal groups", new ActionURL(JournalGroupsAdminViewAction.class, getContainer()));
                root.addChild("Create New Journal Group");
            }
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
            return FormPage.getView("/org/labkey/panoramapublic/view/publish/deleteJournal.jsp", form);
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

            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
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
            journalDetails.setColumns(PanoramaPublicManager.getTableInfoJournal().getColumns("Name", "LabkeyGroupId", "Project", "Created", "CreatedBy"));

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
            QueryView journalExperimentListView = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, errors);
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
        public void addNavTrail(NavTree root)
        {
            if(root != null)
            {
                root.addChild("Journal groups", new ActionURL(JournalGroupsAdminViewAction.class, getContainer()));
                root.addChild("Journal group details");
            }
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

            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/copyExperimentForm.jsp", form, errors);
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

            PanoramaPublicController.ensureCorrectContainer(getContainer(), _experiment.getContainer(), getViewContext());

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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Copy Experiment");
        }
    }

    public static class CopyExperimentForm extends ExperimentIdForm
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
            return PanoramaPublicController.getViewExperimentDetailsURL(getId(), c);
        }
    }

    public static class ExperimentIdForm extends IdForm
    {
        public ExperimentAnnotations lookupExperiment()
        {
            return ExperimentAnnotationsManager.get(getId());
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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Experiment Submission Form");
        }

        @Override
        public ModelAndView getView(PublishExperimentForm form, BindException errors)
        {
            ExperimentAnnotations exptAnnotations = form.lookupExperiment();
            if(exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with id " + form.getId());
            }

            populateForm(form, exptAnnotations);
            return getPublishFormView(form, exptAnnotations, errors);
        }

        private static void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            JournalExperiment journalExperiment = JournalManager.getJournalExperiment(exptAnnotations.getId(), form.getJournalId());
            if(journalExperiment != null)
            {
                form.setShortAccessUrl(journalExperiment.getShortAccessUrl().getShortURL());
                form.setJournalId(journalExperiment.getJournalId());
                form.setKeepPrivate(journalExperiment.isKeepPrivate());
                form.setGetPxid(journalExperiment.isKeepPrivate());
                form.setLabHeadName(journalExperiment.getLabHeadName());
                form.setLabHeadEmail(journalExperiment.getLabHeadEmail());
                form.setLabHeadAffiliation(journalExperiment.getLabHeadAffiliation());
                DataLicense license = journalExperiment.getDataLicense();
                form.setDataLicense(license == null ? DataLicense.defaultLicense().name() : license.name());
            }
            else if(form.getShortAccessUrl() == null)
            {
                form.setShortAccessUrl(generateRandomUrl(RANDOM_URL_SIZE));
                List<Journal> journals = JournalManager.getJournals();
                if (journals.size() == 0)
                {
                    throw new NotFoundException("Could not find any journals.");
                }
                form.setJournalId(journals.get(0).getId()); // This is "Panorama Public" on panoramaweb.org

                form.setDataLicense(DataLicense.defaultLicense().name()); // CC BY 4.0 is default license
            }
        }
    }

    private static final int RANDOM_URL_SIZE = 6;
    private static JspView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors)
    {
        PublishExperimentFormBean bean = new PublishExperimentFormBean();
        bean.setForm(form);
        bean.setJournalList(JournalManager.getJournals());
        bean.setExperimentAnnotations(exptAnnotations);
        bean.setDataLicenseList(Arrays.asList(DataLicense.values()));

        JspView view = new JspView("/org/labkey/panoramapublic/view/publish/publishExperimentForm.jsp", bean, errors);
        view.setFrame(WebPartView.FrameType.PORTAL);
        view.setTitle((form.isUpdate() ? "Update" : "") + " Submission Request to " + form.lookupJournal().getName());
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
            TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(_experimentAnnotations.getContainer());
            if(folderType != TargetedMSService.FolderType.Experiment)
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

            // Check the lab head fields.  They should either all be blank or all filled
            if(!((StringUtils.isBlank(form.getLabHeadName()) && StringUtils.isBlank(form.getLabHeadEmail()) && StringUtils.isBlank(form.getLabHeadAffiliation()))))
            {
                if(StringUtils.isBlank(form.getLabHeadName()))
                {
                    errors.reject(ERROR_MSG,"Please enter a Lab Head Name.");
                }
                if(StringUtils.isBlank(form.getLabHeadEmail()))
                {
                    errors.reject(ERROR_MSG,"Please enter a Lab Head Email");
                }
                else
                {
                    EmailValidator validator = EmailValidator.getInstance();
                    if(!validator.isValid(form.getLabHeadEmail()))
                    {
                        errors.reject(ERROR_MSG,"Lab Head Email is invalid. Please enter a valid email address.");
                    }
                }
                if(StringUtils.isBlank(form.getLabHeadAffiliation()))
                {
                    errors.reject(ERROR_MSG,"Please enter a Lab Head Affiliation");
                }
            }
        }

        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            if(form.isGetPxid() && (!SubmissionDataValidator.isValid(_experimentAnnotations, form.isSkipMetaDataCheck(), form.isSkipRawDataCheck(), form.isSkipModCheck())))
            {
                ActionURL redirectUrl = new ActionURL(PreSubmissionCheckAction.class, _experimentAnnotations.getContainer());
                redirectUrl.addParameter("id", _experimentAnnotations.getId());
                throw new RedirectException(redirectUrl);
            }

            else
            {
                setTitle("Confirm Submission Request");
                PanoramaPublicRequest bean = new PanoramaPublicRequest();
                bean.setExperimentAnnotations(_experimentAnnotations);
                bean.setJournal(_journal);
                bean.setForm(form);

                JspView<PanoramaPublicRequest> view = new JspView<PanoramaPublicRequest>("/org/labkey/panoramapublic/view/publish/confirmSubmit.jsp", bean, errors);
                view.setTitle("Submission Request to " + _journal.getName());
                return view;
            }
        }

        public ModelAndView getSuccessView(PublishExperimentForm form)
        {
            setTitle("Submitted");
            String journal = _journal.getName();
            ActionURL returnUrl = PanoramaPublicController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), getContainer());
            StringBuilder html = new StringBuilder();
            html.append("Thank you for submitting your data to ").append(PageFlowUtil.filter(journal)).append("!");
            html.append(" We will send you a confirmation email once your data has been copied. This can take up to a week.");
            if(form.isKeepPrivate())
            {
                html.append("<br>Your data on ").append(PageFlowUtil.filter(journal)).append(" will be kept private and reviewer account details will be included in the confirmation email. ");
            }
            if(form.isGetPxid())
            {
                html.append("<br>A ProteomeXchange ID will be requested for your data and included in the confirmation email.");
            }

            html.append("<br><br>");
            html.append("<a href=" + returnUrl.getEncodedLocalURIString() + "><span class=\"labkey-button\">Back to Experiment Details</span></a>");
            HtmlView view = new HtmlView(html.toString());
            view.setTitle("Request Submitted to " + journal);
            return view;
        }

        public ModelAndView getFailView(PublishExperimentForm form, BindException errors)
        {
            return getPublishFormView(form, _experimentAnnotations, errors);
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            // Create a short copy URL.
            assignShortCopyUrl(form);

            JournalExperiment je;
            try
            {
                je = JournalManager.addJournalAccess(new PanoramaPublicRequest(_experimentAnnotations, _journal, form), getUser());
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
            return PanoramaPublicController.getViewExperimentDetailsURL(form.getId(), getContainer());
        }

        @Override
        public URLHelper getCancelUrl()
        {
            ActionURL url = getViewContext().getActionURL().clone();
            url = url.setAction(ViewPublishExperimentFormAction.class); // keep parameters same, change action class.
            return url;
        }
    }

    public static final class PublishExperimentFormBean
    {
        private PublishExperimentForm _form;
        private List<Journal> _journalList;
        private List<DataLicense> _dataLicenseList;
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

        public List<DataLicense> getDataLicenseList()
        {
            return _dataLicenseList;
        }

        public void setDataLicenseList(List<DataLicense> dataLicenseList)
        {
            _dataLicenseList = dataLicenseList;
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

    public static final class PanoramaPublicRequest
    {
        private PublishExperimentForm _form;
        private ExperimentAnnotations _experimentAnnotations;
        private Journal _journal;

        public PanoramaPublicRequest(){}
        public PanoramaPublicRequest(ExperimentAnnotations experimentAnnotations, Journal journal, PublishExperimentForm form)
        {
            _form = form;
            _experimentAnnotations = experimentAnnotations;
            _journal = journal;
        }

        public PublishExperimentForm getForm()
        {
            return _form;
        }

        public void setForm(PublishExperimentForm form)
        {
            _form = form;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public void setExperimentAnnotations(ExperimentAnnotations experimentAnnotations)
        {
            _experimentAnnotations = experimentAnnotations;
        }

        public Journal getJournal()
        {
            return _journal;
        }

        public void setJournal(Journal journal)
        {
            _journal = journal;
        }
        public String getShortAccessUrl()
        {
            return _form.getShortAccessUrl();
        }
        public String getShortCopyUrl()
        {
            return _form.getShortCopyUrl();
        }
        public boolean isUpdate()
        {
            return _form.isUpdate();
        }
        public boolean isKeepPrivate()
        {
            return _form.isKeepPrivate();
        }
        public boolean isGetPxid()
        {
            return _form.isGetPxid();
        }
        public String getLabHeadName()
        {
            return _form.getLabHeadName();
        }
        public String getLabHeadAffiliation()
        {
            return _form.getLabHeadAffiliation();
        }
        public String getLabHeadEmail()
        {
            return _form.getLabHeadEmail();
        }
        public String getDataLicense()
        {
            return _form.getDataLicense();
        }
    }

    public static class PublishExperimentForm extends PreSubmissionCheckForm
    {
        private int _journalId;
        private String _shortAccessUrl;
        private String _shortCopyUrl;
        private boolean _update;
        private boolean _keepPrivate;
        private boolean _getPxid;
        private String _labHeadName;
        private String _labHeadAffiliation;
        private String _labHeadEmail;
        private String _dataLicense;

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

        public boolean isGetPxid()
        {
            return _getPxid;
        }

        public void setGetPxid(boolean getPxid)
        {
            _getPxid = getPxid;
        }

        public String getLabHeadName()
        {
            return _labHeadName;
        }

        public void setLabHeadName(String labHeadName)
        {
            _labHeadName = labHeadName;
        }

        public String getLabHeadAffiliation()
        {
            return _labHeadAffiliation;
        }

        public void setLabHeadAffiliation(String labHeadAffiliation)
        {
            _labHeadAffiliation = labHeadAffiliation;
        }

        public String getLabHeadEmail()
        {
            return _labHeadEmail;
        }

        public void setLabHeadEmail(String labHeadEmail)
        {
            _labHeadEmail = labHeadEmail;
        }

        public String getDataLicense()
        {
            return _dataLicense;
        }

        public void setDataLicense(String dataLicense)
        {
            _dataLicense = dataLicense;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for publishing an experiment (provide copy access to a journal)
    // ------------------------------------------------------------------------


    // ------------------------------------------------------------------------
    // BEGIN Action for updating an entry in panoramapublic.JournalExperiment table
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
            setTitle("Confirm Submission Update");
            PanoramaPublicRequest bean = new PanoramaPublicRequest();
            bean.setExperimentAnnotations(_experimentAnnotations);
            bean.setJournal(_journal);
            bean.setForm(form);

            JspView<PanoramaPublicRequest> view = new JspView<>("/org/labkey/panoramapublic/view/publish/confirmSubmit.jsp", bean, errors);
            view.setTitle("Update Submission Request to " + _journal.getName());
            return view;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            _journalExperiment.setKeepPrivate(form.isKeepPrivate());
            _journalExperiment.setPxidRequested(form.isGetPxid());
            _journalExperiment.setDataLicense(DataLicense.resolveLicense(form.getDataLicense()));
            if(!_journalExperiment.getShortAccessUrl().getShortURL().equalsIgnoreCase(form.getShortAccessUrl()))
            {
                // Change the short copy URL to match the access URL.
                assignShortCopyUrl(form);

                try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    JournalManager.updateJournalExperimentUrls(_experimentAnnotations, _journal, _journalExperiment, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                    transaction.commit();
                }
            }
            else
            {
                try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    JournalManager.updateJournalExperiment(_journalExperiment, getUser());
                    transaction.commit();
                }
            }

            sendNotification(form, _experimentAnnotations, _journal, _journalExperiment, getUser(), getContainer(), true);

            return true;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for updating an entry in panoramapublic.JournalExperiment table
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

            StringBuilder text = new StringBuilder("You have been given access to copy an experiment on Panorama" + (updated ? " (**UPDATED**)" : "") +".\n\n");
            text.append("ExperimentID: ").append(exptAnnotations.getId());
            text.append("\n\n");
            String containerUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(exptAnnotations.getContainer()).getURIString();
            text.append("The current location of the data is:\n").append(containerUrl);
            text.append("\n\n");
            text.append("The access URL is:\n").append(journalExperiment.getShortAccessUrl().renderShortURL());
            text.append("\n\n");
            text.append("Use the following link to copy the data:\n");
            text.append(journalExperiment.getShortCopyUrl().renderShortURL()).append("\n\n");
            if(form.isGetPxid())
            {
                text.append("\n***Get ProteomeXchange ID");
                text.append("\nLab Head: ").append(form.getLabHeadName());
                text.append("\nEmail: ").append(form.getLabHeadEmail());
                text.append("\nAffiliation: ").append(form.getLabHeadAffiliation());
                text.append("\n\n");
            }
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
    // BEGIN Action for deleting an entry in panoramapublic.JournalExperiment table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteJournalExperimentAction extends JournalExperimentAction
    {
        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            setTitle("Confirm Delete Submission");
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
            return PanoramaPublicController.getViewExperimentDetailsURL(publishExperimentForm.getId(), getContainer());
        }
    }

    // ------------------------------------------------------------------------
    // END Action for deleting an entry in panoramapublic.JournalExperiment table.
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for resetting an entry in panoramapublic.JournalExperiment table
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
            setTitle("Confirm Resubmit Experiment");
            StringBuilder html = new StringBuilder();
            html.append("Experiment: ").append(_experimentAnnotations.getTitle());
            html.append("<br><br>");
            html.append("This experiment has already been copied by ").append(_journal.getName());
            html.append(". If you click OK the existing copy on ").append(_journal.getName()).append(" will be deleted and a request will be sent to make a new copy.");
            html.append("<br>");
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

            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
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
                if(_journalExperiment.isPxidRequested())
                {
                    text.append("\n***A ProteomeXchangeID is requested.\n\n");
                }
                if(_journalExperiment.isKeepPrivate())
                {
                    text.append("\n***A reviewer account is requested\n\n");
                }
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
            return PanoramaPublicController.getViewExperimentDetailsURL(publishExperimentForm.getId(), getContainer());
        }
    }

    // ------------------------------------------------------------------------
    // END Action for resetting an entry in panoramapublic.JournalExperiment table
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

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

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

    @RequiresPermission(ReadPermission.class)
    public static class CompleteInstrumentAction extends ReadOnlyApiAction<CompletionFieldForm>
    {
        @Override
        public ApiResponse execute(CompletionFieldForm completionForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> completions = new ArrayList<>();

            List<PsiInstrumentParser.PsiInstrument> instruments = new ArrayList<>();
            try
            {
                instruments = PsiInstrumentParser.getInstruments();
            }
            catch (PxException e)
            {
                errors.addError(new LabKeyError("Error reading instrument names from file. " + e.getMessage()));
                LOG.error("Error reading instrument names from file.", e);
            }
            for (PsiInstrumentParser.PsiInstrument instrument : instruments)
            {
                completions.add(new AjaxCompletion(instrument.getDisplayName(), instrument.getName()).toJSON());
            }

            response.put("completions", completions);

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class CompleteOrganismAction extends ReadOnlyApiAction<CompletionFieldForm>
    {
        @Override
        public ApiResponse execute(CompletionFieldForm completionForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> completions = new ArrayList<>();

            try
            {
                if(!StringUtils.isBlank(completionForm.getToken()))
                {
                    completions = NcbiUtils.getCompletions(completionForm.getToken());
                    LOG.info("found " + completions.size() + " matches from NCBI for query string " + PageFlowUtil.encodeURIComponent(completionForm.getToken()));
                }
            }
            catch(Exception e)
            {
                errors.addError(new LabKeyError("Error getting organisms from NCBI. " + e.getMessage()));
                LOG.error("Error getting organisms from NCBI .", e);
            }

            response.put("completions", completions);
            return response;
        }
    }

    public static class CompletionFieldForm
    {
        private String _token;

        public String getToken()
        {
            return _token == null ? "" : _token;
        }

        public void setToken(String substring)
        {
            _token = substring;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class PreSubmissionCheckAction extends SimpleViewAction<PreSubmissionCheckForm>
    {
        @Override
        public ModelAndView getView(PreSubmissionCheckForm form, BindException errors) throws Exception
        {
            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(form.getId());
            if(expAnnot == null)
            {
                errors.reject(ERROR_MSG, "No experiment found in for Id " + form.getId());
                return new SimpleErrorView(errors);
            }

            if(!expAnnot.getContainer().equals(getContainer()))
            {
                ActionURL url = getViewContext().getActionURL().clone();
                url.setContainer(expAnnot.getContainer());
                throw new RedirectException(url);
            }

            if (!hasSkylineDocs(expAnnot))
            {
                errors.reject(ERROR_MSG, "There are no Skyline documents included in this experiment.  " +
                        "Please upload one or more Skyline documents to proceed with the submission request.");
                return new SimpleErrorView(errors);
            }

            SubmissionDataStatus status = SubmissionDataValidator.validateExperiment(expAnnot, form.isSkipMetaDataCheck(), form.isSkipRawDataCheck(), form.isSkipModCheck());
            if(status.isValid())
            {
                throw new RedirectException(getPublishExperimentURL(expAnnot.getId(), getContainer()));
            }
            else
            {
                JspView view = new JspView("/org/labkey/panoramapublic/view/publish/missingExperimentInfo.jsp", status, errors);
                view.setFrame(WebPartView.FrameType.PORTAL);
                view.setTitle("Missing Information in Submission Request");
                return view;
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Pre-submission Check");
        }
    }

    private static boolean hasSkylineDocs(@NotNull ExperimentAnnotations expAnnot)
    {
        TargetedMSService service = TargetedMSService.get();
        if(service != null)
        {
            Set<Container> expContainers = expAnnot.isIncludeSubfolders() ? ContainerManager.getAllChildren(expAnnot.getContainer())
                    : Collections.singleton(expAnnot.getContainer());
            return expContainers.stream().anyMatch(container -> service.getRuns(container).size() > 0);
        }
        return false;
    }

    public static class PreSubmissionCheckForm extends IdForm
    {
        private boolean _skipRawDataCheck;
        private boolean _skipMetaDataCheck;
        private boolean _skipModCheck;
        private boolean _skipAllChecks;

        public boolean isSkipRawDataCheck()
        {
            return _skipAllChecks || _skipRawDataCheck;
        }

        public void setSkipRawDataCheck(boolean skipRawDataCheck)
        {
            _skipRawDataCheck = skipRawDataCheck;
        }

        public boolean isSkipMetaDataCheck()
        {
            return _skipAllChecks || _skipMetaDataCheck;
        }

        public void setSkipMetaDataCheck(boolean skipMetaDataCheck)
        {
            _skipMetaDataCheck = skipMetaDataCheck;
        }

        public boolean isSkipModCheck()
        {
            return _skipAllChecks || _skipModCheck;
        }

        public void setSkipModCheck(boolean skipModCheck)
        {
            _skipModCheck = skipModCheck;
        }

        public boolean isSkipAllChecks()
        {
            return _skipAllChecks;
        }

        public void setSkipAllChecks(boolean skipAllChecks)
        {
            _skipAllChecks = skipAllChecks;
        }
    }
    // ------------------------------------------------------------------------
    // BEGIN Actions for ProteomeXchange
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminOperationsPermission.class)
    public static class GetPxActionsAction extends SimpleViewAction<PxExportForm>
    {
        @Override
        public ModelAndView getView(PxExportForm form, BindException errors) throws Exception
        {
            if(form.getId() <= 0)
            {
                errors.reject(ERROR_MSG, "Invalid experiment annotations ID found in request " + form.getId());
                return new SimpleErrorView(errors);
            }

            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(form.getId());
            if(expAnnot == null)
            {
                errors.reject(ERROR_MSG,"Cannot find experiment with ID " + form.getId());
                return new SimpleErrorView(errors);
            }
            if(!StringUtils.isBlank(expAnnot.getPublicationLink()) && !StringUtils.isBlank(expAnnot.getCitation()))
            {
                form.setPeerReviewed(true);
            }

            form.setTestDatabase(true);
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/publish/pxActions.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("ProteomeXchange Actions");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("ProteomeXchange Actions");
        }
    }

    public static class PxExperimentAnnotations
    {
        private final ExperimentAnnotations _experimentAnnotations;
        private final PxExportForm _form;

        public PxExperimentAnnotations(ExperimentAnnotations experimentAnnotations, PxExportForm form)
        {
            _experimentAnnotations = experimentAnnotations;
            _form = form;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public PxExportForm getForm()
        {
            return _form;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ExportPxXmlAction extends SimpleStreamAction<PxExportForm>
    {
        @Override
        public void render(PxExportForm form, BindException errors, PrintWriter out) throws Exception
        {
            int experimentId = form.getId();
            if(experimentId <= 0)
            {
                out.write("Invalid experiment Id found in request: " + experimentId);
                return;
            }

            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(experimentId);
            if(expAnnot == null)
            {
                out.write("Cannot find experiment with ID " + experimentId);
                return;
            }

            if(!expAnnot.getContainer().equals(getContainer()))
            {
                ActionURL redirect = getViewContext().cloneActionURL();
                redirect.setContainer(expAnnot.getContainer());
                throw new RedirectException(redirect);
            }

            PxXmlWriter annWriter = new PxXmlWriter(out);
            annWriter.write(new PxExperimentAnnotations(expAnnot, form));
        }
    }

    public static class PxExportForm extends ExperimentIdForm
    {
        private boolean _peerReviewed;
        private String _publicationId;
        private String _publicationReference;
        private boolean _testDatabase;
        private String _pxUserName;
        private String _pxPassword;
        private String _changeLog;
        private String _labHeadName;
        private String _labHeadEmail;
        private String _labHeadAffiliation;

        public boolean getPeerReviewed()
        {
            return _peerReviewed;
        }

        public void setPeerReviewed(boolean peerReviewed)
        {
            _peerReviewed = peerReviewed;
        }

        public String getPublicationId()
        {
            return _publicationId;
        }

        public void setPublicationId(String publicationId)
        {
            _publicationId = publicationId;
        }

        public String getPublicationReference()
        {
            return _publicationReference;
        }

        public void setPublicationReference(String publicationReference)
        {
            _publicationReference = publicationReference;
        }

        public boolean isTestDatabase()
        {
            return _testDatabase;
        }

        public void setTestDatabase(boolean testDatabase)
        {
            _testDatabase = testDatabase;
        }

        public String getPxUserName()
        {
            return _pxUserName;
        }

        public void setPxUserName(String pxUserName)
        {
            _pxUserName = pxUserName;
        }

        public String getPxPassword()
        {
            return _pxPassword;
        }

        public void setPxPassword(String pxPassword)
        {
            _pxPassword = pxPassword;
        }

        public String getChangeLog()
        {
            return _changeLog;
        }

        public void setChangeLog(String changeLog)
        {
            _changeLog = changeLog;
        }

        public String getLabHeadName()
        {
            return _labHeadName;
        }

        public void setLabHeadName(String labHeadName)
        {
            _labHeadName = labHeadName;
        }

        public String getLabHeadEmail()
        {
            return _labHeadEmail;
        }

        public void setLabHeadEmail(String labHeadEmail)
        {
            _labHeadEmail = labHeadEmail;
        }

        public String getLabHeadAffiliation()
        {
            return _labHeadAffiliation;
        }

        public void setLabHeadAffiliation(String labHeadAffiliation)
        {
            _labHeadAffiliation = labHeadAffiliation;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public abstract static class PxServiceMethodAction <FormType extends PxExportForm> extends SimpleViewAction<FormType>
    {
        public PxServiceMethodAction(Class<FormType> formClass)
        {
            super(formClass);
        }

        @Override
        public ModelAndView getView(FormType form, BindException errors) throws Exception
        {
            int experimentId = form.getId();
            if(experimentId <= 0)
            {
                errors.reject(ERROR_MSG, "Invalid experiment Id found in request: " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(experimentId);
            if(expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            String pxUser = form.getPxUserName();
            if(StringUtils.isBlank(pxUser))
            {
                errors.reject(ERROR_MSG, "ProteomeXchange username cannot be blank.");
            }
            String pxPassword = form.getPxPassword();
            if(StringUtils.isBlank(pxPassword))
            {
                errors.reject(ERROR_MSG, "ProteomeXchange password cannot be blank.");
            }

            if(errors.hasErrors())
            {
                return new SimpleErrorView(errors, true);
            }

            return getHtmlView(expAnnot, form, pxUser, pxPassword);
        }

        protected File writePxXmlFile(ExperimentAnnotations expAnnot, FormType form) throws PxException
        {
            File xml = getLocalFile(getContainer(), "px.xml");
            // Generate the PX XML
            PxXmlWriter annWriter;
            try (OutputStream out = new FileOutputStream(xml))
            {
                annWriter = new PxXmlWriter(out);
                annWriter.write(new PxExperimentAnnotations(expAnnot, form));
            }
            catch (IOException e)
            {
                throw new PxException("Error writing PX XML file.", e);
            }
            return xml;
        }

        public abstract HtmlView getHtmlView(ExperimentAnnotations expAnnot, FormType form, String pxUser, String pxPassword) throws PxException, ProteomeXchangeServiceException;
    }

    private static File getLocalFile(Container container, String fileName) throws PxException
    {
        java.nio.file.Path fileRoot = FileContentService.get().getFileRootPath(container, FileContentService.ContentType.files);
        if(fileRoot == null)
        {
            throw new PxException("Error writing PX XML.  Could not find file root for container " + container);
        }
        if (FileUtil.hasCloudScheme(fileRoot))
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
            if (root != null)
            {
                LocalDirectory localDirectory = LocalDirectory.create(root, PanoramaPublicModule.NAME);
                return new File(localDirectory.getLocalDirectoryFile(), fileName);
            }
            else
            {
                throw new PxException("Error writing PX XML.  Pipeline root not found for container " + container);
            }
        }
        else
        {
            return fileRoot.resolve(fileName).toFile();
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ValidatePxXmlAction extends PxServiceMethodAction<PxExportForm>
    {
        public ValidatePxXmlAction()
        {
            super(PxExportForm.class);
        }

        @Override
        public HtmlView getHtmlView(ExperimentAnnotations expAnnot, PxExportForm form, String pxUser, String pxPassword) throws PxException, ProteomeXchangeServiceException
        {
            File xml = writePxXmlFile(expAnnot, form);
            String response = ProteomeXchangeService.validatePxXml(xml, form.isTestDatabase(), pxUser, pxPassword);
            StringBuilder html = new StringBuilder();
            html.append("<p>PX XML has been created at ").append(PageFlowUtil.filter(xml.getPath())).append(".</p>");
            html.append("<p>Sent request to validate XML.</p>");
            html.append("<p>Response was: </p>");
            html.append("<p style=\"white-space: pre-wrap;\">").append(PageFlowUtil.filter(response)).append("</p>");
            return new HtmlView(html.toString());
        }
        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Validate ProteomeXchange XML");
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class SubmitPxXmlAction extends PxServiceMethodAction<PxExportForm>
    {
        public SubmitPxXmlAction()
        {
            super(PxExportForm.class);
        }

        @Override
        public HtmlView getHtmlView(ExperimentAnnotations expAnnot, PxExportForm form, String pxUser, String pxPassword) throws PxException, ProteomeXchangeServiceException
        {
            File xml = writePxXmlFile(expAnnot, form);
            String response = ProteomeXchangeService.submitPxXml(xml, form.isTestDatabase(), pxUser, pxPassword);
            StringBuilder html = new StringBuilder();
            html.append("<p>PX XML has been created at ").append(PageFlowUtil.filter(xml.getPath())).append(".</p>");
            html.append("<p>Submitted XML to ProteomeXchange.</p>");
            html.append("<p>Response was: </p>");
            html.append("<p style=\"white-space: pre-wrap;\">").append(PageFlowUtil.filter(response)).append("</p>");
            return new HtmlView(html.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Submit ProteomeXchange XML");
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class UpdatePxXmlAction extends SubmitPxXmlAction
    {
        @Override
        public void validate(PxExportForm form, BindException errors)
        {
            if(StringUtils.isBlank(form.getChangeLog()))
            {
                errors.reject(ERROR_MSG, "Change log cannot be empty.");
            }
            super.validate(form, errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class SavePxIdAction extends PxServiceMethodAction<SavePxIdForm>
    {
        private static final Pattern PXID = Pattern.compile("identifier=(PX[DT]\\d{6})");

        public SavePxIdAction()
        {
            super(SavePxIdForm.class);
        }

        @Override
        public HtmlView getHtmlView(ExperimentAnnotations expAnnot, SavePxIdForm form, String pxUser, String pxPassword) throws PxException, ProteomeXchangeServiceException
        {
            StringBuilder html = new StringBuilder();
            if(expAnnot.getPxid() != null && !form.isOverride())
            {
                html.append("Experiment ID ").append(expAnnot.getId()).append(" is associated with PX ID ").append(expAnnot.getPxid()).append(".");
                html.append("<div style=\"margin-top:20px;\">");
                ActionURL overrideUrl = getViewContext().getActionURL().clone();
                overrideUrl.addParameter("id", expAnnot.getId()).addParameter("override", true).addParameter("testDatabase", form.isTestDatabase());
                html.append(PageFlowUtil.button("Generate New PX ID").href(overrideUrl).addClass("primary"));
                html.append("</div>");
            }
            else
            {
                String response = ProteomeXchangeService.getPxId(form.isTestDatabase(), pxUser, pxPassword);

                html.append("Response from PX Server:");
                html.append("<div style=\"white-space: pre-wrap;margin:10px 0px 10px 0px;\">").append(PageFlowUtil.filter(response)).append("</div>");

                Matcher match = PXID.matcher(response);
                if(match.find())
                {
                    String pxid = match.group(1);

                    // Save the PX ID with the experiment.
                    expAnnot.setPxid(pxid);
                    ExperimentAnnotationsManager.updatePxId(expAnnot, pxid);

                    html.append("<p>");
                    html.append("PX ID: ").append("<span style=\"font-weight-bold;color:red;\">").append(pxid).append("</span> saved for experiment ").append(expAnnot.getId());
                    html.append("</p>");
                }
                else
                {
                    html.append("<p>");
                    html.append("<span style=\"font-weight-bold;color:red;\"> ERROR getting PX ID.</span>");
                    html.append("</p>");
                }
            }

            html.append("<br>");
            html.append(PageFlowUtil.link("Back to PX Actions").href(new ActionURL(GetPxActionsAction.class, getContainer()).addParameter("id", expAnnot.getId())));
            return new HtmlView(html.toString());
        }
        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Save ProteomeXchange ID");
        }
    }

    public static class SavePxIdForm extends PxExportForm
    {
        private boolean _override;

        public boolean isOverride()
        {
            return _override;
        }

        public void setOverride(boolean override)
        {
            _override = override;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class PxXmlSummaryAction extends SimpleViewAction<PxExportForm>
    {
        @Override
        public ModelAndView getView(PxExportForm form, BindException errors) throws Exception
        {
            int experimentId = form.getId();
            if(experimentId <= 0)
            {
                errors.reject(ERROR_MSG, "Invalid experiment Id found in request: " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(experimentId);
            if(expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            StringBuilder summaryHtml = new StringBuilder();
            PxHtmlWriter writer = new PxHtmlWriter(summaryHtml);
            writer.write(new PxExperimentAnnotations(expAnnot, form));

            return new HtmlView(summaryHtml.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Summary of ProteomeXchange Information");
        }
    }

    // ------------------------------------------------------------------------
    // END Actions for ProteomeXchange
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Experiment annotation actions
    // ------------------------------------------------------------------------
    private static final String ADD_SELECTED_RUNS = "addSelectedRuns";
    private static final String SUBMITTER = "submitter";

    @RequiresPermission(InsertPermission.class)
    public class ShowNewExperimentAnnotationFormAction extends SimpleViewAction<NewExperimentAnnotationsForm>
    {

        @Override
        public ModelAndView getView(NewExperimentAnnotationsForm form, BindException errors)
        {
            DataRegion drg = createNewTargetedMsExperimentDataRegion(form, getViewContext());
            InsertView view = new InsertView(drg, errors);
            addExperimentViewDependencies(view);
            view.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            view.setInitialValue(SUBMITTER, getUser().getUserId());
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Targeted MS Experiment");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class SaveNewExperimentAnnotationAction extends FormViewAction<NewExperimentAnnotationsForm>
    {
        private ExperimentAnnotations _expAnnot;

        @Override
        public void validateCommand(NewExperimentAnnotationsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(NewExperimentAnnotationsForm form, boolean reshow, BindException errors)
        {
            // We are here either because handlePost failed or there were errors in the form (e.g. missing required values)
            ExperimentAnnotations expAnnot = form.getBean();

            if (expAnnot.getTitle() == null || expAnnot.getTitle().trim().length() == 0)
            {
                errors.reject(ERROR_MSG, "You must specify a title for the experiment");
            }

            DataRegion drg = createNewTargetedMsExperimentDataRegion(form, getViewContext());
            InsertView view = new InsertView(drg, errors);
            addExperimentViewDependencies(view);
            view.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            if(reshow)
            {
                view.setInitialValues(ViewServlet.adaptParameterMap(form.getRequest().getParameterMap()));
            }
            return view;
        }

        @Override
        public boolean handlePost(NewExperimentAnnotationsForm form, BindException errors)
        {
            _expAnnot = form.getBean();

            if(ExperimentAnnotationsManager.getExperimentIncludesContainer(getContainer()) != null)
            {
                errors.reject(ERROR_MSG, "Failed to create new experiment.  Data in this folder is already part of an experiment.");
                return false;
            }

            if (!StringUtils.isBlank(_expAnnot.getPublicationLink()))
            {
                UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
                if (!urlValidator.isValid(form.getBean().getPublicationLink()))
                {
                    errors.reject(ERROR_MSG, "Publication Link does not appear to be valid. Links should begin with either http or https.");
                    return false;
                }
            }

            // These two values are not set automatically in the form.  They have to be set explicitly.
            form.setAddSelectedRuns("true".equals(getViewContext().getRequest().getParameter(ADD_SELECTED_RUNS)));
            form.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));



            if (errors.getErrorCount() == 0)
            {

                try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
                {
                    // Create a new experiment
                    ExpExperiment experiment = ExperimentService.get().createExpExperiment(getContainer(), makeExpExperimentName(_expAnnot.getTitle()));
                    ensureUniqueLSID(experiment);
                    experiment.save(getUser());

                    // Create a new entry in panoramapublic.experimentannotations
                    _expAnnot.setExperimentId(experiment.getRowId());
                    _expAnnot.setContainer(experiment.getContainer());
                    _expAnnot = ExperimentAnnotationsManager.save(_expAnnot, getUser());

                    // Add all runs in the folder
                    List<? extends ExpRun> runsInFolder = ExperimentService.get().getExpRuns(getContainer(), null, null);
                    int[] runIds = new int[runsInFolder.size()];
                    int i = 0;
                    for(ExpRun run: runsInFolder)
                    {
                        runIds[i++] = run.getRowId();
                    }
                    ExperimentAnnotationsManager.addSelectedRunsToExperiment(experiment, runIds, getUser());

                    transaction.commit();
                }

                return true;
            }
            return false;
        }

        private String makeExpExperimentName(String name)
        {
            ColumnInfo nameCol = ExperimentService.get().getTinfoExperiment().getColumn(FieldKey.fromParts("Name"));
            if (nameCol != null)
            {
                // Truncate name to the max length allowed by Experiment.Name column.
                int maxNameLen = nameCol.getScale();
                if (name != null && name.length() > maxNameLen)
                {
                    String ellipsis = "...";
                    return name.substring(0, maxNameLen - ellipsis.length()) + ellipsis;
                }
            }

            return name;
        }

        private void ensureUniqueLSID(ExpExperiment experiment)
        {
            String lsid = ExperimentService.get().generateLSID(experiment.getContainer(), ExpExperiment.class, experiment.getName());
            int suffix = 1;
            while(ExperimentService.get().getExpExperiment(lsid) != null)
            {
                String name = experiment.getName() + "_" + suffix++;
                lsid =  ExperimentService.get().generateLSID(experiment.getContainer(), ExpExperiment.class, name);
            }
            experiment.setLSID(lsid);
        }

        @Override
        public URLHelper getSuccessURL(NewExperimentAnnotationsForm form)
        {
            if(_expAnnot != null && _expAnnot.getId() > 0)
            {
                return getViewExperimentDetailsURL(_expAnnot.getId(), getContainer());
            }
            else
                return form.getReturnURLHelper();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Targeted MS Experiment");
        }
    }

    public static DataRegion createNewTargetedMsExperimentDataRegion(NewExperimentAnnotationsForm form, ViewContext viewContext)
    {
        DataRegion drg = new ExperimentAnnotationsFormDataRegion(viewContext, form, DataRegion.MODE_INSERT);

        drg.addHiddenFormField(ActionURL.Param.returnUrl, viewContext.getRequest().getParameter(ActionURL.Param.returnUrl.name()));
        drg.addHiddenFormField(ADD_SELECTED_RUNS, Boolean.toString("true".equals(viewContext.getRequest().getParameter(ADD_SELECTED_RUNS))));

        for (String rowId : DataRegionSelection.getSelected(viewContext, false))
        {
            drg.addHiddenFormField(DataRegion.SELECT_CHECKBOX_NAME, rowId);
        }
        drg.addHiddenFormField(DataRegionSelection.DATA_REGION_SELECTION_KEY, viewContext.getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));

        // drg.addHiddenFormField(SUBMITTER, String.valueOf(viewContext.getUser().getUserId()));
        return drg;
    }

    public static class NewExperimentAnnotationsForm extends ExperimentAnnotationsForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private boolean _addSelectedRuns;
        private String _dataRegionSelectionKey;

        public boolean isAddSelectedRuns()
        {
            return _addSelectedRuns;
        }

        public void setAddSelectedRuns(boolean addSelectedRuns)
        {
            _addSelectedRuns = addSelectedRuns;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    public static class ExperimentAnnotationsForm extends BeanViewForm<ExperimentAnnotations>
    {
        public ExperimentAnnotationsForm()
        {
            super(ExperimentAnnotations.class, PanoramaPublicManager.getTableInfoExperimentAnnotations());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowExperimentAnnotationsAction extends SimpleViewAction<ViewExperimentAnnotationsForm>
    {
        @Override
        public ModelAndView getView(final ViewExperimentAnnotationsForm form, BindException errors)
        {
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.get(form.getId());
            if (exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment annotations with ID " + form.getId());
            }

            ExpExperiment experiment = exptAnnotations.getExperiment();
            if(experiment == null)
            {
                throw new NotFoundException("Could not find the base experiment experimentAnnotations with ID " + exptAnnotations.getId());
            }

            // Check container
            ensureCorrectContainer(getContainer(), exptAnnotations.getContainer(), getViewContext());


            // Experiment details
            ExperimentAnnotationsDetails exptDetails = new ExperimentAnnotationsDetails(getUser(), exptAnnotations, true);
            JspView<ExperimentAnnotationsDetails> experimentDetailsView = new JspView<>("/org/labkey/panoramapublic/view/expannotations/experimentDetails.jsp", exptDetails);
            VBox result = new VBox(experimentDetailsView);
            experimentDetailsView.setFrame(WebPartView.FrameType.PORTAL);
            experimentDetailsView.setTitle("Experiment Details");

            // List of runs in the experiment.
//            TargetedMsRunListView.ViewType viewType = exptAnnotations.isJournalCopy() ? TargetedMsRunListView.ViewType.EXPERIMENT_VIEW :
//                    TargetedMsRunListView.ViewType.EDITABLE_EXPERIMENT_VIEW;
            PanoramaPublicRunListView runListView = PanoramaPublicRunListView.createView(getViewContext(), exptAnnotations);
            TableInfo tinfo = runListView.getTable();
            if(tinfo instanceof FilteredTable)
            {
                SQLFragment sql = new SQLFragment();

                sql.append("lsid IN (SELECT run.lsid FROM ");
                sql.append(ExperimentService.get().getTinfoExperimentRun(), "run").append(", ");
                sql.append(ExperimentService.get().getTinfoRunList(), "runlist").append(" ");
                sql.append("WHERE runlist.experimentId = ? AND runlist.experimentRunId = run.rowid) ");
                sql.add(experiment.getRowId());
                ((FilteredTable) tinfo).addCondition(sql);
            }
            result.addView(runListView);

            // List of journals have been provided access to this experiment.
            List<Journal> journals = JournalManager.getJournalsForExperiment(exptAnnotations.getId());
            if(journals.size() > 0)
            {
                QuerySettings qSettings = new QuerySettings(getViewContext(), "Journals", "JournalExperiment");
                qSettings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("ExperimentAnnotationsId"), exptAnnotations.getId()));
                QueryView journalListView = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, errors);
                journalListView.setShowRecordSelectors(false);
                journalListView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
                journalListView.disableContainerFilterSelection();
                journalListView.setShowExportButtons(false);
                journalListView.setShowPagination(false);
                journalListView.setPrintView(false);
                VBox journalsBox = new VBox();
                journalsBox.setTitle("Submission");
                journalsBox.setFrame(WebPartView.FrameType.PORTAL);
                journalsBox.addView(journalListView);
                result.addView(journalsBox);
            }
            return result;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Targeted MS Experiment Details");
        }
    }

    public static class ExperimentAnnotationsDetails
    {
        private ExperimentAnnotations _experimentAnnotations;
        JournalExperiment _lastPublishedRecord;
        private boolean _fullDetails = false;
        private boolean _canPublish = false;

        public ExperimentAnnotationsDetails(){}
        public ExperimentAnnotationsDetails(User user, ExperimentAnnotations exptAnnotations, boolean fullDetails)
        {
            _experimentAnnotations = exptAnnotations;
            _fullDetails = fullDetails;

            Container c = _experimentAnnotations.getContainer();
            TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(c);
            if(folderType == TargetedMSService.FolderType.Experiment)
            {
                _lastPublishedRecord = JournalManager.getLastPublishedRecord(_experimentAnnotations.getId());

                // User needs to be the folder admin to publish an experiment.
                _canPublish = !_experimentAnnotations.isJournalCopy() && c.hasPermission(user, AdminPermission.class);
            }
        }
        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public void setExperimentAnnotations(ExperimentAnnotations experimentAnnotations)
        {
            _experimentAnnotations = experimentAnnotations;
        }

        public boolean isFullDetails()
        {
            return _fullDetails;
        }

        public void setFullDetails(boolean fullDetails)
        {
            _fullDetails = fullDetails;
        }

        public boolean isCanPublish()
        {
            return _canPublish;
        }

        public void setCanPublish(boolean canPublish)
        {
            _canPublish = canPublish;
        }

        public JournalExperiment getLastPublishedRecord()
        {
            return _lastPublishedRecord;
        }

        public void setLastPublishedRecord(JournalExperiment lastPublishedRecord)
        {
            _lastPublishedRecord = lastPublishedRecord;
        }
    }

    public static class ViewExperimentAnnotationsForm
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

    public static void ensureCorrectContainer(Container requestContainer, Container expAnnotContainer, ViewContext viewContext)
    {
        if (!requestContainer.equals(expAnnotContainer))
        {
            ActionURL url = viewContext.cloneActionURL();
            url.setContainer(expAnnotContainer);
            throw new RedirectException(url);
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUpdateExperimentAnnotationsAction extends SimpleViewAction<ExperimentAnnotationsForm>
    {
        public ModelAndView getView(ExperimentAnnotationsForm form, BindException errors)
        {
            form.refreshFromDb();
            ExperimentAnnotations experimentAnnotations = form.getBean();
            if(experimentAnnotations == null)
            {
                throw new NotFoundException("Could not find requested experiment annotations");
            }
            ensureCorrectContainer(getContainer(), experimentAnnotations.getContainer(), getViewContext());

            UpdateView view = new UpdateView(new ExperimentAnnotationsFormDataRegion(getViewContext(), form, DataRegion.MODE_UPDATE), form, errors);
            addExperimentViewDependencies(view);

            view.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update Targeted MS Experiment Details");
        }
    }

    private void addExperimentViewDependencies(DataView view)
    {
        view.addClientDependency(ClientDependency.fromPath("Ext4"));
        view.addClientDependency(ClientDependency.fromPath("internal/jQuery"));
        view.addClientDependency(ClientDependency.fromPath("/PanoramaPublic/css/bootstrap-tagsinput.css"));
        view.addClientDependency(ClientDependency.fromPath("PanoramaPublic/js/bootstrap-tagsinput.min.js"));
        view.addClientDependency(ClientDependency.fromPath("/PanoramaPublic/css/typeahead-examples.css"));
        view.addClientDependency(ClientDependency.fromPath("/PanoramaPublic/js/typeahead.bundle.min.js"));
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateExperimentAnnotationsAction extends FormViewAction<ExperimentAnnotationsForm>
    {
        private int _experimentAnnotationsId;
        public void validateCommand(ExperimentAnnotationsForm target, Errors errors)
        {}

        @Override
        public ModelAndView getView(ExperimentAnnotationsForm form, boolean reshow, BindException errors)
        {
            UpdateView view = new UpdateView(new ExperimentAnnotationsFormDataRegion(getViewContext(), form, DataRegion.MODE_UPDATE), form, errors);
            view.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            return view;
        }

        public boolean handlePost(ExperimentAnnotationsForm form, BindException errors) throws Exception
        {
            _experimentAnnotationsId = form.getBean().getId();
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.get(_experimentAnnotationsId);
            if (exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with ID " + _experimentAnnotationsId);
            }

            // Check container
            ensureCorrectContainer(getContainer(), exptAnnotations.getContainer(), getViewContext());

            if(!StringUtils.isBlank(form.getBean().getPublicationLink()))
            {
                UrlValidator urlValidator = new UrlValidator(new String[] {"http", "https"});
                if(!urlValidator.isValid(form.getBean().getPublicationLink()))
                {
                    errors.reject(ERROR_MSG, "Publication Link does not appear to be valid. Links should begin with either http or https.");
                    return false;
                }
            }

            form.doUpdate();
            return true;
        }

        public ActionURL getSuccessURL(ExperimentAnnotationsForm form)
        {
            return getViewExperimentDetailsURL(_experimentAnnotationsId, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update Targeted MS Experiment");
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteSelectedExperimentAnnotationsAction extends ConfirmAction<SelectedIdsForm>
    {
        @Override
        public ModelAndView getConfirmView(SelectedIdsForm deleteForm, BindException errors)
        {
            setTitle("Confirm Delete Experiments");
            return FormPage.getView("/org/labkey/panoramapublic/view/expannotations/deleteExperimentAnnotations.jsp", deleteForm);
        }

        @Override
        public boolean handlePost(SelectedIdsForm deleteForm, BindException errors)
        {
            return deleteExperimentAnnotations(errors, deleteForm.getIds(), getUser());
        }

        @Override
        public void validateCommand(SelectedIdsForm deleteForm, Errors errors)
        {
            return;
        }

        @Override
        public URLHelper getSuccessURL(SelectedIdsForm deleteExperimentAnnotationForm)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    private static boolean deleteExperimentAnnotations(BindException errors, int[] experimentAnnotationIds, User user)
    {
        ExperimentAnnotations[] experimentAnnotations = new ExperimentAnnotations[experimentAnnotationIds.length];
        int i = 0;
        for(int experimentAnnotationId: experimentAnnotationIds)
        {
            ExperimentAnnotations exp = ExperimentAnnotationsManager.get(experimentAnnotationId);
            Container container = exp.getContainer();
            if(!container.hasPermission(user, DeletePermission.class))
            {
                errors.reject(ERROR_MSG, "You do not have permissions to delete experiments in folder " + container.getPath());
            }
            experimentAnnotations[i++] = exp;
        }

        if(!errors.hasErrors())
        {
            ExperimentService experimentService = ExperimentService.get();
            for(ExperimentAnnotations experiment: experimentAnnotations)
            {
                experimentService.deleteExpExperimentByRowId(experiment.getContainer(), user, experiment.getExperimentId());
            }
            return true;
        }
        return false;
    }

    public static class SelectedIdsForm extends ViewForm implements DataRegionSelection.DataSelectionKeyForm, SelectedExperimentIds
    {
        private String _dataRegionSelectionKey;

        public int[] getIds()
        {
            return PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), getDataRegionSelectionKey(), false));
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    public static interface SelectedExperimentIds
    {
        public int[] getIds();
    }

    public static class DeleteExperimentAnnotationsForm extends ExperimentAnnotationsForm implements SelectedExperimentIds
    {
        public int[] getIds()
        {
            return new int[]{this.getBean().getId()};
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteExperimentAnnotationsAction extends ConfirmAction<DeleteExperimentAnnotationsForm>
    {
        @Override
        public ModelAndView getConfirmView(DeleteExperimentAnnotationsForm deleteForm, BindException errors)
        {
            setTitle("Confirm Delete Experiment");
            return FormPage.getView("/org/labkey/panoramapublic/view/expannotations/deleteExperimentAnnotations.jsp", deleteForm);
        }

        public boolean handlePost(DeleteExperimentAnnotationsForm form, BindException errors)
        {
            int _experimentAnnotationsId = form.getBean().getId();
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.get(_experimentAnnotationsId);
            if (exptAnnotations == null)
            {
                throw new NotFoundException("Could not find experiment with ID " + _experimentAnnotationsId);
            }

            // Check container
            ensureCorrectContainer(getContainer(), exptAnnotations.getContainer(), getViewContext());

            return deleteExperimentAnnotations(errors, form.getIds(), getUser());
        }

        @Override
        public void validateCommand(DeleteExperimentAnnotationsForm deleteForm, Errors errors)
        {
            return;
        }

        @Override
        public URLHelper getSuccessURL(DeleteExperimentAnnotationsForm deleteExperimentAnnotationForm)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class IncludeSubFoldersInExperimentAction extends FormHandlerAction<ExperimentForm>
    {
        private ExperimentAnnotations _expAnnot;

        public void validateCommand(ExperimentForm form, Errors errors)
        {
        }

        public boolean handlePost(ExperimentForm form, BindException errors)
        {
            _expAnnot = form.lookupExperiment();
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup experiment annotations with ID " + form.getId());
                return false;
            }

            ExpExperiment experiment = _expAnnot.getExperiment();
            if(experiment == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup base experiment for experimentAnnotations with ID " + _expAnnot.getTitle());
                return false;
            }

            ensureCorrectContainer(getContainer(), experiment.getContainer(), getViewContext());

            if(!experiment.getContainer().hasPermission(getUser(), InsertPermission.class))
            {
                errors.reject(ERROR_MSG, "User does not have permissions to perform the requested action.");
                return false;
            }

            if(ExperimentAnnotationsManager.hasExperimentsInSubfolders(_expAnnot.getContainer(), getUser()))
            {
                errors.reject(ERROR_MSG, "At least one of the subfolders contains an experiment. Cannot add subfolder data to this experiment.");
                return false;
            }

            ExperimentAnnotationsManager.includeSubfoldersInExperiment(_expAnnot, getUser());
            return true;
        }

        public ActionURL getSuccessURL(ExperimentForm form)
        {
            return getViewExperimentDetailsURL(_expAnnot.getId(), getContainer());
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ExcludeSubFoldersInExperimentAction extends FormHandlerAction<ExperimentForm>
    {
        private ExperimentAnnotations _expAnnot;

        public void validateCommand(ExperimentForm form, Errors errors)
        {
        }

        public boolean handlePost(ExperimentForm form, BindException errors)
        {
            _expAnnot = form.lookupExperiment();
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup experiment annotations with ID " + form.getId());
                return false;
            }

            ExpExperiment experiment = _expAnnot.getExperiment();
            if(experiment == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup base experiment for experimentAnnotations with ID " + _expAnnot.getTitle());
                return false;
            }

            ensureCorrectContainer(getContainer(), experiment.getContainer(), getViewContext());

            if(!experiment.getContainer().hasPermission(getUser(), InsertPermission.class))
            {
                errors.reject(ERROR_MSG, "User does not have permissions to perform the requested action.");
                return false;
            }

            ExperimentAnnotationsManager.excludeSubfoldersFromExperiment(_expAnnot, getUser());

            return true;
        }

        public ActionURL getSuccessURL(ExperimentForm form)
        {
            return getViewExperimentDetailsURL(_expAnnot.getId(), getContainer());
        }
    }

    public static class ExperimentForm
    {
        private Integer _id;

        public Integer getId()
        {
            return _id;
        }

        public void setId(Integer id)
        {
            _id = id;
        }

        public ExperimentAnnotations lookupExperiment()
        {
            return getId() == null ? null : ExperimentAnnotationsManager.get(getId());
        }
    }

    public static void configureRawDataTab(Portal.WebPart webPart, Container c, FileContentService service)
    {
        if (null != service)
        {
            Path fileRoot = service.getFileRootPath(c, FileContentService.ContentType.files);
            if (fileRoot != null)
            {
                Path rawFileDir = fileRoot.resolve(TargetedMSService.RAW_FILES_DIR);
                if (Files.exists(rawFileDir))
                {
                    // TODO:  Not sure this is required. This seems to already be set to @files/RawFiles.  In S3 folders, however,
                    // the "Customize Files" UI does not have any folder selected as "File Root".  But there are no errors.
                    String fileRootString = FileContentService.FILES_LINK + "/" + TargetedMSService.RAW_FILES_DIR + "/";
                    webPart.setProperty(FilesWebPart.FILE_ROOT_PROPERTY_NAME, fileRootString);
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // BEGIN Add the PanoramaPublic module to existing TargetedMS containers
    // ------------------------------------------------------------------------
    @RequiresSiteAdmin
    public class AddPanoramaPublicModuleAction extends FormViewAction<AddPanoramaPublicModuleForm>
    {
        @Override
        public void validateCommand(AddPanoramaPublicModuleForm target, Errors errors) {}

        @Override
        public ModelAndView getView(AddPanoramaPublicModuleForm form, boolean reshow, BindException errors)
        {
            return new HtmlView("Add PanoramaPublic Module",
                                DIV("Add the PanoramaPublic module to all TargetedMS type containers under " + getContainer().getPath(),
                                    FORM(
                                            at(method, "POST"),
                                            CHECKBOX(at(name, "dryRun")),
                                            LABEL("Dry run"),
                                            BR(),
                                            new Button.ButtonBuilder("Start").submit(true).build()
                                    )
                                ));
        }

        @Override
        public boolean handlePost(AddPanoramaPublicModuleForm form, BindException errors) throws Exception
        {
            PipelineJob job = new AddPanoramaPublicModuleJob(getViewBackgroundInfo(), PipelineService.get().getPipelineRootSetting(ContainerManager.getRoot()), form.isDryRun());
            PipelineService.get().queueJob(job);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AddPanoramaPublicModuleForm form)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Add Panorama Public Module");
        }
    }

    private static class AddPanoramaPublicModuleForm
    {
        private boolean _dryRun;

        public boolean isDryRun()
        {
            return _dryRun;
        }

        public void setDryRun(boolean dryRun)
        {
            _dryRun = dryRun;
        }
    }
    // ------------------------------------------------------------------------
    // END Add the PanoramaPublic module to all existing TargetedMS containers
    // ------------------------------------------------------------------------


    public static ActionURL getEditExperimentDetailsURL(Container c, int experimentAnnotationsId, URLHelper returnURL)
    {
        ActionURL url = new ActionURL(ShowUpdateExperimentAnnotationsAction.class, c);
        url.addParameter("id", experimentAnnotationsId);  // The name of the parameter is important. This is used to populate the TableViewForm (refreshFromDb())
        if(returnURL != null)
        {
            url.addReturnURL(returnURL);
        }
        return url;
    }

    public static ActionURL getDeleteExperimentURL(Container c, int experimentAnnotationsId, URLHelper returnURL)
    {
        ActionURL url = new ActionURL(DeleteExperimentAnnotationsAction.class, c);
        url.addParameter("id", experimentAnnotationsId);
        if(returnURL != null)
        {
            url.addReturnURL(returnURL);
        }
        return url;
    }

    public static ActionURL getIncludeSubfoldersInExperimentURL(int experimentAnnotationsId, Container container, URLHelper returnURL)
    {
        ActionURL result = new ActionURL(IncludeSubFoldersInExperimentAction.class, container);
        if (returnURL != null)
        {
            result.addParameter(ActionURL.Param.returnUrl, returnURL.getLocalURIString());
        }
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }

    public static ActionURL getExcludeSubfoldersInExperimentURL(int experimentAnnotationsId, Container container, URLHelper returnURL)
    {
        ActionURL result = new ActionURL(ExcludeSubFoldersInExperimentAction.class, container);
        if (returnURL != null)
        {
            result.addParameter(ActionURL.Param.returnUrl, returnURL.getLocalURIString());
        }
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }

    public static ActionURL getViewExperimentDetailsURL(int experimentAnnotationsId, Container container)
    {
        ActionURL result = new ActionURL(PanoramaPublicController.ShowExperimentAnnotationsAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }
    // ------------------------------------------------------------------------
    // END Actions to create, delete, edit and view experiment annotations.
    // ------------------------------------------------------------------------
    public static ActionURL getCopyExperimentURL(int experimentAnnotationsId, int journalId, Container container)
    {
        ActionURL result = new ActionURL(PanoramaPublicController.CopyExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        return result;
    }

    public static ActionURL getPublishExperimentURL(int experimentAnnotationsId, Container container)
    {
        return getPublishExperimentURL(experimentAnnotationsId, container, true, true);
    }

    public static ActionURL getPublishExperimentURL(int experimentAnnotationsId, Container container, boolean keepPrivate, boolean getPxId)
    {
        ActionURL result = new ActionURL(ViewPublishExperimentFormAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getPrePublishExperimentCheckURL(int experimentAnnotationsId, Container container)
    {
        ActionURL result = new ActionURL(PreSubmissionCheckAction.class, container);
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
            assertTrue(user.hasSiteAdminPermission());

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                new JournalGroupDetailsAction(),
                new PublishExperimentAction(),
                new UpdateJournalExperimentAction(),
                new DeleteJournalExperimentAction(),
                new RepublishJournalExperimentAction(),
                new PxXmlSummaryAction(),
                new PreSubmissionCheckAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                new CreateJournalGroupAction(),
                new DeleteJournalGroupAction(),
                new GetPxActionsAction(),
                new ExportPxXmlAction(),
                new ValidatePxXmlAction(),
                new SavePxIdAction(),
                new SubmitPxXmlAction(),
                new UpdatePxXmlAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(ContainerManager.getRoot(), user,
                new JournalGroupsAdminViewAction()
            );
        }
    }
}
