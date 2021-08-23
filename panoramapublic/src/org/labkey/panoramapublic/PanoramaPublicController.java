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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
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
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.LK;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.panoramapublic.chromlib.ChromLibStateManager;
import org.labkey.panoramapublic.datacite.DataCiteException;
import org.labkey.panoramapublic.datacite.DataCiteService;
import org.labkey.panoramapublic.datacite.Doi;
import org.labkey.panoramapublic.datacite.DoiMetadata;
import org.labkey.panoramapublic.model.DataLicense;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.model.JournalExperiment;
import org.labkey.panoramapublic.model.JournalSubmission;
import org.labkey.panoramapublic.model.PxXml;
import org.labkey.panoramapublic.model.Submission;
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
import org.labkey.panoramapublic.query.SubmissionManager;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.labkey.api.targetedms.TargetedMSService.FolderType.*;
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
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.LINK;
import static org.labkey.api.util.DOM.LK.CHECKBOX;
import static org.labkey.api.util.DOM.LK.ERRORS;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TBODY;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TH;
import static org.labkey.api.util.DOM.THEAD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.UL;
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

    private static final Logger LOG = LogManager.getLogger(PanoramaPublicController.class);

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

            view.addView(qView);
            view.addView(getPXCredentialsLink());
            view.addView(getDataCiteCredentialsLink());
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

        private ModelAndView getDataCiteCredentialsLink()
        {
            ActionURL url = new ActionURL(ManageDataCiteCredentials.class, getContainer());
            return new HtmlView(DIV(at(style, "margin-top:20px;"),
                    new Link.LinkBuilder("Set DataCite Credentials").href(url).build()));
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
                moduleProperty.saveValue(getUser(), container, Experiment.toString());
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
        public void addNavTrail(NavTree root)
        {
            if(_journal != null)
            {
                root.addChild("Journal group details", getJournalGroupDetailsUrl(_journal.getId(), getContainer()));
            }
            root.addChild("Journal group support container");
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
    public class ManageDataCiteCredentials extends FormViewAction<DataCiteCredentialsForm>
    {
        @Override
        public void validateCommand(DataCiteCredentialsForm form, Errors errors)
        {
            String user = form.getUser();
            String password = form.getPassword();
            String doiPrefix = form.getDoiPrefix();

            if (StringUtils.isBlank(user))
            {
                errors.reject(ERROR_MSG, "User name cannot be blank");
            }
            if (StringUtils.isBlank(password))
            {
                errors.reject(ERROR_MSG, "Password cannot be blank");
            }
            if (StringUtils.isBlank(doiPrefix))
            {
                errors.reject(ERROR_MSG, "DOI prefix cannot be blank");
            }

            String testUser = form.getTestUser();
            String testPassword = form.getTestPassword();
            String testDoiPrefix = form.getTestDoiPrefix();
            if (StringUtils.isBlank(testUser))
            {
                errors.reject(ERROR_MSG, "Test user name cannot be blank");
            }
            if (StringUtils.isBlank(testPassword))
            {
                errors.reject(ERROR_MSG, "Test password cannot be blank");
            }
            if (StringUtils.isBlank(testDoiPrefix))
            {
                errors.reject(ERROR_MSG, "Test DOI prefix cannot be blank");
            }
        }

        @Override
        public boolean handlePost(DataCiteCredentialsForm form, BindException errors)
        {
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DataCiteService.CREDENTIALS, true);
            map.put(DataCiteService.USER, form.getUser());
            map.put(DataCiteService.PASSWORD, form.getPassword());
            map.put(DataCiteService.PREFIX, form.getDoiPrefix());
            map.put(DataCiteService.TEST_USER, form.getTestUser());
            map.put(DataCiteService.TEST_PASSWORD, form.getTestPassword());
            map.put(DataCiteService.TEST_PREFIX, form.getTestDoiPrefix());
            map.save();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(DataCiteCredentialsForm form)
        {
            return null;
        }

        @Override
        public ModelAndView getSuccessView(DataCiteCredentialsForm form)
        {
            ActionURL adminUrl = new ActionURL(JournalGroupsAdminViewAction.class, getContainer());
            return new HtmlView(
                    DIV("DataCite credentials saved!",
                    BR(),
                    new Link.LinkBuilder("Back to Panorama Public Admin Console").href(adminUrl).build()));
        }

        @Override
        public ModelAndView getView(DataCiteCredentialsForm form, boolean reshow, BindException errors)
        {
            if(!reshow)
            {
                PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(DataCiteService.CREDENTIALS, false);
                if(map != null)
                {
                    // Force the user to re-enter the passwords; do not set them in the form

                    form.setUser(map.get(DataCiteService.USER));
                    form.setDoiPrefix(map.get(DataCiteService.PREFIX));

                    form.setTestUser(map.get(DataCiteService.TEST_USER));
                    form.setTestDoiPrefix(map.get(DataCiteService.TEST_PREFIX));
                }
            }
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/manageDataCiteCredentials.jsp", form, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("DataCite Credentials");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Set DataCite Credentials");
        }
    }

    public static class DataCiteCredentialsForm
    {
        private String _user;
        private String _password;
        private String _doiPrefix;
        private String _testUser;
        private String _testPassword;
        private String _testDoiPrefix;

        public String getTestUser()
        {
            return _testUser;
        }

        public void setTestUser(String testUser)
        {
            _testUser = testUser;
        }

        public String getTestPassword()
        {
            return _testPassword;
        }

        public void setTestPassword(String testPassword)
        {
            _testPassword = testPassword;
        }

        public String getUser()
        {
            return _user;
        }

        public void setUser(String user)
        {
            _user = user;
        }

        public String getPassword()
        {
            return _password;
        }

        public void setPassword(String password)
        {
            _password = password;
        }

        public String getTestDoiPrefix()
        {
            return _testDoiPrefix;
        }

        public void setTestDoiPrefix(String testDoiPrefix)
        {
            _testDoiPrefix = testDoiPrefix;
        }

        public String getDoiPrefix()
        {
            return _doiPrefix;
        }

        public void setDoiPrefix(String doiPrefix)
        {
            _doiPrefix = doiPrefix;
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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Set ProteomeXchange Credentials");
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
        private JournalSubmission _journalSubmission;

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
                CopyExperimentForm.setDefaults(form, _experiment, _journalSubmission);
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
            _journalSubmission = SubmissionManager.getJournalSubmission(_experiment.getId(), _journal.getId());
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a submission for experimentId " + _experiment.getId()
                + " and journalId " + _journal.getId());
                return false;
            }

            if(_journalSubmission.getPendingSubmission() == null)
            {
                errors.reject(ERROR_MSG,"Could not find a submission request for experimentId " + _experiment.getId()
                        + " and journalId " + _journal.getId());
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

            Submission submission = _journalSubmission.getPendingSubmission();
            // Validate the data if a ProteomeXchange ID was requested.
            if(submission.isPxidRequested())
            {
                SubmissionDataStatus status = SubmissionDataValidator.validateExperiment(_experiment);
                if(submission.isIncompletePxSubmission() && !status.canSubmitToPx())
                {
                    errors.reject(ERROR_MSG, "A ProteomeXchange ID was requested for an \"incomplete\" submission.  But the data is not valid for a ProteomeXchange submission");
                    return false;
                }
                if(!submission.isIncompletePxSubmission() && !status.isComplete())
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
                errors.reject(ERROR_MSG, "Invalid destination folder name " + destinationFolder + ". " + errMessages);
                return false;
            }

            Submission previousSubmission = _journalSubmission.getLastCopiedSubmission();
            if(previousSubmission != null)
            {
                // We expect the previous copy of this data to have the same folder name;  Rename that folder first.
                try
                {
                    renamePreviousCopy(previousSubmission, destinationFolder, _journalSubmission);
                }
                catch (ValidationException validationErrors)
                {
                    errors.reject(ERROR_MSG, "Could not successfully rename previous copy of the data. Error was: "
                            + errors.getMessage());
                    return false;
                }
            }
            if(ContainerManager.getForPath(parentContainer.getParsedPath().append(destinationFolder)) != null)
            {
                errors.reject(ERROR_MSG, "Destination folder " + destinationFolder + " already exists. Please enter another folder name."
                        + errMessages);
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
                job.setAssignDoi(form.isAssignDoi());
                job.setUseDataCiteTestApi(form.isUseDataCiteTestApi());
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

        private void renamePreviousCopy(Submission previousSubmission, String targetContainerName, JournalSubmission jSubmission) throws ValidationException
        {
            ExperimentAnnotations previousCopy = ExperimentAnnotationsManager.get(previousSubmission.getCopiedExperimentId());
            if(previousCopy != null)
            {
                Container prevContainer = previousCopy.getContainer();
                try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                {
                    int version = jSubmission.getNextVersion();

//                    previousSubmission.setVersion(version);
//                    previousSubmission.setShortCopyUrl(null);

                    if(targetContainerName.equals(prevContainer.getName()))
                    {
                        // Rename the container where the old copy lives so that the same folder name can be used for the new copy.
                        ContainerManager.rename(prevContainer, getUser(), prevContainer.getName() + " V." + version);
//                        previousCopy = ExperimentAnnotationsManager.get(previousSubmission.getCopiedExperimentId()); // query again to get the updated container name
//                        // TODO: Do we really need to update the short access URL?
//                        JournalManager.updateAccessUrl(previousCopy, previousSubmission, getUser());
                    }
//                    else
//                    {
//                        JournalManager.updateJournalExperiment(previousSubmission, getUser());
//                    }
                    transaction.commit();
                }
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
        private boolean _assignDoi;
        private boolean _useDataCiteTestApi;
        private boolean _sendEmail;
        private String _toEmailAddresses;
        private String _replyToAddress;
        private boolean _deleteOldCopy;

        static void setDefaults(CopyExperimentForm form, ExperimentAnnotations sourceExperiment, JournalSubmission js)
        {
            Submission currentSubmission = js.getPendingSubmission();
            if(currentSubmission.isKeepPrivate())
            {
                form.setReviewerEmailPrefix(PANORAMA_REVIEWER_PREFIX);
            }

            form.setAssignPxId(currentSubmission.isPxidRequested());
            form.setUsePxTestDb(false);

            form.setAssignDoi(true);
            form.setUseDataCiteTestApi(false);

            form.setSendEmail(true);
            Set<String> toEmailAddresses = new HashSet<>();
            User submitter = UserManager.getUser(currentSubmission.getCreatedBy()); // User that clicked the submit button
            toEmailAddresses.add(submitter.getEmail());

            User dataSubmitter = sourceExperiment.getSubmitterUser(); // User selected as the data submitter.
                                                                      // May be different from the user that clicked the button.
                                                                      // This user's name will be included in the PX announcement.
            if(dataSubmitter != null)
            {
                toEmailAddresses.add(dataSubmitter.getEmail());
            }
            User labHead = sourceExperiment.getLabHeadUser();
            if(labHead != null)
            {
                toEmailAddresses.add(labHead.getEmail());
            }
            else if(!StringUtils.isBlank(currentSubmission.getLabHeadEmail()))
            {
                // Email address of the lab head was entered in the data submission form
                toEmailAddresses.add(currentSubmission.getLabHeadEmail());
            }
            form.setToEmailAddresses(StringUtils.join(toEmailAddresses, '\n'));

            Container sourceExptContainer = sourceExperiment.getContainer();
            Container project = sourceExptContainer.getProject();
            // Project names on PanoramaWeb can be like: U. of Washington - MacCoss Lab
            // On Panorama Public we want to create a folder: U. of Washington MacCoss Lab - <name of folder being copied>
            // Remove hyphen and any extra spaces from project name .
            String projectName = StringUtils.normalizeSpace(project.getName().replaceAll("-", ""));
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

        public boolean isAssignDoi()
        {
            return _assignDoi;
        }

        public void setAssignDoi(boolean assignDoi)
        {
            _assignDoi = assignDoi;
        }

        public boolean isUseDataCiteTestApi()
        {
            return _useDataCiteTestApi;
        }

        public void setUseDataCiteTestApi(boolean useDataCiteTestApi)
        {
            _useDataCiteTestApi = useDataCiteTestApi;
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
                if(form.doSubfolderCheck())
                {
                    List<Container> allSubfolders = getAllSubfolders(_experimentAnnotations.getContainer());
                    List<Container> hiddenFolders = getHiddenFolders(allSubfolders, getUser());
                    if(!_experimentAnnotations.isIncludeSubfolders() && allSubfolders.size() > 0)
                    {
                        // Experiment is not configured to include subfolders but there are subfolders.
                        ActionURL skipSubfolderCheckUrl = getViewContext().getActionURL().clone();
                        skipSubfolderCheckUrl.addParameter("doSubfolderCheck", "false");
                        if(hiddenFolders.size() == 0)
                        {
                            // Return a view to make the user confirm their intention to include / exclude subfolders from the experiment.
                            return getConfirmIncludeSubfoldersView(_experimentAnnotations, allSubfolders, skipSubfolderCheckUrl);
                        }
                        else
                        {
                            // There are subfolders where the user does not have read permissions. Subfolders can only be included if the
                            // user has read permissions in all of them. Return a view that lets them with go back to the folder home page
                            // or continue with the submission without including subfolders.
                            return getNoPermsInSubfoldersView(_experimentAnnotations, hiddenFolders, skipSubfolderCheckUrl);
                        }
                    }
                    else if(_experimentAnnotations.isIncludeSubfolders() && hiddenFolders.size() > 0)
                    {
                        // Experiment is already configured to include subfolders but there are subfolders where this user does
                        // not have read permissions.
                        return getExperimentIncludesHiddenFoldersView(_experimentAnnotations, hiddenFolders);
                    }
                }

                populateForm(form, _experimentAnnotations);
            }

            if (!form.isDataValidated())
            {
                // Cannot publish if this is not an "Experimental data"  or Library folder.
                TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(_experimentAnnotations.getContainer());
                if (!isSupportedFolderType(folderType))
                {
                    errors.reject(ERROR_MSG, "Targeted MS folders of type \"" + folderType + "\" cannot be submitted.");
                    return new SimpleErrorView(errors);
                }
                
                // Do not allow submitting a library folder with conflicts.  This check is also done on any library sub-folders
                // if the experiment is configured to include subfolders.
                Container libraryFolderWithConflicts = getLibraryFolderWithConflicts(getUser(), _experimentAnnotations);
                if(libraryFolderWithConflicts != null)
                {
                    errors.reject(ERROR_MSG, String.format("The chromatogram library folder '%s' has conflicts. Please resolve the conflicts before submitting.",
                            libraryFolderWithConflicts.getName()));
                    return new SimpleErrorView(errors);
                }

                // Ensure there is at least one Skyline document in submission.
                if (!hasSkylineDocs(_experimentAnnotations))
                {
                    errors.reject(ERROR_MSG, "There are no Skyline documents included in this experiment.  " +
                            "Please upload one or more Skyline documents to proceed with the submission request.");
                    return new SimpleErrorView(errors);
                }

                // Require that the users selected as the submitter and lab head have first and last names in their account details
                if(userInfoIncomplete(_experimentAnnotations, errors))
                {
                    return getAccountInfoIncompleteView(errors);
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

        private Container getLibraryFolderWithConflicts(User user, ExperimentAnnotations exptAnnotations)
        {
            List<Container> containers = exptAnnotations.isIncludeSubfolders() ?
                    ContainerManager.getAllChildren(exptAnnotations.getContainer(), user)
                    : Collections.singletonList(exptAnnotations.getContainer());

            for(Container container: containers)
            {
                TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(container);
                if(isLibraryFolder(folderType) && ChromLibStateManager.getConflictCount(user, container, folderType) > 0)
                {
                    return container;
                }
            }
            return null;
        }

        private HtmlView getConfirmIncludeSubfoldersView(ExperimentAnnotations expAnnotations, List<Container> allSubfolders, ActionURL skipSubfolderCheckUrl)
        {
            ActionURL includeSubfoldersUrl = new ActionURL(IncludeSubFoldersInExperimentAction.class, _experimentAnnotations.getContainer())
                    .addParameter("id", expAnnotations.getId())
                    .addReturnURL(skipSubfolderCheckUrl);
            HtmlView confirmView = new HtmlView(DIV("Would you like to include data from the following subfolders in the experiment?",
                    getSubfolderListHtml(expAnnotations.getContainer(), allSubfolders),
                    new Button.ButtonBuilder("Include Subfolders and Continue").href(includeSubfoldersUrl).usePost().build(),
                    HtmlString.NBSP,
                    getSkipSubfoldersAndContinueButton(skipSubfolderCheckUrl)));

            confirmView.setTitle("Confirm Include Subfolders");
            confirmView.setFrame(WebPartView.FrameType.PORTAL);
            return confirmView;
        }

        private HtmlView getNoPermsInSubfoldersView(ExperimentAnnotations expAnnotations, List<Container> hiddenSubfolders,  ActionURL skipSubfolderCheckUrl)
        {
            return new HtmlView(DIV("This folder has the following subfolders in which you do not have read permissions: ",
                            getSubfolderListHtml(expAnnotations.getContainer(), hiddenSubfolders),
                            "If you want to include data from subfolders in this experiment " +
                                    "please contact the folder or project administrator to give you the required permissions. " +
                                    "Otherwise, click the \"Skip Subfolders And Continue\" button below.",
                            BR(),
                            DIV(at(style, "margin-top:10px;"),
                                getBackToFolderButton(expAnnotations.getContainer()),
                                HtmlString.NBSP,
                                getSkipSubfoldersAndContinueButton(skipSubfolderCheckUrl))));
        }

        private ModelAndView getExperimentIncludesHiddenFoldersView(ExperimentAnnotations expAnnotations, List<Container> hiddenSubfolders)
        {
            return new HtmlView(DIV("This experiment is configured to include subfolders but you do not have read permissions in the following folders:",
                    getSubfolderListHtml(expAnnotations.getContainer(), hiddenSubfolders),
                    "Read permissions are required in all subfolders to include data from subfolders in a submission request. " +
                            "Please contact the folder or project administrator to give you the required permissions " +
                            " or exclude subfolders from the experiment before submitting.",
                    BR(),
                    DIV(at(style, "margin-top:10px;"), getBackToFolderButton(expAnnotations.getContainer()),
                    HtmlString.NBSP,
                    getExcludeSubfoldersButton(expAnnotations).build())));
        }

        @NotNull
        private Button getBackToFolderButton(Container container)
        {
            return new Button.ButtonBuilder("Back to Folder").href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container)).build();
        }

        @NotNull
        private Button getSkipSubfoldersAndContinueButton(ActionURL skipSubfolderCheckUrl)
        {
            return new Button.ButtonBuilder("Skip Subfolders and Continue").href(skipSubfolderCheckUrl).build();
        }

        private HtmlView getAccountInfoIncompleteView(BindException errors)
        {
            return new HtmlView("Incomplete Account Information",
                    DIV(ERRORS(errors),
                            DIV(at(style, "margin-top:10px; margin-bottom:10px;"),
                            "Users can update their account information by clicking the user icon (",
                            SPAN(at(style, "padding:5px;"), LK.FA("user")),
                            ") in the top right corner of the page and selecting \"My Account\" from the menu."),
                            BR(),
                            PageFlowUtil.generateBackButton()));
        }

        private boolean userInfoIncomplete(ExperimentAnnotations experimentAnnotations, BindException errors)
        {
            checkAccountInfo(experimentAnnotations.getSubmitterUser(), "data submitter", errors);
            checkAccountInfo(experimentAnnotations.getLabHeadUser(), "lab head", errors);
            return errors.getErrorCount() > 0;
        }

        private void checkAccountInfo(User user, String userType, BindException errors)
        {
            if(user != null)
            {
                boolean noFirst = StringUtils.isBlank(user.getFirstName());
                boolean noLast = StringUtils.isBlank(user.getLastName());
                if (noFirst || noLast)
                {
                    String message = (noFirst && noLast) ? "First and last names " : (noFirst ? "First name " : "Last name ");
                    message += "missing for " + userType + ": " + user.getEmail();
                    errors.reject(ERROR_MSG, message);
                }
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
                    JournalSubmission js = JournalManager.setupJournalAccess(new PanoramaPublicRequest(_experimentAnnotations, _journal, form), getUser());

                    // Create notifications
                    PanoramaPublicNotification.notifyCreated(_experimentAnnotations, _journal, js, getUser());

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

    @NotNull
    private static List<Container> getAllSubfolders(Container parent)
    {
        return ContainerManager.getAllChildren(parent).stream().filter(child -> !child.equals(parent)).collect(Collectors.toList());
    }
    
    // Returns a list of folders from the given folders where the user does not have read permissions
    @NotNull
    private static List<Container> getHiddenFolders(List<Container> folders, User user)
    {
        return folders.stream().filter(c -> !c.hasPermission(user, ReadPermission.class)).collect(Collectors.toList());
    }

    private static DOM.Renderable getSubfolderListHtml(Container parent, List<Container> children)
    {
        return UL(children.stream()
                .filter(child -> !child.equals(parent))
                .map(child -> DOM.LI(new Link.LinkBuilder(parent.getParsedPath().relativize(child.getParsedPath()).toString())
                        .href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(child))
                        .clearClasses())));
    }

    private static Button.ButtonBuilder getExcludeSubfoldersButton(ExperimentAnnotations exptAnnotations)
    {
        return new Button.ButtonBuilder("Exclude Subfolders")
                .href(new ActionURL(ExcludeSubFoldersInExperimentAction.class, exptAnnotations.getContainer()).addParameter("id", exptAnnotations.getId()))
                .usePost();
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
        private Submission _submission;

        @Override
        protected Submission getSubmission()
        {
            return _submission;
        }

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            if(super.validateGetRequest(form, errors))
            {
                if(!getPendingSubmission(_experimentAnnotations.getId(), errors))
                {
                    return false;
                }
            }
            return true;
        }

        private boolean getPendingSubmission(Integer expAnnotationsId, Errors errors)
        {
            _submission = _journalSubmission.getPendingSubmission();
            if(_submission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a current submission request for experiment ID " + expAnnotationsId);
                return false;

            }
            return true;
        }

        @Override
        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            super.populateForm(form, exptAnnotations);
            form.setUpdate(true);
        }

        @Override
        void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalSubmission = SubmissionManager.getJournalSubmission(_experimentAnnotations.getId(), _journal.getId());
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a submission request for experiment ID " + _experimentAnnotations.getId());
                return;
            }

            // Get the latest submission request that hasn't yet been copied.
            if(!getPendingSubmission(_experimentAnnotations.getId(), errors))
            {
                return;
            }

            if(_journalSubmission.getCopiedSubmissions().size() > 0)
            {
                // The user should not be able to change the short access URL if a copy of this data already exists
                // on Panorama Public.  The corresponding form field should not have been editable.
                if(!form.getShortAccessUrl().equals(_journalSubmission.getShortAccessUrl().getShortURL()))
                {
                    errors.reject(ERROR_MSG,"This data was previously copied to " + _journal.getName() + ". The short access URL cannot be changed.");
                    return;
                }
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
            getValuesFromForm(form);

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                if(!_journalSubmission.getShortAccessUrl().getShortURL().equals(form.getShortAccessUrl())) // TODO: equals
                {
                    // Change the short copy URL to match the access URL.
                    assignShortCopyUrl(form);
                    JournalManager.updateJournalExperimentUrls(_experimentAnnotations, _journal, _journalSubmission.getJournalExperiment(),
                            _submission, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                }

                _submission.setShortAccessUrl(_journalSubmission.getShortAccessUrl());
                SubmissionManager.updateSubmission(_submission, getUser());

                // Create notifications
                _journalSubmission = SubmissionManager.getJournalSubmission(_journalSubmission.getJournalExperimentId()); // Query updated values
                PanoramaPublicNotification.notifyUpdated(_experimentAnnotations, _journal, _journalSubmission, getUser());

                transaction.commit();
            }
            return true;
        }

        void getValuesFromForm(PublishExperimentForm form)
        {
            _journalSubmission.getJournalExperiment().setJournalId(form.getJournalId());
            _submission.setKeepPrivate(form.isKeepPrivate());
            _submission.setPxidRequested(form.isGetPxid());
            _submission.setIncompletePxSubmission(form.isIncompletePxSubmission());
            _submission.setDataLicense(DataLicense.resolveLicense(form.getDataLicense()));
            _submission.setLabHeadName(form.getLabHeadName());
            _submission.setLabHeadEmail(form.getLabHeadEmail());
            _submission.setLabHeadAffiliation(form.getLabHeadAffiliation());
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
        protected JournalSubmission _journalSubmission;

        protected abstract Submission getSubmission();

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            if(super.validateGetRequest(form, errors))
            {
                _journal = JournalManager.getJournal(form.getJournalId());
                if(_journal == null)
                {
                    errors.reject(ERROR_MSG,"Could not find a journal for Id " + form.getJournalId());
                    return false;
                }
                _journalSubmission = SubmissionManager.getJournalSubmission(_experimentAnnotations.getId(), _journal.getId());
                if(_journalSubmission == null)
                {
                    errors.reject(ERROR_MSG,"Could not find a submission for experiment ID " + _experimentAnnotations.getId()
                            + " to the journal " + _journal.getName());
                    return false;
                }
            }
            return true;
        }

        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            form.setShortAccessUrl(_journalSubmission.getShortAccessUrl().getShortURL());
            form.setJournalId(_journalSubmission.getJournalId());
            Submission submission = getSubmission();
            form.setKeepPrivate(submission.isKeepPrivate());
            form.setLabHeadName(submission.getLabHeadName());
            form.setLabHeadEmail(submission.getLabHeadEmail());
            form.setLabHeadAffiliation(submission.getLabHeadAffiliation());
            DataLicense license = submission.getDataLicense();
            form.setDataLicense(license == null ? DataLicense.defaultLicense().name() : license.name());
        }

        @Override
        void validateJournal(Errors errors, ExperimentAnnotations experiment, Journal journal)
        {
            Journal oldJournal = JournalManager.getJournal(_journalSubmission.getJournalId());
            if(oldJournal != null && (!oldJournal.getId().equals(journal.getId())))
            {
                super.validateJournal(errors, experiment, journal);
            }
        }

        @Override
        void validateShortAccessUrl(PublishExperimentForm form, Errors errors)
        {
            ShortURLRecord accessUrlRecord = _journalSubmission.getShortAccessUrl();
            if(!accessUrlRecord.getShortURL().equals(form.getShortAccessUrl()))
            {
                super.validateShortAccessUrl(form, errors);
            }
        }
    }
    // ------------------------------------------------------------------------
    // END Action for updating an entry in panoramapublic.JournalExperiment table
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for deleting an entry in panoramapublic.JournalExperiment table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteSubmissionAction extends ConfirmAction<IdForm>
    {
        protected ExperimentAnnotations _experimentAnnotations;
        protected Journal _journal;
        private JournalSubmission _journalSubmission;
        private Submission _submission;

        @Override
        public void validateCommand(IdForm form, Errors errors)
        {
            _submission = SubmissionManager.getSubmission(form.getId());
            if (_submission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a submission for Id: " + form.getId());
                return;
            }

            if (_submission.getCopiedExperimentId() != null)
            {
                errors.reject(ERROR_MSG, "This submission request has already been copied. It cannot be deleted.");
                return;
            }

            _journalSubmission = SubmissionManager.getJournalSubmission(_submission.getJournalExperimentId());
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a row in table JournalExperiment with Id " + _submission.getJournalExperimentId());
                return;
            }
            _experimentAnnotations = ExperimentAnnotationsManager.get(_journalSubmission.getExperimentAnnotationsId());
            if(_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG,"Could not find experiment with Id " + _journalSubmission.getExperimentAnnotationsId());
                return;
            }

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            _journal = JournalManager.getJournal(_journalSubmission.getJournalId());
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find a journal with Id " + _journalSubmission.getJournalId());
                return;
            }
        }

        @Override
        public ModelAndView getConfirmView(IdForm form, BindException errors)
        {
            setTitle("Confirm Delete Submission");
            HtmlView view = new HtmlView(DIV("Are you sure you want to cancel your submission request to " + _journal.getName() + "?",
                    BR(), BR(),
                    SPAN("Experiment: " + _experimentAnnotations.getTitle())));
            view.setTitle("Cancel Submission Request to " + _journal.getName());
            return view;
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors)
        {
            try(DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
            {
                SubmissionManager.deleteSubmission(_submission, getUser());
                JournalManager.removeJournalPermissions(_experimentAnnotations, _journal, getUser());

                // Create notifications
                PanoramaPublicNotification.notifyDeleted(_experimentAnnotations, _journal, _journalSubmission, getUser());

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
        public URLHelper getSuccessURL(IdForm form)
        {
            if(_experimentAnnotations != null)
            {
                return PanoramaPublicController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), getContainer());
            }
            else
            {
                return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
            }
        }
    }

    // ------------------------------------------------------------------------
    // END Action for deleting an entry in panoramapublic.JournalExperiment table.
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for resubmitting an entry in panoramapublic.JournalExperiment table
    //       -- Give journal copy privilege again.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class RepublishJournalExperimentAction extends ResubmitExperimentAction
    {
        private Submission _lastCopiedSubmission;

        @Override
        protected Submission getSubmission()
        {
            return _lastCopiedSubmission;
        }

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            if(super.validateGetRequest(form, errors))
            {
                if(_journalSubmission.getPendingSubmission() != null)
                {
                    errors.reject(ERROR_MSG,"This experiment is already submitted and is pending copy. It cannot be resubmitted.");
                    return false;
                }

                _lastCopiedSubmission = _journalSubmission.getLastCopiedSubmission();
                if(_lastCopiedSubmission == null)
                {
                    errors.reject(ERROR_MSG,"Could not find the last copied submission for experiment ID " + _experimentAnnotations.getId());
                    return false;
                }
            }
            return true;
        }

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
            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                Submission submission = createNewSubmission(_journalSubmission.getJournalExperiment(), form);

                // Give the journal permission to copy the folder
                Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(_journal.getLabkeyGroupId());
                JournalManager.addJournalPermissions(_experimentAnnotations, journalGroup, getUser());

                // Rename the container where the old journal copy lives so that the same folder name can be used for the new copy.
                // The container may be deleted by an admin after the data has been re-copied.
//                int version = getVersion(_journalExperiment);
                ExperimentAnnotations copiedExpt = ExperimentAnnotationsManager.get(_lastCopiedSubmission.getCopiedExperimentId());
//                renameOldContainer(copiedExpt.getContainer(), version);
//                copiedExpt = ExperimentAnnotationsManager.get(_journalExperiment.getCopiedExperimentId()); // query again to get the updated container name
//
//                _journalExperiment.setVersion(version); // Set the version
//                _journalExperiment.setShortCopyUrl(null); // Remove the short copy URL
//                // Point the shortAccessUrl to the renamed container for the existing copy of the experiment in the journal's project
//                JournalManager.updateAccessUrl(copiedExpt, _journalExperiment, getUser());

                _journalSubmission = SubmissionManager.getJournalSubmission(_journalSubmission.getJournalExperimentId()); // Requery
                PanoramaPublicNotification.notifyResubmitted(_experimentAnnotations, _journal, _journalSubmission, copiedExpt, getUser());

                transaction.commit();
            }

            return true;
        }

        private Submission createNewSubmission(JournalExperiment journalExperiment, PublishExperimentForm form)
        {
            Submission submission = new Submission();
            submission.setJournalExperimentId(journalExperiment.getId());
            submission.setShortAccessUrl(journalExperiment.getShortAccessUrl());
            submission.setKeepPrivate(form.isKeepPrivate());
            submission.setPxidRequested(form.isGetPxid());
            submission.setIncompletePxSubmission(form.isIncompletePxSubmission());
            submission.setDataLicense(DataLicense.resolveLicense(form.getDataLicense()));
            submission.setLabHeadName(form.getLabHeadName());
            submission.setLabHeadEmail(form.getLabHeadEmail());
            submission.setLabHeadAffiliation(form.getLabHeadAffiliation());
            return SubmissionManager.saveSubmission(submission, getUser());
        }

        @Override
        void validateForm(PublishExperimentForm form, Errors errors)
        {
            _journalSubmission = SubmissionManager.getJournalSubmission(_experimentAnnotations.getId(), _journal.getId());
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a submission request for experiment ID " + _experimentAnnotations.getId());
                return;
            }

            if(_journalSubmission.getPendingSubmission() != null)
            {
                errors.reject(ERROR_MSG,"This experiment is already submitted and is pending copy. It cannot be resubmitted.");
                return;
            }
            _lastCopiedSubmission = _journalSubmission.getLastCopiedSubmission();
            if(_lastCopiedSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find the last copied submission for experiment with Id " + form.getId());
                return;
            }

            ExperimentAnnotations journalCopy = ExperimentAnnotationsManager.get(_lastCopiedSubmission.getCopiedExperimentId());
            if(journalCopy == null)
            {
                errors.reject(ERROR_MSG,"This experiment does not have an existing copy on " + _journal.getName() + ".  It cannot be resubmitted.");
                return;
            }
            if(journalCopy.isFinal())
            {
                Journal journal = JournalManager.getJournal(_journalSubmission.getJournalId());
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
        private boolean _doSubfolderCheck = true;

        public boolean isNotSubmitting()
        {
            return _notSubmitting;
        }

        public boolean isSubmitting()
        {
            return !_notSubmitting;
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

        public boolean doSubfolderCheck()
        {
            return _doSubfolderCheck;
        }

        public void setDoSubfolderCheck(boolean doSubfolderCheck)
        {
            _doSubfolderCheck = doSubfolderCheck;
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
        private JournalSubmission _journalSubmission;
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

            _journalSubmission = SubmissionManager.getNewestJournalSubmission(_expAnnot);
            if(_journalSubmission == null)
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

        private PxXml createPxXml(ExperimentAnnotations expAnnot, JournalSubmission js, String pxChanageLog, boolean submittingToPx) throws PxException
        {
            // Generate the PX XML
            int pxVersion = PxXmlManager.getNextVersion(_journalSubmission.getJournalExperimentId());
            PxXmlWriter annWriter;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, js);
                pxInfo.setPxChangeLog(pxChanageLog);
                pxInfo.setVersion(pxVersion);
                annWriter = new PxXmlWriter(baos, submittingToPx);
                annWriter.write(pxInfo);

                String xml = baos.toString(StandardCharsets.UTF_8);
                return new PxXml(js.getJournalExperimentId(), xml, pxVersion, pxChanageLog);
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
            File xmlFile = writePxXmlFile(createPxXml(_expAnnot, _journalSubmission, pxChangeLog, true).getXml());
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
            PxXml pxXml = createPxXml(_expAnnot, _journalSubmission, pxChangeLog, true);
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
        private final JournalSubmission _journalExperiment;
        private String _pxChangeLog;
        private int _version;

        public PxExperimentAnnotations(ExperimentAnnotations experimentAnnotations, JournalSubmission js)
        {
            _experimentAnnotations = experimentAnnotations;
            _journalExperiment = js;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public JournalSubmission getJournalExperiment()
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

            JournalSubmission js = expAnnot.isJournalCopy() ? SubmissionManager.getSubmissionForJournalCopy(expAnnot)
                    : SubmissionManager.getNewestJournalSubmission(expAnnot);
            if(js == null)
            {
                out.write("Cannot find a row in JournalExperiment for experiment ID " + experimentId);
            }

            ensureCorrectContainer(getContainer(), expAnnot.getContainer(), getViewContext());

            PxXmlWriter annWriter = new PxXmlWriter(out, false);
            PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, js);
            pxInfo.setVersion(PxXmlManager.getNextVersion(js.getJournalExperimentId()));
            pxInfo.setPxChangeLog(form.getChangeLog());
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

            JournalSubmission js = expAnnot.isJournalCopy() ? SubmissionManager.getSubmissionForJournalCopy(expAnnot)
                    : SubmissionManager.getNewestJournalSubmission(expAnnot);
            if(js == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a row in JournalExperiment for experiment ID: " + experimentId);
                return new SimpleErrorView(errors, true);
            }

            StringBuilder summaryHtml = new StringBuilder();
            PxHtmlWriter writer = new PxHtmlWriter(summaryHtml);
            PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, js);
            pxInfo.setPxChangeLog(form.getChangeLog());
            pxInfo.setVersion(PxXmlManager.getNextVersion(js.getJournalExperimentId()));
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
        private JournalSubmission _journalSubmission;
        private Submission _submission;

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
                errors.reject(ERROR_MSG, "Experiment is not a journal copy. Cannot update PX ID and other details.");
                return;
            }

            _journalSubmission = SubmissionManager.getSubmissionForJournalCopy(_experimentAnnotations);
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a row in JournalExperiment for copied experiment " + _experimentAnnotations.getId());
                return;
            }

            _submission = _journalSubmission.getSubmissionForJournalCopy(_experimentAnnotations.getId());
            if(_submission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a submission request for copied experiment " + _experimentAnnotations.getId());
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
               form.setIncompletePxSubmission(_submission.isIncompletePxSubmission());
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

                _submission.setIncompletePxSubmission(form.isIncompletePxSubmission());
                SubmissionManager.updateSubmission(_submission, getUser());

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
    // BEGIN Actions for DataCite DOI assignment
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminOperationsPermission.class)
    public static class DoiOptionsAction extends SimpleViewAction<ExperimentIdForm>
    {
        private ExperimentAnnotations _expAnnot;

        @Override
        public ModelAndView getView(ExperimentIdForm form, BindException errors)
        {
            _expAnnot = form.lookupExperiment();
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + form.getId());
                return new SimpleErrorView(errors);
            }
            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());
            if(!_expAnnot.isJournalCopy())
            {
                // DOIs can only be assigned to data on Panorama Public
                errors.reject(ERROR_MSG, "DOIs can only be assigned to data in the Panorama Public project");
                return new SimpleErrorView(errors);
            }

            DOM.Renderable updateDoiForm = DIV(at(style, "margin-top:10px;"),FORM(at(method, "GET", action, new ActionURL(UpdateDoiAction.class, getContainer())),
                    SPAN(cl("labkey-form-label"), "DOI"),
                    INPUT(at(type, "hidden", name, "id", value, _expAnnot.getId())),
                    INPUT(at(type, "Text", name, "doi", value, _expAnnot.getDoi())),
                    SPAN(at(style, "margin:5px;")),
                    new Button.ButtonBuilder("Submit").submit(true).build()));

            if(_expAnnot.hasDoi())
            {
                return new HtmlView(
                        DIV(DIV(at(style, "margin-bottom:10px;"), "DOI assigned to the data is " + _expAnnot.getDoi()),
                                DIV(
                                new Link.LinkBuilder("Publish DOI").clearClasses().addClass("btn btn-default").href(new ActionURL(PublishDoiAction.class, getContainer()).addParameter("id", _expAnnot.getId())),
                                SPAN(at(style, "margin:5px;")),
                                new Link.LinkBuilder("Delete DOI").clearClasses().addClass("btn btn-default").href(new ActionURL(DeleteDoiAction.class, getContainer()).addParameter("id", _expAnnot.getId()))),
                                updateDoiForm));

            }
            else
            {
               return new HtmlView(
                       DIV(DIV(new Link.LinkBuilder("Assign New DOI").clearClasses().addClass("btn btn-default").href(getAssignDoiUrl(_expAnnot, getContainer(), false)),
                       SPAN(at(style, "margin:5px;")),
                       new Link.LinkBuilder("Assign New Test DOI").clearClasses().addClass("btn btn-default").href(getAssignDoiUrl(_expAnnot, getContainer(), true))),
                       updateDoiForm));
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("DOI Actions");
        }
    }

    private static ActionURL getAssignDoiUrl(ExperimentAnnotations expAnnot, Container container, boolean testMode)
    {
        return new ActionURL(AssignDoiAction.class, container).addParameter("id", expAnnot.getId()).addParameter("testMode", testMode);
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static abstract class DoiAction extends ConfirmAction<DoiForm>
    {
        ExperimentAnnotations _expAnnot;
        // JournalExperiment _journalExperiment;
        DataCiteException _exception;

        @Override
        public void validateCommand(DoiForm form, Errors errors)
        {
            _expAnnot = form.lookupExperiment();
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find experiment with ID " + form.getId());
                return;
            }
            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());
            if(!_expAnnot.isJournalCopy())
            {
                errors.reject(ERROR_MSG, "DOIs actions are only allowed on experiments in the Panorama Public project");
                return;
            }

            if(actionAllowedInLastCopy())
            {
                JournalSubmission js = SubmissionManager.getSubmissionForJournalCopy(_expAnnot);
                if(js == null)
                {
                    errors.reject(ERROR_MSG, "Cannot find a submission request for copied experiement Id " + _expAnnot.getId());
                    return;
                }
                if(!js.isLastCopiedSubmission(_expAnnot.getId()))
                {
                    errors.reject(ERROR_MSG, "Experiment id " + _expAnnot.getId() + " is not the last copied submission. "
                            + getActionName(this.getClass()) + " is only allowed in the last copy of the submitted data");
                    return;
                }
            }
        }

        protected boolean actionAllowedInLastCopy()
        {
            return false;
        }

        @Override
        public boolean handlePost(DoiForm form, BindException errors)
        {
            if(errors.getErrorCount() > 0)
            {
                return false;
            }

            try
            {
                return doPost(form, errors);
            }
            catch (DataCiteException e)
            {
                _exception = e;
                LOG.error(e);
                return false;
            }
        }

        @Override
        public @NotNull URLHelper getSuccessURL(DoiForm form)
        {
            return null;
        }

        @Override
        public ModelAndView getSuccessView(DoiForm form)
        {
            return new HtmlView(
                    DIV(getSuccessMessage(),
                            BR(),
                            DIV(new Link.LinkBuilder("Back to folder").href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_expAnnot.getContainer())))));
        }

        protected abstract DOM.Renderable getSuccessMessage();
        protected abstract boolean doPost(DoiForm form, BindException errors) throws DataCiteException;

        public ModelAndView getFailView(DoiForm form, BindException errors)
        {
            if(_exception != null)
            {
                return new HtmlView(_exception.getHtmlString());
            }
            else
            {
                return super.getFailView(form, errors);
            }
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class AssignDoiAction extends DoiAction
    {
        private Doi _doi;

        @Override
        protected boolean actionAllowedInLastCopy()
        {
            return true;
        }

        @Override
        public ModelAndView getConfirmView(DoiForm form, BindException errors)
        {
            return new HtmlView(DIV("Are you sure you want to assign a DOI with the DataCite ", SPAN(at(style, "font-weight:bold;color:red"), form.isTestMode() ? "Test" : "Live"), " API?"));
        }

        @Override
        public void validateCommand(DoiForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if(errors.getErrorCount() > 0)
            {
                return;
            }

            if(_expAnnot.hasDoi())
            {
                errors.reject(ERROR_MSG, "This data is already assigned a DOI: " + _expAnnot.getDoi());
            }
        }

        @Override
        protected HtmlString getSuccessMessage()
        {
            return HtmlString.of("DOI assigned: " + _expAnnot.getDoi() + "; State: " + _doi.getState() + "; Findable: " + _doi.isFindable());
        }

        @Override
        public boolean doPost(DoiForm form, BindException errors) throws DataCiteException
        {
            _doi = DataCiteService.create(form.isTestMode());
            _expAnnot.setDoi(_doi.getDoi());
            ExperimentAnnotationsManager.updateDoi(_expAnnot);
            return true;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class DeleteDoiAction extends DoiAction
    {
        private String _doi;
        @Override
        public ModelAndView getConfirmView(DoiForm form, BindException errors)
        {
            return new HtmlView(DIV("Are you sure you want to delete the DOI: " + _expAnnot.getDoi()));
        }

        @Override
        public void validateCommand(DoiForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if(errors.getErrorCount() > 0)
            {
                return;
            }
            if(!_expAnnot.hasDoi())
            {
                errors.reject(ERROR_MSG,"This data is not assigned a DOI");
            }
        }

        @Override
        protected HtmlString getSuccessMessage()
        {
            return HtmlString.of("DOI deleted: " + _doi);
        }

        @Override
        public boolean doPost(DoiForm form, BindException errors) throws DataCiteException
        {
            _doi = _expAnnot.getDoi();
            DataCiteService.delete(_expAnnot.getDoi());
            _expAnnot.setDoi(null);
            ExperimentAnnotationsManager.updateDoi(_expAnnot);
            return true;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class PublishDoiAction extends DoiAction
    {
        private JournalSubmission _journalSubmission;
        private DoiMetadata _metadata;

        @Override
        protected boolean actionAllowedInLastCopy()
        {
            return true;
        }

        @Override
        public ModelAndView getConfirmView(DoiForm form, BindException errors)
        {
            return new HtmlView(DIV("Are you sure you want to publish the DOI: " + _expAnnot.getDoi(), BR(), _metadata.getHtmlString()));
        }

        @Override
        public void validateCommand(DoiForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if(errors.getErrorCount() > 0)
            {
                return;
            }
            _journalSubmission = SubmissionManager.getSubmissionForJournalCopy(_expAnnot);
            if(_journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a row in JournalExperiment for copied experiment " + _expAnnot.getId());
            }
            if(!_expAnnot.hasDoi())
            {
                errors.reject(ERROR_MSG,"This data is not assigned a DOI");
            }
            try
            {
                _metadata = DoiMetadata.from(_expAnnot);
            }
            catch (DataCiteException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        protected HtmlString getSuccessMessage()
        {
            return HtmlString.of("DOI published: " + _expAnnot.getDoi());
        }

        @Override
        public boolean doPost(DoiForm form, BindException errors) throws DataCiteException
        {
            DataCiteService.publish(_expAnnot);
            return true;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class UpdateDoiAction extends DoiAction
    {
        @Override
        public void validateCommand(DoiForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if(errors.getErrorCount() > 0)
            {
                return;
            }
            if(_expAnnot.getDoi() == null && StringUtils.isBlank(form.getDoi()))
            {
                errors.reject(ERROR_MSG,"Please enter a DOI");
            }
            if(_expAnnot.getDoi() != null && _expAnnot.getDoi().equals(form.getDoi()))
            {
                errors.reject(ERROR_MSG, "DOI entered is the same as the DOI already assigned to the experiment");
            }
        }

        @Override
        public ModelAndView getConfirmView(DoiForm form, BindException errors)
        {
            return _expAnnot.getDoi() != null ?
                   new HtmlView(DIV("This experiment is assigned the DOI: " + _expAnnot.getDoi() + " Are you sure you want to" +
                           (StringUtils.isBlank(form.getDoi()) ? " remove it?"
                                   : " overwrite it with: " + form.getDoi() + "?"),
                           BR(),
                           SPAN(at(style, "color:red; font-weight:bold;"), (StringUtils.isBlank(form.getDoi()) ?
                                   " The DOI " : " The old DOI"), " will NOT be deleted from DataCite.")))
                    : new HtmlView(DIV("Are you sure you want to set the DOI for this experiment to: " + form.getDoi() + "?"));
        }

        @Override
        protected HtmlString getSuccessMessage()
        {
            return HtmlString.of("DOI updated to: " + _expAnnot.getDoi());
        }

        @Override
        public boolean doPost(DoiForm form, BindException errors)
        {
            // TODO: If there is a DOI in the form we should check if it is valid.
            // All DOI related actions requires AdminOperationsPermission, so for now we assume that the admin knows what they are doing.
            _expAnnot.setDoi(StringUtils.isBlank(form.getDoi()) ? null : form.getDoi());
            ExperimentAnnotationsManager.updateDoi(_expAnnot);

            return true;
        }
    }

    public static class DoiForm extends ExperimentIdForm
    {
        private boolean _testMode;
        private String _method;

        private String _doi;

        public boolean isTestMode()
        {
            return _testMode;
        }

        public void setTestMode(boolean testMode)
        {
            _testMode = testMode;
        }

        public String getMethod()
        {
            return _method;
        }

        public void setMethod(String method)
        {
            _method = method;
        }

        public String getDoi()
        {
            return _doi;
        }

        public void setDoi(String doi)
        {
            _doi = doi;
        }
    }

    // ------------------------------------------------------------------------
    // END Actions for DataCite DOI assignment
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
            TargetedMSExperimentWebPart experimentDetailsView = new TargetedMSExperimentWebPart(exptAnnotations, getViewContext(), true);
            experimentDetailsView.setFrame(WebPartView.FrameType.PORTAL);
            experimentDetailsView.setTitle(TargetedMSExperimentWebPart.WEB_PART_NAME);
            VBox result = new VBox(experimentDetailsView);

            JournalSubmission journalSubmission;

            if(exptAnnotations.isJournalCopy())
            {

                journalSubmission = SubmissionManager.getSubmissionForJournalCopy(exptAnnotations);
            }
            else
            {
                journalSubmission = SubmissionManager.getNewestJournalSubmission(exptAnnotations);
            }
            if (journalSubmission != null)
            {
                List<Submission> publishedVersions = journalSubmission.getCopiedSubmissions();
                if (publishedVersions.size() > 0)
                {
                    List<DOM.Renderable> rows = new ArrayList<>();
                    rows.add(THEAD(TR(TH(cl("labkey-column-header"), "Version"), TH("Published"), TH("Link"))));
                    final AtomicInteger cnt = new AtomicInteger();
                    rows.add(TBODY(publishedVersions.stream().map(s ->
                            TR(cl(cnt.getAndIncrement() % 2 == 0, "labkey-alternate-row", "labkey-row"),
                                    TD(s.getVersion() == null ? (s.getCopied() == null ? "Submitted" : "Current") : s.getVersion().toString()),
                                    TD(DateUtil.formatDateTime(s.getCopied(), "MM/dd/yyyy")),
                                    TD(new Link.LinkBuilder(s.getShortAccessUrl().renderShortURL())
                                            .href(s.getShortAccessUrl().renderShortURL()).clearClasses().build())
                            ))));
                    HtmlView versionsView = new HtmlView(TABLE(cl("labkey-data-region-legacy", "labkey-show-borders"), rows));
                    versionsView.setTitle("Published Versions");
                    result.addView(versionsView);
                }
            }

            // Show a list of subfolders, if any
            List<Container> children = getAllSubfolders(exptAnnotations.getContainer());
            HtmlView subfoldersView = null;
            if(exptAnnotations.isIncludeSubfolders())
            {
                if(children.size() == 0)
                {
                   subfoldersView = new HtmlView(DIV(cl("labkey-error"),"Experiment is configured to include subfolders but no subfolders were found.",
                           BR(),
                           getExcludeSubfoldersButton(exptAnnotations).build()));
                }
                else
                {
                    subfoldersView = new HtmlView(DIV("This experiment includes data in the following subfolders:",
                            getSubfolderListHtml(exptAnnotations.getContainer(), children),
                            getExcludeSubfoldersButton(exptAnnotations).build()));
                }
            }
            else if(children.size() > 0)
            {
                subfoldersView = new HtmlView(DIV("This folder contains " + children.size() + " subfolders. " +
                                "Data from the subfolders is not included in this experiment. Click the button below to include subfolders.",
                    BR(),
                    new Button.ButtonBuilder("Include Subfolders")
                            .usePost()
                            .href(new ActionURL(IncludeSubFoldersInExperimentAction.class, getContainer()).addParameter("id", exptAnnotations.getId()))
                            .build()));
                
            }
            if(subfoldersView != null)
            {
                subfoldersView.setTitle("Subfolders");
                subfoldersView.setFrame(WebPartView.FrameType.PORTAL);
                result.addView(subfoldersView);
            }

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

            // If this experiment has been submitted show the submission requests
            List<JournalSubmission> jsList = SubmissionManager.getAllJournalSubmissions(exptAnnotations);
            if(jsList.size() > 0)
            {
                QuerySettings qSettings = new QuerySettings(getViewContext(), "Submissions", "Submission");
                qSettings.setBaseFilter(new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("JournalExperimentId"),
                        jsList.stream().map(js -> js.getJournalExperimentId()).collect(Collectors.toList()))));
                QueryView submissionList = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, errors);
                submissionList.setShowRecordSelectors(false);
                submissionList.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
                submissionList.disableContainerFilterSelection();
                submissionList.setShowExportButtons(false);
                submissionList.setShowPagination(false);
                submissionList.setPrintView(false);
                VBox journalsBox = new VBox();
                journalsBox.setTitle("Submission");
                journalsBox.setFrame(WebPartView.FrameType.PORTAL);
                journalsBox.addView(submissionList);

                ActionURL url = PanoramaPublicController.getPrePublishExperimentCheckURL(exptAnnotations.getId(), exptAnnotations.getContainer(), true);
                url.addReturnURL(getViewExperimentDetailsURL(exptAnnotations.getId(), exptAnnotations.getContainer()));
                journalsBox.addView(new HtmlView(DIV(new Link.LinkBuilder("Validate for ProteomeXchange").href(url).build())));

                result.addView(journalsBox);
            }
            return result;
        }

//        @NotNull
//        private String getExptVersion(JournalExperiment je)
//        {
//            StringBuilder sb = new StringBuilder(je.getVersion() == null ? "Current" : "Version " + je.getVersion());
//            sb.append(" ").append(je.getCopied()).append(" ").append(je.getShortAccessUrl().getFullURL());
//            return sb.toString();
//        }
//
//        private Iterable<Map.Entry<Object, Object>> getSubmittedVersion(JournalExperiment je)
//        {
//            return null;
//        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Targeted MS Experiment Details");
        }
    }

    public static class ExperimentAnnotationsDetails
    {
        private ExperimentAnnotations _experimentAnnotations;
        JournalSubmission _lastPublishedRecord;
        private boolean _fullDetails = false;
        private boolean _canPublish = false;

        public ExperimentAnnotationsDetails(){}
        public ExperimentAnnotationsDetails(User user, ExperimentAnnotations exptAnnotations, boolean fullDetails)
        {
            _experimentAnnotations = exptAnnotations;
            _fullDetails = fullDetails;

            Container c = _experimentAnnotations.getContainer();
            TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(c);
            if(isSupportedFolderType(folderType))
            {
                _lastPublishedRecord = SubmissionManager.getNewestJournalSubmission(_experimentAnnotations);

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

        public JournalSubmission getLastPublishedRecord()
        {
            return _lastPublishedRecord;
        }

        public void setLastPublishedRecord(JournalSubmission lastPublishedRecord)
        {
            _lastPublishedRecord = lastPublishedRecord;
        }
    }

    private static boolean isSupportedFolderType(TargetedMSService.FolderType folderType)
    {
        return folderType == Experiment || isLibraryFolder(folderType);
    }

    private static boolean isLibraryFolder(TargetedMSService.FolderType folderType)
    {
        return folderType == Library || folderType == LibraryProtein;
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
            JournalSubmission je = SubmissionManager.getNewestJournalSubmission(exptAnnotations);
            if(je != null && je.getNewestSubmission().isPxidRequested())
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
    public static class IncludeSubFoldersInExperimentAction extends FormHandlerAction<ExperimentForm>
    {
        private ExperimentAnnotations _expAnnot;
        private ActionURL _returnPublishExptUrl;

        @Override
        public void validateCommand(ExperimentForm form, Errors errors)
        {
            _expAnnot = form.lookupExperiment();
            if(_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup experiment annotations with ID " + form.getId());
                return;
            }

            ExpExperiment experiment = _expAnnot.getExperiment();
            if(experiment == null)
            {
                errors.reject(ERROR_MSG, "Failed to lookup base experiment for experimentAnnotations with ID " + _expAnnot.getTitle());
                return;
            }

            ensureCorrectContainer(getContainer(), experiment.getContainer(), getViewContext());

            if (ExperimentAnnotationsManager.hasExperimentsInSubfolders(_expAnnot.getContainer(), getUser()))
            {
                errors.reject(ERROR_MSG, "At least one of the subfolders contains an experiment. Cannot add subfolder data to this experiment.");
                return;
            }

            List<Container> allSubfolders = getAllSubfolders(_expAnnot.getContainer());
            if (allSubfolders.size() == 0)
            {
                errors.reject(ERROR_MSG, "No subfolders were found.");
                return;
            }

            List<Container> hiddenSubfolders = getHiddenFolders(allSubfolders, getUser());
            if (hiddenSubfolders.size() > 0)
            {
                errors.reject(ERROR_MSG, "User needs read permissions in all the subfolders to be able to include them in the experiment.");
            }

            ActionURL returnUrl = form.getReturnActionURL();
            if (returnUrl != null)
            {
                String action = returnUrl.getAction();
                if(SpringActionController.getActionName(PublishExperimentAction.class).equals(action) ||
                        SpringActionController.getActionName(RepublishJournalExperimentAction.class).equals(action) ||
                        SpringActionController.getActionName(UpdateJournalExperimentAction.class).equals(action))
                _returnPublishExptUrl = returnUrl;
            }
        }

        @Override
        public boolean handlePost(ExperimentForm form, BindException errors)
        {
            ExperimentAnnotationsManager.includeSubfoldersInExperiment(_expAnnot, getUser());

            // Add the Subfolders webpart if it is not already there
            String webpartName = "Subfolders";
            List<Portal.WebPart> parts = Portal.getParts(_expAnnot.getContainer(), DefaultFolderType.DEFAULT_DASHBOARD);
            boolean hasSubfolderPart = parts.stream().anyMatch(p -> webpartName.equals(p.getName()));

            if(!hasSubfolderPart)
            {
                WebPartFactory webPartFactory = Portal.getPortalPart(webpartName);
                if(webPartFactory != null)
                {
                    Portal.addPart(_expAnnot.getContainer(), DefaultFolderType.DEFAULT_DASHBOARD, webPartFactory, WebPartFactory.LOCATION_BODY);
                }
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(ExperimentForm form)
        {
            return _returnPublishExptUrl != null ? _returnPublishExptUrl : getViewExperimentDetailsURL(_expAnnot.getId(), _expAnnot.getContainer());
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class ExcludeSubFoldersInExperimentAction extends FormHandlerAction<ExperimentForm>
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

    public static class ExperimentForm extends ReturnUrlForm
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
            result.addReturnURL(returnURL);
        }
        result.addParameter("id", experimentAnnotationsId);
        return result;
    }

    public static ActionURL getExcludeSubfoldersInExperimentURL(int experimentAnnotationsId, Container container, URLHelper returnURL)
    {
        ActionURL result = new ActionURL(ExcludeSubFoldersInExperimentAction.class, container);
        if (returnURL != null)
        {
            result.addReturnURL(returnURL);
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
                new DeleteSubmissionAction(),
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
