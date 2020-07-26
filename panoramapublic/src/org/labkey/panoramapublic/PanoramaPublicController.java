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
import org.labkey.api.data.ContainerService;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.PropertyManager;
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
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.Button;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.PxXml;
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
import org.labkey.panoramapublic.query.PxXmlManager;
import org.labkey.panoramapublic.view.PanoramaPublicRunListView;
import org.labkey.panoramapublic.view.expannotations.ExperimentAnnotationsFormDataRegion;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentWebPart;
import org.labkey.panoramapublic.view.expannotations.TargetedMSExperimentsWebPart;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.action;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.LABEL;
import static org.labkey.api.util.DOM.LK.CHECKBOX;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.createHtmlFragment;

/**
 * User: vsharma
 * Date: 8/5/2014
 * Time: 8:39 AM
 */
public class PanoramaPublicController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PanoramaPublicController.class);
    public static final String NAME = "panoramapublic";
    public static final String PANORAMA_REVIEWER_PREFIX = "panorama+reviewer";
    public static final String PUBMED_ID = "^[0-9]{1,8}$"; // https://libguides.library.arizona.edu/c.php?g=406096&p=2779570

    public PanoramaPublicController()
    {
        setActionResolver(_actionResolver);
    }

    private static final Logger LOG = Logger.getLogger(PanoramaPublicController.class);

    // ------------------------------------------------------------------------
    // BEGIN Actions for journal groups.
    // ------------------------------------------------------------------------
    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
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

            ModelAndView pxCredentialsLink = getPXCredentialsLink();

            view.addView(qView);
            view.addView(pxCredentialsLink);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Journal groups");
            return view;
        }

        private ModelAndView getPXCredentialsLink()
        {
            ActionURL url = new ActionURL(ManageProteomeXchangeCredentials.class, getContainer());
            return new HtmlView(DIV(at(style, "margin-top:20px;"),
                    new Link.LinkBuilder("Set ProteomeXchange Credentials").href(url).build()));
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

            if(JournalManager.getExperimentsForJournal(journal.getId()).size() > 0)
            {
                // Do not delete the journal via the Admin console link if it contains any published data.
                // The journal project can still be deleted from the LabKey UI, however.
                errors.addError(new LabKeyError("The journal project contains published experiments. It cannot be deleted."));
                return false;
            }

            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                // Delete the journal.
                JournalManager.delete(journal, getUser());

                // Delete the permissions group created for this journal.
                SecurityManager.deleteGroup(SecurityManager.getGroup(journal.getLabkeyGroupId()));

                // Delete the project created for this journal.
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

    @RequiresPermission(AdminOperationsPermission.class)
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
            journalDetails.setColumns(PanoramaPublicManager.getTableInfoJournal().getColumns("Name", "LabkeyGroupId", "Project", "Created", "CreatedBy", "SupportContainer"));

            ButtonBar buttonBar = new ButtonBar();
            buttonBar.setStyle(ButtonBar.Style.separateButtons);
            if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
            {
                ActionURL url = new ActionURL(DeleteJournalGroupAction.class, getViewContext().getContainer());
                ActionButton deleteJournalButton = new ActionButton(url, "Delete");
                deleteJournalButton.setActionType(ActionButton.Action.GET);
                buttonBar.add(deleteJournalButton);

                ActionURL changeSupportContainerUrl = new ActionURL(ChangeJournalSupportContainerAction.class, getContainer());
                ActionButton changeSupportContainerButton = new ActionButton(changeSupportContainerUrl, "Change Support Container");
                changeSupportContainerButton.setActionType(ActionButton.Action.GET);
                changeSupportContainerButton.setPrimary(false);
                buttonBar.add(changeSupportContainerButton);
            }
            journalDetails.setButtonBar(buttonBar);

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
            view.addView(new HtmlView(DIV("This journal has access to the following Targeted MS experiments:")));
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

    /*
    Support container for a Journal (e.g. "PanoramaPublic on panoramweb.org) is where new submission requests, as well as updates related to a submission
    are posted in a message board. By default, it is the same container as the Journal's project container. But site admins should be able to
    change it to another container.
     */
    @RequiresPermission(AdminOperationsPermission.class)
    public static class ChangeJournalSupportContainerAction extends FormViewAction<JournalSupportContainerForm>
    {
        private Journal _journal;
        @Override
        public void validateCommand(JournalSupportContainerForm form, Errors errors)
        {
            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "No journal found for journal ID  " + form.getId());
            }
            if(StringUtils.isBlank(form.getSupportContainerPath()))
            {
                errors.reject(ERROR_MSG, "Support container path cannot be blank");
            }
            else if(form.getSupportContainer() == null)
            {
                errors.reject(ERROR_MSG, "Could not find a container for the given path: '" + form.getSupportContainerPath() + "'");
            }
        }

        @Override
        public ModelAndView getView(JournalSupportContainerForm form, boolean reshow, BindException errors)
        {
            if(!reshow)
            {
                _journal = form.lookupJournal();
                if(_journal == null)
                {
                    errors.reject(ERROR_MSG, "Did not find a journal for journalId: " + form.getId());
                    return new SimpleErrorView(errors);
                }
                form.setSupportContainerPath(_journal.getSupportContainer().getPath());
            }

            VBox view = new VBox();
            if(errors.getErrorCount() > 0)
            {
                StringBuilder html = new StringBuilder();
                for(ObjectError error: errors.getAllErrors())
                {
                    HtmlString errHtml = createHtmlFragment(SPAN(cl("labkey-error"), error.getDefaultMessage()));
                    errHtml.appendTo(html);
                }
                BR().appendTo(html);
                view.addView(new HtmlView(HtmlString.unsafe(html.toString())));
            }

            view.addView(new HtmlView(
                    DIV("Support container is where messages for new requests are posted.  The default location is the main project container of the Journal.",
                            FORM(at(method, "POST", action, getChangeSupportContainerUrl(_journal.getId(), getContainer())),
                                SPAN(cl("labkey-form-label"), "Support Container Path"),
                                INPUT(at(type, "Text", name, "supportContainerPath", value, form.getSupportContainerPath())),
                                BR(),
                                new Button.ButtonBuilder("Save").submit(true).build(),
                                new Button.ButtonBuilder("Cancel").submit(false).href(getJournalGroupDetailsUrl(_journal.getId(), getContainer())).build()
                            )
                    )
            ));
            view.setTitle("Change Journal Support Container");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public boolean handlePost(JournalSupportContainerForm form, BindException errors) throws Exception
        {
            _journal.setSupportContainer(form.getSupportContainer());
            JournalManager.updateJournal(_journal, getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(JournalSupportContainerForm form)
        {
            return getJournalGroupDetailsUrl(form.getId(), getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if(_journal != null)
            {
                root.addChild("Journal group details", getJournalGroupDetailsUrl(_journal.getId(), getContainer()));
            }
            root.addChild("Journal group support container");
            return root;
        }
    }

    private static ActionURL getJournalGroupDetailsUrl(int journalId, Container container)
    {
        ActionURL url = new ActionURL(JournalGroupDetailsAction.class, container);
        url.addParameter("id", journalId);
        return url;
    }

    private static ActionURL getChangeSupportContainerUrl(int journalId, Container container)
    {
        ActionURL url = new ActionURL(ChangeJournalSupportContainerAction.class, container);
        url.addParameter("id", journalId);
        return url;
    }

    public static class JournalSupportContainerForm extends JournalForm
    {
        private String _supportContainerPath;

        public String getSupportContainerPath()
        {
            return _supportContainerPath;
        }

        public void setSupportContainerPath(String supportContainerPath)
        {
            _supportContainerPath = supportContainerPath;
        }

        public Container getSupportContainer()
        {
            if(!StringUtils.isBlank(_supportContainerPath))
            {
                return ContainerService.get().getForPath(_supportContainerPath);
            }
            return null;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ManageProteomeXchangeCredentials extends FormViewAction<PXCredentialsForm>
    {
        @Override
        public void validateCommand(PXCredentialsForm form, Errors errors)
        {
            String user = form.getUserName();
            String password = form.getPassword();

            if (StringUtils.isBlank(user))
            {
                errors.reject(ERROR_MSG, "User name cannot be blank");
            }

            if (StringUtils.isBlank(password))
            {
                errors.reject(ERROR_MSG, "Password cannot be blank");
            }
        }

        @Override
        public boolean handlePost(PXCredentialsForm form, BindException errors)
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(ProteomeXchangeService.PX_CREDENTIALS, true);
            map.put(ProteomeXchangeService.PX_USER, form.getUserName());
            map.put(ProteomeXchangeService.PX_PASSWORD, form.getPassword());
            map.save();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(PXCredentialsForm form)
        {
            return null;
        }

        @Override
        public ModelAndView getSuccessView(PXCredentialsForm form)
        {
            ActionURL adminUrl = new ActionURL(JournalGroupsAdminViewAction.class, getContainer());
            return new HtmlView(
                    DIV("ProteomeXchange credentials saved!",
                    BR(),
                    new Link.LinkBuilder("Back to Panorama Public Admin Console").href(adminUrl).build()));
        }

        @Override
        public ModelAndView getView(PXCredentialsForm form, boolean reshow, BindException errors)
        {
            if(!reshow)
            {
                PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(ProteomeXchangeService.PX_CREDENTIALS, false);
                if(map != null)
                {
                    String user = map.get(ProteomeXchangeService.PX_USER);
                    String password = map.get(ProteomeXchangeService.PX_PASSWORD);
                    form.setUserName(user != null ? user : "");
                    form.setPassword(password != null ? password : "");
                }
            }
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/managePxCredentials.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("ProteomeXchange Credentials");
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Set ProteomeXchange Credentials");
        }
    }

    public static class PXCredentialsForm
    {
        private String _userName;
        private String _password;

        public String getUserName()
        {
            return _userName != null ? _userName : "";
        }

        public void setUserName(String userName)
        {
            _userName = userName;
        }

        public String getPassword()
        {
            return _password != null ? _password : "";
        }

        public void setPassword(String password)
        {
            _password = password;
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
        private JournalExperiment _journalExperiment;

        @Override
        public void validateCommand(CopyExperimentForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(CopyExperimentForm form, boolean reshow, BindException errors)
        {
            if(!validateAction(form, errors))
            {
                return new SimpleErrorView(errors);
            }

            if(!reshow)
            {
                CopyExperimentForm.setDefaults(form, _experiment, _journalExperiment);
            }

            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/copyExperimentForm.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Copy Targeted MS Experiment");
            return view;
        }

        private boolean validateAction(CopyExperimentForm form, BindException errors)
        {
            _experiment = form.lookupExperiment();
            if(_experiment == null)
            {
                errors.reject(ERROR_MSG, "Could not find experiment with id " + form.getId());
                return false;
            }

            PanoramaPublicController.ensureCorrectContainer(getContainer(), _experiment.getContainer(), getViewContext());

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find journal with id " + form.getJournalId());
                return false;
            }
            // User initiating the copy must be a member of a journal that was given access
            // to the experiment.
            if(!JournalManager.userHasCopyAccess(_experiment, _journal, getUser()))
            {
                errors.reject(ERROR_MSG,"You do not have permissions to copy this experiment.");
                return false;
            }
            _journalExperiment = JournalManager.getJournalExperiment(_experiment.getId(), _journal.getId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG,"Could not find an entry in JournalExperiment table for experimentId " + _experiment.getId()
                + " and journalId " + _journal.getId());
                return false;
            }
            if(_journalExperiment.getCopied() != null)
            {
                errors.reject(ERROR_MSG, String.format("The experiment ID %d has already been copied.  It cannot be copied again.", _experiment.getId()));
                return false;
            }
            return true;
        }

        @Override
        public boolean handlePost(CopyExperimentForm form, BindException errors)
        {
            if(!validateAction(form, errors))
            {
                return false;
            }

            if(form.isAssignPxId() && !ExperimentAnnotationsManager.hasProteomicData(_experiment, getUser()))
            {
                errors.reject(ERROR_MSG, "Cannot get a ProteomeXchange ID for small molecule data.");
                return false;
            }

            // Validate the data if a ProteomeXchange ID was requested.
            if(_journalExperiment.isPxidRequested())
            {
                SubmissionDataStatus status = SubmissionDataValidator.validateExperiment(_experiment);
                if(_journalExperiment.isIncompletePxSubmission() && !status.canSubmitToPx())
                {
                    errors.reject(ERROR_MSG, "A ProteomeXchange ID was requested for an \"incomplete\" submission.  But the data is not valid for a ProteomeXchange submission");
                    return false;
                }
                if(!_journalExperiment.isIncompletePxSubmission() && !status.isComplete())
                {
                    errors.reject(ERROR_MSG, "Data is not valid for a \"complete\" ProteomeXchange submission.");
                    return false;
                }
            }

            List<String> recipientEmails = new ArrayList<>();
            String replyToEmail = null;
            if(form.isSendEmail())
            {
                if(StringUtils.isBlank(form.getToEmailAddresses()))
                {
                    errors.reject(ERROR_MSG, "Please enter at least one email address.");
                    return false;
                }

                for(String email: form.getToEmailAddressList())
                {
                    ValidEmail vEmail = getValidEmail(email, "Invalid email address in \"To\" field: " + email, errors);
                    if(vEmail != null)
                    {
                        recipientEmails.add(vEmail.getEmailAddress());
                    }
                }

                if(!StringUtils.isBlank(form.getReplyToAddress()))
                {
                    ValidEmail vEmail = getValidEmail(form.getReplyToAddress(), "Invalid email address in \"Reply-To\" field: " + form.getReplyToAddress(), errors);
                    if(vEmail != null)
                    {
                        replyToEmail = vEmail.getEmailAddress();
                    }
                }
                if(errors.getErrorCount() > 0)
                {
                    return false;
                }
            }

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

                CopyExperimentPipelineJob job = new CopyExperimentPipelineJob(info, root, _experiment, _journal);
                job.setAssignPxId(form.isAssignPxId());
                job.setUsePxTestDb(form.isUsePxTestDb());
                job.setReviewerEmailPrefix(form.getReviewerEmailPrefix());
                job.setEmailSubmitter(form.isSendEmail());
                job.setToEmailAddresses(recipientEmails);
                job.setReplyToAddress(replyToEmail);
                job.setDeletePreviousCopy(form.isDeleteOldCopy());
                PipelineService.get().queueJob(job);

                _successURL = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(target);

                return true;
            }
            catch (PipelineValidationException e){
                return false;
            }
        }

        private ValidEmail getValidEmail(String email, String errMsg, BindException errors)
        {
            try
            {
                return new ValidEmail(email);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, errMsg + ". " + e.getMessage());
            }
            return null;
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
        private String _reviewerEmailPrefix;
        private boolean _assignPxId;
        private boolean _usePxTestDb; // Use the test database for getting a PX ID if true
        private boolean _sendEmail;
        private String _toEmailAddresses;
        private String _replyToAddress;
        private boolean _deleteOldCopy;

        static void setDefaults(CopyExperimentForm form, ExperimentAnnotations sourceExperiment, JournalExperiment je)
        {
            if(je.isKeepPrivate())
            {
                form.setReviewerEmailPrefix(PANORAMA_REVIEWER_PREFIX);
            }

            form.setAssignPxId(je.isPxidRequested());
            form.setUsePxTestDb(true); // TODO:

            form.setSendEmail(true);
            User submitter = UserManager.getUser(je.getCreatedBy());
            // CONSIDER: Add other users that have admin access to the folder?
            form.setToEmailAddresses(submitter.getEmail());

            Container sourceExptContainer = sourceExperiment.getContainer();
            Container project = sourceExptContainer.getProject();
            String projectName = project.getName().replaceAll("-", "");
            form.setDestContainerName(projectName + " - " + sourceExptContainer.getName());
        }

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
            return JournalManager.getJournal(getJournalId());
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

        @Override
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

        public String getReviewerEmailPrefix()
        {
            return _reviewerEmailPrefix;
        }

        public void setReviewerEmailPrefix(String reviewerEmailPrefix)
        {
            _reviewerEmailPrefix = reviewerEmailPrefix;
        }

        public boolean isAssignPxId()
        {
            return _assignPxId;
        }

        public void setAssignPxId(boolean assignPxId)
        {
            _assignPxId = assignPxId;
        }

        public boolean isUsePxTestDb()
        {
            return _usePxTestDb;
        }

        public void setUsePxTestDb(boolean usePxTestDb)
        {
            _usePxTestDb = usePxTestDb;
        }

        public boolean isSendEmail()
        {
            return _sendEmail;
        }

        public void setSendEmail(boolean sendEmail)
        {
            _sendEmail = sendEmail;
        }

        public String getToEmailAddresses()
        {
            return _toEmailAddresses;
        }

        public List<String> getToEmailAddressList()
        {
            return StringUtils.isBlank(_toEmailAddresses) ? Collections.emptyList() : Arrays.asList(StringUtils.split(_toEmailAddresses, "\n\r"));
        }

        public void setToEmailAddresses(String toEmailAddresses)
        {
            _toEmailAddresses = toEmailAddresses;
        }

        public String getReplyToAddress()
        {
            return _replyToAddress;
        }

        public void setReplyToAddress(String replyToAddress)
        {
            _replyToAddress = replyToAddress;
        }

        public boolean isDeleteOldCopy()
        {
            return _deleteOldCopy;
        }

        public void setDeleteOldCopy(boolean deleteOldCopy)
        {
            _deleteOldCopy = deleteOldCopy;
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
    public static class PublishExperimentAction extends FormViewAction<PublishExperimentForm>
    {
        ExperimentAnnotations _experimentAnnotations;
        Journal _journal;
        private boolean _doConfirm = false;

        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors)
        {
            if(!reshow && !validateGetRequest(form, errors))
            {
                return new SimpleErrorView(errors);
            }

            if(!reshow)
            {
                populateForm(form, _experimentAnnotations);
            }

            if (!form.isDataValidated())
            {
                // Cannot publish if this is not an "Experimental data" folder.
                TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(_experimentAnnotations.getContainer());
                if (folderType != TargetedMSService.FolderType.Experiment)
                {
                    errors.reject(ERROR_MSG, "Only Targeted MS folders of type \"Experimental data\" can be submitted to " + _journal.getName() + ".");
                    return new SimpleErrorView(errors);
                }

                // Ensure there is at least one Skyline document in submission.
                if (!hasSkylineDocs(_experimentAnnotations))
                {
                    errors.reject(ERROR_MSG, "There are no Skyline documents included in this experiment.  " +
                            "Please upload one or more Skyline documents to proceed with the submission request.");
                    return new SimpleErrorView(errors);
                }

                if(!ExperimentAnnotationsManager.hasProteomicData(_experimentAnnotations, getUser()))
                {
                    // Cannot get a PX ID small molecule data
                    form.setGetPxid(false);
                }
                boolean validateForPx = form.isGetPxid();
                if (validateForPx)
                {
                    SubmissionDataStatus status = SubmissionDataValidator.validateExperiment(_experimentAnnotations);
                    if (!status.isComplete() && !form.isIncompletePxSubmission())
                    {
                        form.setValidationStatus(status);
                        return getMissingInformationView(form, errors);
                    }
                }
                form.setDataValidated(true);
            }

            if(_doConfirm)
            {
                return getConfirmView(form, errors);
            }
            else
            {
                return getPublishFormView(form, _experimentAnnotations, errors);
            }
        }

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            _experimentAnnotations = ExperimentAnnotationsManager.get(form.getId());
            if (_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG, "No experiment found for Id " + form.getId());
                return false;
            }

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            return true;
        }

        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            form.setShortAccessUrl(generateRandomUrl(RANDOM_URL_SIZE));
            List<Journal> journals = JournalManager.getJournals();
            if (journals.size() == 0)
            {
                throw new NotFoundException("Could not find any journals.");
            }
            form.setJournalId(journals.get(0).getId()); // This is "Panorama Public" on panoramaweb.org

            form.setDataLicense(DataLicense.defaultLicense().name()); // CC BY 4.0 is default license
            form.setKeepPrivate(true);
        }

        private JspView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors)
        {
            PublishExperimentFormBean bean = new PublishExperimentFormBean();
            bean.setForm(form);
            bean.setJournalList(JournalManager.getJournals());
            bean.setExperimentAnnotations(exptAnnotations);
            bean.setDataLicenseList(Arrays.asList(DataLicense.values()));

            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/publishExperimentForm.jsp", bean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle(getFormViewTitle(form.lookupJournal().getName()));
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

        String getFormViewTitle(String journalName)
        {
            return "Submission Request to " + journalName;
        }

        ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            setTitle(getConfirmViewTitle());
            PanoramaPublicRequest bean = new PanoramaPublicRequest();
            bean.setExperimentAnnotations(_experimentAnnotations);
            bean.setJournal(_journal);
            bean.setForm(form);

            JspView<PanoramaPublicRequest> confirmView = new JspView<PanoramaPublicRequest>("/org/labkey/panoramapublic/view/publish/confirmSubmit.jsp", bean, errors);
            confirmView.setTitle(getConfirmViewTitle());
            return confirmView;
        }

        String getConfirmViewTitle()
        {
            return "Confirm submission request To " + _journal.getName();
        }

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG,"Could not find experiment with Id " + form.getId());
                return;
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

        void validateForm(PublishExperimentForm form, Errors errors)
        {
            validateJournal(errors, _experimentAnnotations, _journal);

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

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            if(!form.isRequestConfirmed())
            {
                _doConfirm = true;
                return false;
            }
            if(!form.isDataValidated())
            {
                return false;
            }
            return doUpdates(form, errors);
        }

        boolean doUpdates(PublishExperimentForm form, BindException errors) throws ValidationException
        {
            // Create a short copy URL.
            assignShortCopyUrl(form);

            try
            {
                try(DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
                {
                    JournalExperiment je = JournalManager.setupJournalAccess(new PanoramaPublicRequest(_experimentAnnotations, _journal, form), getUser());

                    // Create notifications
                    PanoramaPublicNotification.notifyCreated(_experimentAnnotations, _journal, je, getUser());

                    transaction.commit();
                }
            }
            catch(ValidationException | UnauthorizedException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            return true;
        }

        @Override
        public ModelAndView getSuccessView(PublishExperimentForm form)
        {
            setTitle(getSuccessViewTitle());
            String journal = _journal.getName();
            ActionURL returnUrl = PanoramaPublicController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), getContainer());

            String dataPrivate = "";
            if(form.isKeepPrivate())
            {
                dataPrivate = "Your data on " + journal + " will be kept private";
                dataPrivate += form.isResubmit() ? ". The reviewer account details will be the same before." : " and reviewer account details will be included in the confirmation email.";
            }

            String pxdAssigned = "";
            if(form.isGetPxid())
            {
                pxdAssigned = form.isResubmit() ? "The ProteomeXchange ID assigned to the data will remain the same as before."
                        : "A ProteomeXchange ID will be requested for your data and included in the confirmation email.";
            }

            HtmlView view = new HtmlView(DIV(getSuccessViewText() + " We will send you a confirmation email once your data has been copied.  This can take upto a week.",
                    DIV(dataPrivate),
                    DIV(pxdAssigned),
                    BR(), BR(),
                    new Link.LinkBuilder("Back to Experiment Details").href(returnUrl).build()));

            view.setTitle(getSuccessViewTitle());
            return view;
        }

        String getSuccessViewTitle()
        {
            return "Request submitted to " + _journal.getName();
        }

        String getSuccessViewText()
        {
            return "Thank you for submitting your data to " + _journal.getName() + "!";
        }

        @Override
        public URLHelper getSuccessURL(PublishExperimentForm form)
        {
            return null;
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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Submit Experiment");
        }
    }

    private static final int RANDOM_URL_SIZE = 6;
    private static void assignShortCopyUrl(PublishExperimentForm form)
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

        public PanoramaPublicRequest()
        {
        }

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

        public boolean isResubmit()
        {
            return _form.isResubmit();
        }

        public boolean isKeepPrivate()
        {
            return _form.isKeepPrivate();
        }

        public boolean isGetPxid()
        {
            return _form.isGetPxid();
        }

        public boolean isIncompletePxSubmission()
        {
            return _form.isIncompletePxSubmission();
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
        private boolean _incompletePxSubmission;
        private String _labHeadName;
        private String _labHeadAffiliation;
        private String _labHeadEmail;
        private String _dataLicense;
        private boolean _dataValidated;
        private boolean _requestConfirmed;
        private boolean _resubmit;

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

        public boolean isIncompletePxSubmission()
        {
            return _incompletePxSubmission;
        }

        public void setIncompletePxSubmission(boolean incompletePxSubmission)
        {
            _incompletePxSubmission = incompletePxSubmission;
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

        public boolean isDataValidated()
        {
            return _dataValidated;
        }

        public void setDataValidated(boolean dataValidated)
        {
            _dataValidated = dataValidated;
        }

        public boolean isRequestConfirmed()
        {
            return _requestConfirmed;
        }

        public void setRequestConfirmed(boolean requestConfirmed)
        {
            _requestConfirmed = requestConfirmed;
        }

        public boolean isResubmit()
        {
            return _resubmit;
        }

        public void setResubmit(boolean resubmit)
        {
            _resubmit = resubmit;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for publishing an experiment (provide copy access to a journal)
    // ------------------------------------------------------------------------


    // ------------------------------------------------------------------------
    // BEGIN Action for updating an entry in panoramapublic.JournalExperiment table
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class UpdateJournalExperimentAction extends ResubmitExperimentAction
    {
        @Override
        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            super.populateForm(form, exptAnnotations);
            form.setUpdate(true);
        }

        @Override
        void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), form.getJournalId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG,"Could not find an entry in JournalExperiment for experiment ID " + _experimentAnnotations.getId() + " and journal ID " + form.getJournalId());
                return;
            }

            // If this experiment has already been copied by the journal, don't allow editing.
            if(_journalExperiment.getCopied() != null)
            {
                errors.reject(ERROR_MSG, "This experiment has already been copied by " + _journal.getName() + ". You cannot edit the submission request." );
                return;
            }

            super.validateForm(form, errors);
        }

        String getFormViewTitle(String journalName)
        {
            return "Update Submission Request to " + journalName;
        }

        @Override
        String getConfirmViewTitle()
        {
            return "Update submission request to " + _journal.getName();
        }

        @Override
        String getSuccessViewTitle()
        {
            return "Updated submission request to " + _journal.getName();
        }

        @Override
        String getSuccessViewText()
        {
            return "Your submission request to " + _journal.getName() + " has been updated.";
        }

        @Override
        boolean doUpdates(PublishExperimentForm form, BindException errors) throws ValidationException
        {
            setValuesInJournalExperiment(form);

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                if(!_journalExperiment.getShortAccessUrl().getShortURL().equalsIgnoreCase(form.getShortAccessUrl()))
                {
                    // Change the short copy URL to match the access URL.
                    assignShortCopyUrl(form);
                    JournalManager.updateJournalExperimentUrls(_experimentAnnotations, _journal, _journalExperiment, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                }
                else
                {
                    JournalManager.updateJournalExperiment(_journalExperiment, getUser());
                }
                // Create notifications
                PanoramaPublicNotification.notifyUpdated(_experimentAnnotations, _journal, _journalExperiment, getUser());

                transaction.commit();
            }
            return true;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update Submission Request");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public abstract static class ResubmitExperimentAction extends PublishExperimentAction
    {
        protected JournalExperiment _journalExperiment;

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            if(super.validateGetRequest(form, errors))
            {
                _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), form.getJournalId());
                if(_journalExperiment == null)
                {
                    errors.reject(ERROR_MSG,"Could not find an entry in JournalExperiment for experiment ID " + _experimentAnnotations.getId() + " and journal ID " + form.getJournalId());
                    return false;
                }
            }
            return true;
        }

        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            form.setShortAccessUrl(_journalExperiment.getShortAccessUrl().getShortURL());
            form.setJournalId(_journalExperiment.getJournalId());
            form.setKeepPrivate(_journalExperiment.isKeepPrivate());
            form.setLabHeadName(_journalExperiment.getLabHeadName());
            form.setLabHeadEmail(_journalExperiment.getLabHeadEmail());
            form.setLabHeadAffiliation(_journalExperiment.getLabHeadAffiliation());
            DataLicense license = _journalExperiment.getDataLicense();
            form.setDataLicense(license == null ? DataLicense.defaultLicense().name() : license.name());
        }

        @Override
        void validateJournal(Errors errors, ExperimentAnnotations experiment, Journal journal)
        {
            Journal oldJournal = JournalManager.getJournal(_journalExperiment.getJournalId());
            if(oldJournal != null && (!oldJournal.getId().equals(journal.getId())))
            {
                super.validateJournal(errors, experiment, journal);
            }
        }

        @Override
        void validateShortAccessUrl(PublishExperimentForm form, Errors errors)
        {
            ShortURLRecord accessUrlRecord = _journalExperiment.getShortAccessUrl();
            if(!accessUrlRecord.getShortURL().equals(form.getShortAccessUrl()))
            {
                super.validateShortAccessUrl(form, errors);
            }
        }

        void setValuesInJournalExperiment(PublishExperimentForm form)
        {
            _journalExperiment.setKeepPrivate(form.isKeepPrivate());
            _journalExperiment.setPxidRequested(form.isGetPxid());
            _journalExperiment.setIncompletePxSubmission(form.isIncompletePxSubmission());
            _journalExperiment.setDataLicense(DataLicense.resolveLicense(form.getDataLicense()));
            _journalExperiment.setLabHeadName(form.getLabHeadName());
            _journalExperiment.setLabHeadEmail(form.getLabHeadEmail());
            _journalExperiment.setLabHeadAffiliation(form.getLabHeadAffiliation());
        }
    }
    // ------------------------------------------------------------------------
    // END Action for updating an entry in panoramapublic.JournalExperiment table
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for deleting an entry in panoramapublic.JournalExperiment table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteJournalExperimentAction extends ConfirmAction<PublishExperimentForm>
    {
        protected ExperimentAnnotations _experimentAnnotations;
        protected Journal _journal;
        private JournalExperiment _journalExperiment;

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG,"Could not find experiment with Id " + form.getId());
                return;
            }

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find a journal with Id " + form.getJournalId());
                return;
            }

            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG, "Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
                return;
            }

            if(_journalExperiment.getCopiedExperimentId() != null)
            {
                errors.reject(ERROR_MSG, "The experiment has already been copied by the journal. Unable to delete submission request.");
                return;
            }
        }

        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            setTitle("Confirm Delete Submission");
            HtmlView view = new HtmlView(DIV("Are you sure you want to cancel your submission request to " + _journal.getName() + "?",
                    BR(), BR(),
                    SPAN("Experiment: " + _experimentAnnotations.getTitle())));
            view.setTitle("Cancel Submission Request to " + _journal.getName());
            return view;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors)
        {
            try(DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                JournalManager.removeJournalAccess(_experimentAnnotations, _journal, getUser());

                // Create notifications
                PanoramaPublicNotification.notifyDeleted(_experimentAnnotations, _journal, _journalExperiment, getUser());

                transaction.commit();
            }

            return true;
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
    // BEGIN Action for resubmitting an entry in panoramapublic.JournalExperiment table
    //       -- Set 'Copied' column to null.
    //       -- Give journal copy privilege again.
    //       -- Reset access URL to point to the author's data
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class RepublishJournalExperimentAction extends ResubmitExperimentAction
    {
        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            super.populateForm(form, exptAnnotations);
            form.setResubmit(true);
        }

        String getFormViewTitle(String journalName)
        {
            return "Resubmit Request to " + journalName;
        }

        @Override
        public ModelAndView getConfirmView(PublishExperimentForm form, BindException errors)
        {
            setTitle("Confirm Resubmit Experiment");
            return super.getConfirmView(form, errors);
        }

        @Override
        protected String getConfirmViewTitle()
        {
            return "Confirm resubmission request to " + _journal.getName();
        }

        @Override
        String getSuccessViewTitle()
        {
            return "Request resubmitted to " + _journal.getName();
        }

        @Override
        String getSuccessViewText()
        {
            return "Your request to " + _journal.getName() + " has been resubmitted.";
        }

        @Override
        boolean doUpdates(PublishExperimentForm form, BindException errors) throws ValidationException
        {
            setValuesInJournalExperiment(form);
            _journalExperiment.setCopied(null);

            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(_journal.getLabkeyGroupId());
                JournalManager.addJournalPermissions(_experimentAnnotations, journalGroup, getUser());

                JournalManager.updateJournalExperiment(_journalExperiment, getUser());

                // Reset the access URL to point to the author's folder
                JournalManager.updateAccessUrl(_experimentAnnotations, _journalExperiment, getUser());

                // Remove shortAccessURL from the existing copy of the experiment in the journal's project
                ExperimentAnnotationsManager.removeShortUrl(_journalExperiment.getExperimentAnnotationsId(),
                                                            _journalExperiment.getShortAccessUrl(), getUser());

                // Rename the container where the old copy lives so that the same folder name can be used for the new copy.
                // The container will be deleted after the data has been re-copied.
                ExperimentAnnotations currentJournalExpt = ExperimentAnnotationsManager.get(_journalExperiment.getCopiedExperimentId());
                renameOldContainer(currentJournalExpt.getContainer());
                currentJournalExpt = ExperimentAnnotationsManager.get(_journalExperiment.getCopiedExperimentId()); // query again to get the updated container name
                PanoramaPublicNotification.notifyResubmitted(_experimentAnnotations, _journal, _journalExperiment, currentJournalExpt, getUser());

                transaction.commit();
            }

            return true;
        }

        private void renameOldContainer(Container container)
        {
            String name = container.getName();
            Container parent = container.getParent();
            int version = 1;
            String newName = name + " V." ;
            while(parent.hasChild(newName + version))
            {
                version++;
            }
            ContainerManager.rename(container, getUser(), newName + version);
        }

        @Override
        void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalExperiment = JournalManager.getJournalExperiment(_experimentAnnotations.getId(), _journal.getId());
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG,"Could not find an entry for experiment with Id " + form.getId() + " and journal Id " + _journal.getId());
                return;
            }

            ExperimentAnnotations journalCopy = ExperimentAnnotationsManager.getJournalCopy(_experimentAnnotations);
            if(journalCopy != null && journalCopy.isFinal())
            {
                Journal journal = JournalManager.getJournal(_journalExperiment.getJournalId());
                errors.reject(ERROR_MSG,"The experiment cannot be resubmitted. It has been copied to " + journal.getName()
                        + ", and the copy is final. The publication link is " + PageFlowUtil.filter(journalCopy.getPublicationLink()));
                return;
            }

            super.validateForm(form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Resubmit Request");
        }
    }

    // ------------------------------------------------------------------------
    // END Action for resetting an entry in panoramapublic.JournalExperiment table
    // ------------------------------------------------------------------------

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
        public ModelAndView getView(PreSubmissionCheckForm form, BindException errors)
        {
            ExperimentAnnotations expAnnot = ExperimentAnnotationsManager.get(form.getId());
            if(expAnnot == null)
            {
                errors.reject(ERROR_MSG, "No experiment found for Id " + form.getId());
                return new SimpleErrorView(errors);
            }

            ensureCorrectContainer(getContainer(), expAnnot.getContainer(), getViewContext());

            if (!hasSkylineDocs(expAnnot))
            {
                errors.reject(ERROR_MSG, "There are no Skyline documents included in this experiment.  It cannot be submitted.");
                return new SimpleErrorView(errors);
            }

            boolean validateForPx = ExperimentAnnotationsManager.hasProteomicData(expAnnot, getUser()); // Can get a PX ID only for proteomic data, not small molecule data
            if (validateForPx)
            {
                SubmissionDataStatus status = SubmissionDataValidator.validateExperiment(expAnnot);
                form.setValidationStatus(status);
                if(!status.isComplete())
                {
                    return getMissingInformationView(form, errors);
                }
            }

            String text = validateForPx ? "Data is valid for a complete ProteomeXchange submission." :
                    "No proteomic data was found in the experiment. It was NOT validated for a ProteomeXchange submission.";
            ActionURL returnUrl = form.getReturnActionURL();
            return returnUrl == null ? new HtmlView(DIV(text))
                    : new HtmlView(DIV(text, BR(), new Link.LinkBuilder("Back").href(returnUrl).build()));

        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Pre-submission Check");
        }
    }

    @NotNull
    private static ModelAndView getMissingInformationView(PreSubmissionCheckForm form, BindException errors)
    {
        JspView view = new JspView("/org/labkey/panoramapublic/view/publish/missingExperimentInfo.jsp", form, errors);
        view.setFrame(WebPartView.FrameType.PORTAL);
        view.setTitle("Missing Information in Submission Request");
        return view;
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
        private boolean _notSubmitting;
        private SubmissionDataStatus _validationStatus;

        public boolean isNotSubmitting()
        {
            return _notSubmitting;
        }

        public void setNotSubmitting(boolean notSubmitting)
        {
            _notSubmitting = notSubmitting;
        }

        public SubmissionDataStatus getValidationStatus()
        {
            return _validationStatus;
        }

        public void setValidationStatus(SubmissionDataStatus validationStatus)
        {
            _validationStatus = validationStatus;
        }
    }
    // ------------------------------------------------------------------------
    // BEGIN Actions for ProteomeXchange
    // ------------------------------------------------------------------------
    public enum PX_METHOD {GET_ID, VALIDATE, SUBMIT, UPDATE}

    @RequiresPermission(AdminOperationsPermission.class)
    public static class GetPxActionsAction extends FormViewAction<PxActionsForm>
    {
        private ExperimentAnnotations _expAnnot;
        private JournalExperiment _journalExperiment;
        private String _pxResponse;

        @Override
        public void validateCommand(PxActionsForm form, Errors errors)
        {
            int experimentId = form.getId();

            _expAnnot = ExperimentAnnotationsManager.get(experimentId);
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + experimentId);
                return;
            }

            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());

            if(!_expAnnot.isJournalCopy())
            {
                errors.reject(ERROR_MSG, "ProteomeXchange actions can only be executed on a Panorama Public copy.");
                return;
            }

            _journalExperiment = JournalManager.getRowForJournalCopy(_expAnnot);
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a row in JournalExperiment for experiment ID: " + _expAnnot.getId());
                return;
            }
        }

        @Override
        public ModelAndView getView(PxActionsForm form, boolean reshow, BindException errors)
        {
            if(errors.getErrorCount() > 0)
            {
                if(!StringUtils.isBlank(_pxResponse))
                {
                    errors.addError(new LabKeyError(_pxResponse));
                }
                return new SimpleErrorView(errors, true);
            }

            _expAnnot = ExperimentAnnotationsManager.get(form.getId());
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + form.getId());
                return new SimpleErrorView(errors);
            }

            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());

            if(!reshow)
            {
                form.setTestDatabase(true);
            }

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

        @Override
        public boolean handlePost(PxActionsForm form, BindException errors) throws ProteomeXchangeServiceException, PxException
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(ProteomeXchangeService.PX_CREDENTIALS, false);
            String pxUser = null;
            String pxPassword = null;
            if(map != null)
            {
                pxUser = map.get(ProteomeXchangeService.PX_USER);
                pxPassword = map.get(ProteomeXchangeService.PX_PASSWORD);
            }
            if(StringUtils.isBlank(pxUser))
            {
                errors.reject(ERROR_MSG, "Cannot find ProteomeXchange username.");
            }
            if(StringUtils.isBlank(pxPassword))
            {
                errors.reject(ERROR_MSG, "Cannot find ProteomeXchange password.");
            }
            if(StringUtils.isBlank(form.getMethod()))
            {
                errors.reject(ERROR_MSG, "Did not find a ProteomeXchange method in request.");
            }
            if(errors.getErrorCount() > 0)
            {
                return false;
            }

            PX_METHOD method = PX_METHOD.valueOf(form.getMethod());
            switch (method)
            {
                case GET_ID:
                    assignPxId(form.isTestDatabase(), form.isTestMode(), pxUser, pxPassword, errors);
                    break;
                case VALIDATE:
                    validatePxXml(form.isTestDatabase(), form.getChangeLog(), pxUser, pxPassword, errors);
                    break;
                case SUBMIT:
                    submitPxXml(form.isTestDatabase(), form.isTestMode(), pxUser, pxPassword, errors);
                    break;
                case UPDATE:
                    updatePxXml(form.isTestDatabase(), form.isTestMode(), form.getChangeLog(), pxUser, pxPassword, errors);
                    break;
                default: return false;
            }

            return errors.getErrorCount() == 0;
        }

        @Override
        public URLHelper getSuccessURL(PxActionsForm form)
        {
            return null;
        }

        @Override
        public ModelAndView getSuccessView(PxActionsForm form)
        {
            return new HtmlView(
                    DIV("Sent request to " + form.getMethod(), ".  Request was successful.", BR(),
                        SPAN("Response from PX server: "), BR(),
                        DIV(at(style, "white-space: pre-wrap;margin:10px 0px 10px 0px;"),
                            _pxResponse),
                    DIV(new Link.LinkBuilder("Back to folder").href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_expAnnot.getContainer())))));
        }

        private PxXml createPxXml(ExperimentAnnotations expAnnot, JournalExperiment je, String pxChanageLog) throws PxException
        {
            // Generate the PX XML
            int pxVersion = PxXmlManager.getNextVersion(_journalExperiment.getId());
            PxXmlWriter annWriter;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, je);
                pxInfo.setPxChangeLog(pxChanageLog);
                pxInfo.setVersion(pxVersion);
                annWriter = new PxXmlWriter(baos);
                annWriter.write(pxInfo);

                String xml = baos.toString(StandardCharsets.UTF_8);
                return new PxXml(je.getId(), xml, pxVersion, pxChanageLog);
            }
            catch (IOException e)
            {
                throw new PxException("Error creating PX XML.", e);
            }
        }

        private File writePxXmlFile(String xmlString) throws PxException
        {
            File xml = getLocalFile(getContainer(), "px.xml");

            try (PrintWriter out = new PrintWriter(new FileWriter(xml, StandardCharsets.UTF_8)))
            {
                out.write(xmlString);
            }
            catch (IOException e)
            {
                throw new PxException("Error writing PX XML.", e);
            }
            return xml;
        }

        public void assignPxId(boolean useTestDb, boolean testMode, String pxUser, String pxPassword, BindException errors) throws ProteomeXchangeServiceException
        {
            if(!StringUtils.isBlank(_expAnnot.getPxid()))
            {
                errors.reject(ERROR_MSG, "A PX ID is already assigned to the experiment.");
                return;
            }

            _pxResponse = ProteomeXchangeService.getPxIdResponse(useTestDb, pxUser, pxPassword);
            String pxid = ProteomeXchangeService.parsePxIdFromResponse(_pxResponse);
            if(pxid != null)
            {
                // Save the PX ID with the experiment.
                _expAnnot.setPxid(pxid);
                ExperimentAnnotationsManager.updatePxId(_expAnnot, pxid);
            }
            else
            {
                errors.reject(ERROR_MSG, "Could not parse PXD accession from the response.");
            }
        }

        private void validatePxXml(boolean useTestDb, String pxChangeLog, String pxUser, String pxPassword, BindException errors) throws PxException, ProteomeXchangeServiceException
        {
            File xmlFile = writePxXmlFile(createPxXml(_expAnnot, _journalExperiment, pxChangeLog).getXml());
            _pxResponse = ProteomeXchangeService.validatePxXml(xmlFile, useTestDb, pxUser, pxPassword);
            if(ProteomeXchangeService.responseHasErrors(_pxResponse))
            {
                errors.reject(ERROR_MSG, "PX XML is could not be validated. See response for details.");
            }
        }

        private void submitPxXml(boolean useTestDb, boolean testMode, String pxUser, String pxPassword, BindException errors) throws ProteomeXchangeServiceException, PxException
        {
            submitPxXml(useTestDb, testMode,null, pxUser, pxPassword, errors);
        }

        private void submitPxXml(boolean useTestDb, boolean testMode, String pxChangeLog, String pxUser, String pxPassword, BindException errors) throws ProteomeXchangeServiceException, PxException
        {
            PxXml pxXml = createPxXml(_expAnnot, _journalExperiment, pxChangeLog);
            File xmlFile = writePxXmlFile(pxXml.getXml());
            _pxResponse = ProteomeXchangeService.submitPxXml(xmlFile, useTestDb, pxUser, pxPassword);
            if(ProteomeXchangeService.responseHasErrors(_pxResponse))
            {
                errors.reject(ERROR_MSG, "PX XML could not be submitted. See response for details.");
            }
            else
            {
                PxXmlManager.save(pxXml, getUser());
            }
        }

        private void updatePxXml(boolean useTestDb, boolean testMode, String pxChangeLog, String pxUser, String pxPassword, BindException errors) throws PxException, ProteomeXchangeServiceException
        {
            if(StringUtils.isBlank(pxChangeLog))
            {
                errors.reject(ERROR_MSG, "Change log cannot be empty.");
                return;
            }
            submitPxXml(useTestDb, testMode, pxChangeLog, pxUser, pxPassword, errors);
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
    }

    public static class PxExperimentAnnotations
    {
        private final ExperimentAnnotations _experimentAnnotations;
        private final JournalExperiment _journalExperiment;
        private String _pxChangeLog;
        private int _version;
        private boolean _useTestDb;

        public PxExperimentAnnotations(ExperimentAnnotations experimentAnnotations, JournalExperiment je)
        {
            _experimentAnnotations = experimentAnnotations;
            _journalExperiment = je;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public JournalExperiment getJournalExperiment()
        {
            return _journalExperiment;
        }

        public String getPxChangeLog()
        {
            return _pxChangeLog;
        }

        public void setPxChangeLog(String pxChangeLog)
        {
            _pxChangeLog = pxChangeLog;
        }

        public int getVersion()
        {
            return _version;
        }

        public void setVersion(int version)
        {
            _version = version;
        }

        public boolean isUseTestDb()
        {
            return _useTestDb;
        }

        public void setUseTestDb(boolean useTestDb)
        {
            _useTestDb = useTestDb;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ExportPxXmlAction extends SimpleStreamAction<PxActionsForm>
    {
        @Override
        public void render(PxActionsForm form, BindException errors, PrintWriter out) throws Exception
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

            JournalExperiment je = expAnnot.isJournalCopy() ? JournalManager.getRowForJournalCopy(expAnnot) : JournalManager.getLastPublishedRecord(experimentId);
            if(je == null)
            {
                out.write("Cannot find a row in JournalExperiment for experiment ID " + experimentId);
            }

            ensureCorrectContainer(getContainer(), expAnnot.getContainer(), getViewContext());

            PxXmlWriter annWriter = new PxXmlWriter(out);
            PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, je);
            pxInfo.setVersion(PxXmlManager.getNextVersion(je.getId()));
            annWriter.write(pxInfo);
        }
    }

    public static class PxActionsForm extends ExperimentIdForm
    {
        private boolean _testDatabase;
        private boolean _testMode;
        private String _changeLog;
        private String _method;

        public boolean isTestDatabase()
        {
            return _testDatabase;
        }

        public void setTestDatabase(boolean testDatabase)
        {
            _testDatabase = testDatabase;
        }

        public boolean isTestMode()
        {
            return _testMode;
        }

        public void setTestMode(boolean testMode)
        {
            _testMode = testMode;
        }

        public String getChangeLog()
        {
            return _changeLog;
        }

        public void setChangeLog(String changeLog)
        {
            _changeLog = changeLog;
        }

        public String getMethod()
        {
            return _method;
        }

        public void setMethod(String method)
        {
            _method = method;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class PxXmlSummaryAction extends SimpleViewAction<PxActionsForm>
    {
        @Override
        public ModelAndView getView(PxActionsForm form, BindException errors) throws Exception
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

            ensureCorrectContainer(getContainer(), expAnnot.getContainer(), getViewContext());

            JournalExperiment je = expAnnot.isJournalCopy() ? JournalManager.getRowForJournalCopy(expAnnot) : JournalManager.getLastPublishedRecord(experimentId);
            if(je == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a row in JournalExperiment for experiment ID: " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            StringBuilder summaryHtml = new StringBuilder();
            PxHtmlWriter writer = new PxHtmlWriter(summaryHtml);
            PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, je);
            pxInfo.setUseTestDb(form.isTestDatabase());
            pxInfo.setPxChangeLog(form.getChangeLog());
            pxInfo.setVersion(PxXmlManager.getNextVersion(je.getId()));
            writer.write(pxInfo);

            return new HtmlView(summaryHtml.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Summary of ProteomeXchange Information");
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class UpdatePxDetailsAction extends FormViewAction<PxDetailsForm>
    {
        private ExperimentAnnotations _experimentAnnotations;
        private JournalExperiment _journalExperiment;

        @Override
        public void validateCommand(PxDetailsForm form, Errors errors)
        {
            _experimentAnnotations = form.lookupExperiment();
            if(_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + form.getId());
                return;
            }

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            if(!_experimentAnnotations.isJournalCopy())
            {
                errors.reject(ERROR_MSG, "Experiment is not a jounal copy. Cannot update PX ID and other details.");
                return;
            }

            _journalExperiment = JournalManager.getRowForJournalCopy(_experimentAnnotations);
            if(_journalExperiment == null)
            {
                errors.reject(ERROR_MSG, "Could not find a row in JournalExperiment for copied experiment " + _experimentAnnotations.getId());
            }
        }

        @Override
        public ModelAndView getView(PxDetailsForm form, boolean reshow, BindException errors)
        {
            if(!reshow)
            {
               validateCommand(form, errors);
               if(errors.getErrorCount() > 0)
               {
                   return new SimpleErrorView(errors);
               }
               form.setPxId(_experimentAnnotations.getPxid());
               form.setIncompletePxSubmission(_journalExperiment.isIncompletePxSubmission());
            }

            JspView view = new JspView<>("/org/labkey/panoramapublic/view/publish/updatePxDetails.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Update ProteomeXchange Details");
            return view;
        }

        @Override
        public boolean handlePost(PxDetailsForm form, BindException errors)
        {
            String pxid = form.getPxId();
            if(!StringUtils.isBlank(pxid) && !pxid.matches(ProteomeXchangeService.PXID))
            {
                errors.reject(ERROR_MSG, "Invalid ProteomeXchange ID");
                return false;
            }

            try(DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                _experimentAnnotations.setPxid(pxid);
                ExperimentAnnotationsManager.updatePxId(_experimentAnnotations, pxid);

                _journalExperiment.setIncompletePxSubmission(form.isIncompletePxSubmission());
                JournalManager.updateJournalExperiment(_journalExperiment, getUser());

                transaction.commit();
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(PxDetailsForm pxDetailsForm)
        {
            return PanoramaPublicController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), _experimentAnnotations.getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update ProteomeXchange Details");
        }
    }

    public static class PxDetailsForm extends ExperimentIdForm
    {
        private String _pxId;
        private boolean _incompletePxSubmission;

        public String getPxId()
        {
            return _pxId;
        }

        public void setPxId(String pxId)
        {
            _pxId = pxId;
        }

        public boolean isIncompletePxSubmission()
        {
            return _incompletePxSubmission;
        }

        public void setIncompletePxSubmission(boolean incompletePxSubmission)
        {
            _incompletePxSubmission = incompletePxSubmission;
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
            if(!StringUtils.isBlank(_expAnnot.getPubmedId()) && !_expAnnot.getPubmedId().matches(PUBMED_ID))
            {
                errors.reject(ERROR_MSG, "PubMed ID should be a 1 to 8 digit number.");
                return false;
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

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
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
            experimentDetailsView.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);

            // List of runs in the experiment.
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

                JournalExperiment je = JournalManager.getLastPublishedRecord(exptAnnotations.getId());
                if(je.isPxidRequested())
                {
                    ActionURL url = PanoramaPublicController.getPrePublishExperimentCheckURL(exptAnnotations.getId(), exptAnnotations.getContainer(), true);
                    url.addReturnURL(getViewExperimentDetailsURL(exptAnnotations.getId(), exptAnnotations.getContainer()));
                    journalsBox.addView(new HtmlView(DIV(new Link.LinkBuilder("Validate for ProteomeXchange").href(url).build())));
                }
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

                // Should see the "Submit" or "Resubmit" button only if
                // 1. User is an admin in the folder
                // 2. AND this is a NOT journal copy (i.e. a folder in the Panorama Public project)
                // 3. AND if this experiment has been copied to Panorama Public, the copy is not final (paper published and data public).
                _canPublish = c.hasPermission(user, AdminPermission.class) && (ExperimentAnnotationsManager.canSubmitExperiment(_experimentAnnotations));
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
        @Override
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
        @Override
        public void validateCommand(ExperimentAnnotationsForm target, Errors errors)
        {}

        @Override
        public ModelAndView getView(ExperimentAnnotationsForm form, boolean reshow, BindException errors)
        {
            UpdateView view = new UpdateView(new ExperimentAnnotationsFormDataRegion(getViewContext(), form, DataRegion.MODE_UPDATE), form, errors);
            view.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            addExperimentViewDependencies(view);
            return view;
        }

        @Override
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

            if(!StringUtils.isBlank(form.getBean().getPubmedId()) && !form.getBean().getPubmedId().matches(PUBMED_ID))
            {
                errors.reject(ERROR_MSG, "PubMed ID should be a 1 to 8 digit number");
                return false;
            }

            // User is updating the experiment metadata. If this data has already been submitted, and a PX ID was requested,
            // check that the new details entered are consistent with PX requirements.
            JournalExperiment je = JournalManager.getLastPublishedRecord(_experimentAnnotationsId);
            if(je != null && je.isPxidRequested())
            {
                List<String> missingFields = SubmissionDataValidator.getMissingExperimentMetadataFields(form.getBean());
                if(missingFields.size() > 0)
                {
                    missingFields.stream().forEach(err -> errors.reject(ERROR_MSG, err));
                    return false;
                }
            }

            form.doUpdate();
            return true;
        }

        @Override
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

        @Override
        public int[] getIds()
        {
            return PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), getDataRegionSelectionKey(), false));
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
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
        @Override
        public int[] getIds()
        {
            return new int[]{this.getBean().getId()};
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteExperimentAnnotationsAction extends ConfirmAction<DeleteExperimentAnnotationsForm>
    {
        private ExperimentAnnotations _expAnnotations;

        @Override
        public ModelAndView getConfirmView(DeleteExperimentAnnotationsForm deleteForm, BindException errors)
        {
            setTitle("Confirm Delete Experiment");
            return FormPage.getView("/org/labkey/panoramapublic/view/expannotations/deleteExperimentAnnotations.jsp", deleteForm);
        }

        @Override
        public boolean handlePost(DeleteExperimentAnnotationsForm form, BindException errors)
        {
            // Check container
            ensureCorrectContainer(getContainer(), _expAnnotations.getContainer(), getViewContext());

            return deleteExperimentAnnotations(errors, form.getIds(), getUser());
        }

        @Override
        public void validateCommand(DeleteExperimentAnnotationsForm deleteForm, Errors errors)
        {
            int _experimentAnnotationsId = deleteForm.getBean().getId();
            _expAnnotations = ExperimentAnnotationsManager.get(_experimentAnnotationsId);
            if (_expAnnotations == null)
            {
                errors.reject(ERROR_MSG, "Could not find an experiment with ID "  + _experimentAnnotationsId);
            }
            if(_expAnnotations.isJournalCopy() && _expAnnotations.isFinal())
            {
                errors.reject(ERROR_MSG, "Experiment cannot be deleted.  It is public and is associated with a publication.");
            }
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

        @Override
        public void validateCommand(ExperimentForm form, Errors errors)
        {
        }

        @Override
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

        @Override
        public ActionURL getSuccessURL(ExperimentForm form)
        {
            return getViewExperimentDetailsURL(_expAnnot.getId(), getContainer());
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ExcludeSubFoldersInExperimentAction extends FormHandlerAction<ExperimentForm>
    {
        private ExperimentAnnotations _expAnnot;

        @Override
        public void validateCommand(ExperimentForm form, Errors errors)
        {
        }

        @Override
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

        @Override
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

    public static ActionURL getPublishExperimentURL(int experimentAnnotationsId, Container container, boolean keepPrivate, boolean getPxId)
    {
        ActionURL result = new ActionURL(PublishExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getUpdateJournalExperimentURL(int experimentAnnotationsId, int journalId, Container container, boolean keepPrivate, boolean getPxId)
    {
        ActionURL result = new ActionURL(UpdateJournalExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getPrePublishExperimentCheckURL(int experimentAnnotationsId, Container container, boolean notSubmitting)
    {
        ActionURL result = new ActionURL(PreSubmissionCheckAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("notSubmitting", notSubmitting);
        return result;
    }

    public static ActionURL getRePublishExperimentURL(int experimentAnnotationsId, int journalId, Container container, boolean keepPrivate , boolean getPxId)
    {
        ActionURL result = new ActionURL(RepublishJournalExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
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
                new JournalGroupDetailsAction(),
                new DeleteJournalGroupAction(),
                new GetPxActionsAction(),
                new ExportPxXmlAction(),
                new UpdatePxDetailsAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                new JournalGroupsAdminViewAction()
            );
        }
    }
}
