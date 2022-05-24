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
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.data.Sort;
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
import org.labkey.api.pipeline.PipelineStatusFile;
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
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.targetedms.IModification;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.targetedms.TargetedMSUrls;
import org.labkey.api.util.Button;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.LK;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
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
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.model.validation.Modification;
import org.labkey.panoramapublic.model.validation.PxStatus;
import org.labkey.panoramapublic.model.validation.SkylineDoc;
import org.labkey.panoramapublic.model.validation.SkylineDocSampleFile;
import org.labkey.panoramapublic.model.validation.Status;
import org.labkey.panoramapublic.model.speclib.SpecLibDependencyType;
import org.labkey.panoramapublic.model.speclib.SpecLibInfo;
import org.labkey.panoramapublic.model.speclib.SpecLibSourceType;
import org.labkey.panoramapublic.model.speclib.SpectralLibrary;
import org.labkey.panoramapublic.pipeline.AddPanoramaPublicModuleJob;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;
import org.labkey.panoramapublic.pipeline.PxDataValidationPipelineJob;
import org.labkey.panoramapublic.pipeline.PxValidationPipelineProvider;
import org.labkey.panoramapublic.proteomexchange.ChemElement;
import org.labkey.panoramapublic.proteomexchange.ExperimentModificationGetter;
import org.labkey.panoramapublic.proteomexchange.Formula;
import org.labkey.panoramapublic.proteomexchange.NcbiUtils;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService;
import org.labkey.panoramapublic.proteomexchange.ProteomeXchangeServiceException;
import org.labkey.panoramapublic.proteomexchange.PsiInstrumentParser;
import org.labkey.panoramapublic.proteomexchange.PxException;
import org.labkey.panoramapublic.proteomexchange.PxHtmlWriter;
import org.labkey.panoramapublic.proteomexchange.PxXmlWriter;
import org.labkey.panoramapublic.proteomexchange.UnimodModification;
import org.labkey.panoramapublic.proteomexchange.UnimodModifications;
import org.labkey.panoramapublic.proteomexchange.UnimodUtil;
import org.labkey.panoramapublic.query.DataValidationManager;
import org.labkey.panoramapublic.query.DataValidationManager.MissingMetadata;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;
import org.labkey.panoramapublic.query.ModificationInfoManager;
import org.labkey.panoramapublic.query.PxXmlManager;
import org.labkey.panoramapublic.query.SpecLibInfoManager;
import org.labkey.panoramapublic.query.SubmissionManager;
import org.labkey.panoramapublic.query.modification.ExperimentIsotopeModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentModInfo;
import org.labkey.panoramapublic.query.modification.ExperimentStructuralModInfo;
import org.labkey.panoramapublic.query.modification.ModificationsView;
import org.labkey.panoramapublic.query.speclib.SpecLibView;
import org.labkey.panoramapublic.speclib.LibSourceFile;
import org.labkey.panoramapublic.speclib.SpecLibReader;
import org.labkey.panoramapublic.speclib.SpecLibReaderException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.labkey.api.targetedms.TargetedMSService.FolderType.Experiment;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.Library;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.LibraryProtein;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.QC;
import static org.labkey.api.targetedms.TargetedMSService.FolderType.Undefined;
import static org.labkey.api.targetedms.TargetedMSService.RAW_FILES_DIR;
import static org.labkey.api.targetedms.TargetedMSService.RAW_FILES_TAB;
import static org.labkey.api.util.DOM.*;
import static org.labkey.api.util.DOM.Attribute.action;
import static org.labkey.api.util.DOM.Attribute.border;
import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.valign;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.LK.CHECKBOX;
import static org.labkey.api.util.DOM.LK.ERRORS;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.panoramapublic.proteomexchange.NcbiUtils.PUBMED_ID;

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


    public PanoramaPublicController()
    {
        setActionResolver(_actionResolver);
    }

    private static final Logger LOG = LogHelper.getLogger(PanoramaPublicController.class, "PanoramaPublicController requests");

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

                ActionURL addPublicDataUserUrl = new ActionURL(AddPublicDataUserAction.class, getContainer());
                ActionButton addPublicDataUserButton = new ActionButton(addPublicDataUserUrl, "Add Public Data User");
                addPublicDataUserButton.setActionType(ActionButton.Action.GET);
                addPublicDataUserButton.setPrimary(false);
                buttonBar.add(addPublicDataUserButton);
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

    /*
    Set the user account that can be used to download public datasets on Panorama Public with a WebDAV client such as
    Cyberduck, or by mapping a network drive in Windows. Anonymous downloads do not work with WebDAV.
     */
    @RequiresPermission(AdminOperationsPermission.class)
    public static class AddPublicDataUserAction extends FormViewAction<PublicDataUseForm>
    {
        private Journal _journal;
        @Override
        public void validateCommand(PublicDataUseForm form, Errors errors)
        {
            _journal = form.lookupJournal();
            if (_journal == null)
            {
                errors.reject(ERROR_MSG, "No journal found for journal Id " + form.getId());
            }
            if (StringUtils.isBlank(form.getUserEmail()))
            {
                errors.reject(ERROR_MSG, "User email cannot be blank");
            }
            if (StringUtils.isBlank(form.getUserPassword()))
            {
                errors.reject(ERROR_MSG, "User password cannot be blank");
            }
        }

        @Override
        public ModelAndView getView(PublicDataUseForm form, boolean reshow, BindException errors)
        {
            if (!reshow)
            {
                _journal = form.lookupJournal();
                if (_journal == null)
                {
                    errors.reject(ERROR_MSG, "No journal found for journal Id " + form.getId());
                    return new SimpleErrorView(errors);
                }
                JournalManager.PublicDataUser publicDataUser = JournalManager.getPublicDataUser(_journal);
                if (publicDataUser != null)
                {
                    form.setUserEmail(publicDataUser.getEmail());
                }
            }

            HtmlView view = new HtmlView(
                    DIV(
                            ERRORS(errors),
                            "Enter the email address for the user account that can be used to download public datasets in a WebDAV client",
                            FORM(at(method, "POST", action, new ActionURL(AddPublicDataUserAction.class, getContainer()).addParameter("id", _journal.getId())),
                                    SPAN(cl("labkey-form-label"), "User Email Address"),
                                    INPUT(at(type, "Text", name, "userEmail", value, form.getUserEmail())),
                                    BR(),
                                    SPAN(cl("labkey-form-label"), "User Password"),
                                    INPUT(at(type, "Text", name, "userPassword", value, "")),
                                    BR(),
                                    new Button.ButtonBuilder("Save").submit(true).build(),
                                    new Button.ButtonBuilder("Cancel").submit(false).href(getJournalGroupDetailsUrl(_journal.getId(), getContainer())).build()
                            )
                    )
            );
            view.setTitle("Add Public Data User");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public boolean handlePost(PublicDataUseForm form, BindException errors)
        {
            ValidEmail validEmail;
            try
            {
                validEmail = new ValidEmail(form.getUserEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid email address");
                return false;
            }
            User user = UserManager.getUser(validEmail);
            if (user == null)
            {
                errors.reject(ERROR_MSG, "User with given email address does not exist");
                return false;
            }
            if (!SecurityManager.matchPassword(form.getUserPassword(), SecurityManager.getPasswordHash(validEmail)))
            {
                errors.reject(ERROR_MSG, "Incorrect password for " + user.getEmail());
                return false;
            }
            JournalManager.savePublicDataUser(_journal, user, form.getUserPassword());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(PublicDataUseForm form)
        {
            return getJournalGroupDetailsUrl(form.getId(), getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Configure Public Data User");
        }
    }

    public static class PublicDataUseForm extends JournalForm
    {
        private String _userEmail;
        private String _userPassword;

        public String getUserEmail()
        {
            return _userEmail;
        }

        public void setUserEmail(String userEmail)
        {
            _userEmail = userEmail;
        }

        public String getUserPassword()
        {
            return _userPassword;
        }

        public void setUserPassword(String userPassword)
        {
            _userPassword = userPassword;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ManageDataCiteCredentials extends FormViewAction<DataCiteCredentialsForm>
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
    public static class ManageProteomeXchangeCredentials extends FormViewAction<PXCredentialsForm>
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
        private Submission _pendingSubmission;

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
                CopyExperimentForm.setDefaults(form, _experiment, _pendingSubmission);
            }

            if (_pendingSubmission.isPxidRequested())
            {
                var validation = DataValidationManager.getLatestValidation(_experiment.getId(), getContainer());
                if (validation == null)
                {
                    return getStartDataValidationView("A ProteomeXchange ID is requested but no data validation was found. Run a validation job before copying",
                            _experiment, getContainer());
                }
                if (!validation.isComplete())
                {
                    return getDataValidationIncompleteView(validation, false, _experiment, getContainer());
                }
                if (DataValidationManager.isValidationOutdated(validation, _experiment, getUser()))
                {
                    return getStartDataValidationView("Last validation check (Id: " + validation.getId() + ") is outdated. Run a validation job again before copying.",
                            _experiment, getContainer());
                }
            }

            JspView<CopyExperimentBean> view = new JspView<>("/org/labkey/panoramapublic/view/publish/copyExperimentForm.jsp", new CopyExperimentBean(form, _experiment, _journalSubmission), errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Copy Targeted MS Experiment");
            return view;
        }

        private boolean validateAction(CopyExperimentForm form, BindException errors)
        {
            _experiment = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (_experiment == null)
            {
                return false;
            }

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find journal with id " + form.getJournalId());
                return false;
            }
            // User initiating the copy must be a member of a journal that was given access
            // to the experiment.
            if (!JournalManager.userHasCopyAccess(_experiment, _journal, getUser()))
            {
                errors.reject(ERROR_MSG,"You do not have permissions to copy this experiment.");
                return false;
            }
            _journalSubmission = SubmissionManager.getJournalSubmission(_experiment.getId(), _journal.getId(), getContainer());
            if (_journalSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a submission request for experiment Id " + _experiment.getId()
                + " and journalId " + _journal.getId() + " in the folder '" + getContainer().getPath() + "'");
                return false;
            }

            _pendingSubmission = _journalSubmission.getPendingSubmission();
            if (_pendingSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a pending submission request for experiment Id " + _experiment.getId()
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

            // Validate the data if a ProteomeXchange ID was requested.
            if (!isDataValid(_experiment, _pendingSubmission, getContainer(), getUser(), errors))
            {
                return false;
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

            Submission previousSubmission = _journalSubmission.getLatestCopiedSubmission();
            if (previousSubmission != null)
            {
                // Target folder name is automatically populated in the copy experiment form. Unless the admin making the copy changed the
                // folder name we expect the previous copy of the data to have the same folder name. Rename the old folder so that we can
                // use the same folder name for the new copy.
                if (!renamePreviousFolder(previousSubmission, destinationFolder, errors))
                {
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

        private boolean renamePreviousFolder(Submission previousSubmission, String targetContainerName, BindException errors)
        {
            ExperimentAnnotations previousCopy = ExperimentAnnotationsManager.get(previousSubmission.getCopiedExperimentId());
            if (previousCopy != null)
            {
                Container previousContainer = previousCopy.getContainer();
                if (targetContainerName.equals(previousContainer.getName()))
                {
                    try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                    {
                        Integer version = previousCopy.getDataVersion();
                        if (version == null)
                        {
                            errors.reject(ERROR_MSG, "Previous experiment copy (Id: " + previousCopy.getId() + ") does not have a version. " +
                                    "Cannot rename previous folder.");
                            return false;
                        }
                        // Rename the container where the old copy lives so that the same folder name can be used for the new copy.
                        String newName = previousContainer.getName() + " V." + version;
                        if (ContainerManager.getChild(previousContainer.getParent(), newName) != null)
                        {
                            errors.reject(ERROR_MSG, "Cannot rename previous folder to '" + newName + "'. A folder with that name already exists.");
                            return false;
                        }
                        ContainerManager.rename(previousContainer, getUser(), newName);
                        transaction.commit();
                    }
                }
            }
            return true;
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

    public static class CopyExperimentBean
    {
        private final CopyExperimentForm _form;
        private final ExperimentAnnotations _expAnnotations;
        private final JournalSubmission _journalSubmission;

        public CopyExperimentBean(CopyExperimentForm form, ExperimentAnnotations expAnnotations, JournalSubmission journalSubmission)
        {
            _form = form;
            _expAnnotations = expAnnotations;
            _journalSubmission = journalSubmission;
        }

        public CopyExperimentForm getForm()
        {
            return _form;
        }

        public ExperimentAnnotations getExpAnnotations()
        {
            return _expAnnotations;
        }

        public JournalSubmission getJournalSubmission()
        {
            return _journalSubmission;
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

        static void setDefaults(CopyExperimentForm form, ExperimentAnnotations sourceExperiment, Submission currentSubmission)
        {
            if (currentSubmission.isKeepPrivate())
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
            if (submitter != null)
            {
                toEmailAddresses.add(submitter.getEmail());
            }

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
            else if (!StringUtils.isBlank(currentSubmission.getLabHeadEmail()))
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
            String projectName = project != null ? StringUtils.normalizeSpace(project.getName().replaceAll("-", "")) : "";
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

            // Bad or missing returnUrl -- go to experiment annotation details
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

    private static boolean isDataValid(ExperimentAnnotations experimentAnnotations, Submission submission, Container container, User user, Errors errors)
    {
        if(submission.isPxidRequested())
        {
            return isDataValid(experimentAnnotations, submission.isIncompletePxSubmission(), container, user, errors);
        }
        return true;
    }

    private static boolean isDataValid(ExperimentAnnotations experimentAnnotations, boolean incompleteSubmission, Container container, User user, Errors errors)
    {
        var validation = DataValidationManager.getLatestValidation(experimentAnnotations.getId(), container);
        return isDataValid(experimentAnnotations, validation, incompleteSubmission, user, errors);
    }

    private static boolean isDataValid(ExperimentAnnotations experimentAnnotations, DataValidation validation, boolean incompleteSubmission, User user, Errors errors)
    {
        if (validation == null)
        {
            errors.reject(ERROR_MSG, "A ProteomeXchange ID was requested but no data validation row was found.");
        }
        else if (validation.getStatus() == null)
        {
            errors.reject(ERROR_MSG, "Data validation Id " + validation.getId() + " does not have a PX status.");
        }
        else if (DataValidationManager.isValidationOutdated(validation, experimentAnnotations, user))
        {
            errors.reject(ERROR_MSG, "Data validation Id " + validation.getId() + " is outdated. Data validation must be run again before copying.");
        }
        else if (validation.getStatus().invalidSubmission())
        {
            errors.reject(ERROR_MSG, "A ProteomeXchange ID was requested.  But the data is not valid for a ProteomeXchange submission");
        }
        else if (!incompleteSubmission && validation.getStatus().incompleteSubmission())
        {
            errors.reject(ERROR_MSG, "Data is not valid for a \"complete\" ProteomeXchange submission.");
        }
        else
        {
            return true;
        }
        return false;
    }

    public static HtmlView getStartDataValidationView(String message, ExperimentAnnotations experimentAnnotations, Container container)
    {
        var url = new ActionURL(DataValidationCheckAction.class, container).addParameter("id", experimentAnnotations.getId());
        return new HtmlView(DIV(DIV(at(style, "margin-bottom:15px;"), message), new Button.ButtonBuilder("Validate Data").href(url).build()));
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
        DataValidation _dataValidation;
        private boolean _doConfirm = false;

        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors)
        {
            if (reshow)
            {
                if(_doConfirm)
                {
                    // Could extend ConfirmAction but it does not allow setting a cancel URL. It takes a cancel
                    // text but generates a back button.  We would rather go back to the folder home page.
                    return getConfirmView(form, errors);
                }
                else
                {
                    // Show the form with the errors
                    return getPublishFormView(form, _experimentAnnotations, errors, false);
                }
            }
            else
            {
                // Handle GET request
                if (!validateGetRequest(form, errors))
                {
                    return new SimpleErrorView(errors);
                }
                checkForValidation(_experimentAnnotations, form);
                checkIfResubmit(_experimentAnnotations, form, errors);
                if (errors.hasErrors())
                {
                    return new SimpleErrorView(errors);
                }

                return getView(form, errors);
            }
        }

        private ModelAndView getView(PublishExperimentForm form, BindException errors)
        {
            if(form.isDoSubfolderCheck())
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

            // Cannot submit if this is not a supported folder type
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

            // Ensure that there is at least one Skyline document in submission.
            if (!hasSkylineDocs(_experimentAnnotations))
            {
                errors.reject(ERROR_MSG, "There are no Skyline documents included in this experiment.  " +
                        "Please upload one or more Skyline documents to proceed with the submission request.");
                return new SimpleErrorView(errors);
            }

            // For Panorama Public we will require that users import .sky.zip files. The code allows importing .sky files.
            // This may be OK for template documents that do not have any imported chromatograms but they should be rare on Panorama.
            // Most documents should have chromatogram data (.skyd files in the .sky.zip archive)
            List<ITargetedMSRun> notSkyZipRuns = getNotSkyZipRuns(_experimentAnnotations);
            if (notSkyZipRuns.size() > 0)
            {
                return new HtmlView(DIV("The following Skyline documents are incomplete. " +
                                "Please upload the complete Skyline ZIP archive (.sky.zip file) by using the 'Upload to Panorama' button or menu in Skyline.",
                        UL(notSkyZipRuns.stream().map(run-> LI(run.getFileName()))),
                        BR(),
                        DIV(getBackToFolderButton(getContainer()))
                ));
            }

            // Require that the users selected as the submitter and lab head have first and last names in their account details
            if(userInfoIncomplete(_experimentAnnotations, errors))
            {
                return getAccountInfoIncompleteView(errors);
            }

            // PX validation is not required for small molecule data since ProteomeXchange accepts only proteomic data
            boolean hasProteomicData = ExperimentAnnotationsManager.hasProteomicData(_experimentAnnotations, getUser());
            if (!hasProteomicData)
            {
                form.setGetPxid(false); // We cannot assign a ProteomeXchange ID if the experiment does not contain any proteomic data
            }
            boolean validateForPx = hasProteomicData && form.isGetPxid();
            MissingMetadata missingMetadata = DataValidationManager.getMissingExperimentMetadataFields(_experimentAnnotations, validateForPx);
            if (missingMetadata.count() > 0)
            {
                return getMissingMetadataView(_experimentAnnotations, missingMetadata, validateForPx, errors);
            }

            if (validateForPx)
            {
                if (form.getValidationId() == null)
                {
                    var validation = DataValidationManager.getLatestValidation(_experimentAnnotations.getId(), getContainer());
                    if (validation != null && validation.isComplete() && !DataValidationManager.isValidationOutdated(validation, _experimentAnnotations, getUser()))
                    {
                        // Redirect to validation status page
                        throw new RedirectException(getPxValidationStatusUrl(_experimentAnnotations.getId(),
                                validation.getId(), _experimentAnnotations.getContainer()));
                    }
                }
                else
                {
                    _dataValidation = DataValidationManager.getValidation(form.getValidationId(), getContainer());
                    if (_dataValidation == null)
                    {
                        errors.reject(ERROR_MSG, "Could not find a data validation row for Id " + form.getValidationId());
                        return new SimpleErrorView(errors);
                    }
                    if (!_dataValidation.isComplete())
                    {
                        return getDataValidationIncompleteView(_dataValidation, true, _experimentAnnotations, getContainer());
                    }
                    if (!DataValidationManager.isLatestValidation(_dataValidation, getContainer()))
                    {
                        errors.reject(ERROR_MSG, "Data validation with Id " + _dataValidation.getId() + " is not the latest validation.");
                        return new SimpleErrorView(errors);
                    }
                    if (DataValidationManager.isValidationOutdated(_dataValidation, _experimentAnnotations, getUser()))
                    {
                        return getStartValidationView("Data validation is outdated.");
                    }

                    // Validation is complete and current. Show the form.
                    return getPublishFormView(form, _experimentAnnotations, errors);
                }
                return getStartValidationView();
            }
            else
            {
                // ProteomeXchange validation is not required because this is small molecule data OR the submitter chose to not get a ProteomeXchange Id
                return getPublishFormView(form, _experimentAnnotations, errors);
            }
        }

        protected void checkForValidation(ExperimentAnnotations experimentAnnotations, PublishExperimentForm form)
        {
            if (form.getValidationId() != null)
            {
                var validation = DataValidationManager.getValidation(form.getValidationId(), getContainer());
                if (validation != null && DataValidationManager.isPipelineJobRunning(validation))
                {
                    throw new RedirectException(getPxValidationStatusUrl(_experimentAnnotations.getId(),
                            validation.getId(), _experimentAnnotations.getContainer()));
                }
            }
        }

        protected void checkIfResubmit(ExperimentAnnotations experimentAnnotations, PublishExperimentForm form, Errors errors)
        {
            JournalSubmission js = SubmissionManager.getNewestJournalSubmission(experimentAnnotations);
            if (js != null)
            {
                var latestSubmission = js.getLatestSubmission();
                if (latestSubmission != null)
                {
                    if (latestSubmission.isPending() && !form.isUpdate())
                    {
                        errors.reject(ERROR_MSG, "A submission is currently pending. Expected a submission update request.");
                    }
                    if (latestSubmission.hasCopy() && !form.isResubmit())
                    {
                        errors.reject(ERROR_MSG, "The latest submission request was copied. Expected a resubmit request.");
                    }
                }
            }
        }

        private static List<ITargetedMSRun> getNotSkyZipRuns(@NotNull ExperimentAnnotations expAnnot)
        {
            TargetedMSService service = TargetedMSService.get();
            if (service != null)
            {
                Set<Container> expContainers = expAnnot.isIncludeSubfolders() ? ContainerManager.getAllChildren(expAnnot.getContainer())
                        : Collections.singleton(expAnnot.getContainer());
                List<ITargetedMSRun> allRuns = new ArrayList<>();
                for (Container container: expContainers)
                {
                    allRuns.addAll(service.getRuns(container));
                }
                return allRuns.stream().filter(run -> run.getFileName().endsWith(".sky")).collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @NotNull
        protected HtmlView getStartValidationView()
        {
            return getStartValidationView(null, true);
        }

        @NotNull
        private HtmlView getStartValidationView(String message)
        {
            return getStartValidationView(DIV(message));
        }

        @NotNull
        protected HtmlView getStartValidationView(DOM.Renderable message)
        {
            return getStartValidationView(message, true);
        }

        @NotNull
        protected HtmlView getStartValidationView(DOM.Renderable message, boolean forSubmit)
        {
            return PanoramaPublicController.getStartValidationView(message, forSubmit, _experimentAnnotations, getContainer(),
                    getViewContext().getActionURL());
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
                            getBackToFolderButton(getContainer())));
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
            _experimentAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);
            return _experimentAnnotations != null;
        }

        @NotNull
        protected ModelAndView getMissingMetadataView(ExperimentAnnotations expAnnotations, MissingMetadata missingMedata, boolean pxSubmission, BindException errors)
        {
            return getMissingMetadataView(new MissingMetadataBean(expAnnotations, pxSubmission, missingMedata), "Missing Metadata in Submission Request", errors);
        }

        @NotNull ModelAndView getMissingMetadataView(MissingMetadataBean missingMetadataBean, String viewTitle, BindException errors)
        {
            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/missingMetadata.jsp", missingMetadataBean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle(viewTitle);
            return view;
        }

        protected WebPartView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors)
        {
            return getPublishFormView(form, exptAnnotations, errors, true);
        }

        private JspView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors, boolean populateForm)
        {
            if (populateForm)
            {
                populateForm(form, exptAnnotations);
            }
            PublishExperimentFormBean bean = new PublishExperimentFormBean(form, JournalManager.getJournals(), Arrays.asList(DataLicense.values()), exptAnnotations);
            JspView<PublishExperimentFormBean> view = new JspView<>("/org/labkey/panoramapublic/view/publish/publishExperimentForm.jsp", bean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle(getFormViewTitle(form.lookupJournal().getName()));
            return view;
        }

        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            form.setId(exptAnnotations.getId());
            form.setShortAccessUrl(generateRandomUrl(RANDOM_URL_SIZE));
            List<Journal> journals = JournalManager.getJournals();
            if (journals.size() == 0)
            {
                throw new NotFoundException("Could not find any journals.");
            }
            form.setJournalId(journals.get(0).getId()); // This is "Panorama Public" on panoramaweb.org

            form.setDataLicense(DataLicense.defaultLicense().name()); // CC BY 4.0 is default license
            form.setKeepPrivate(true);
            if (_dataValidation != null)
            {
                form.setIncompletePxSubmission(_dataValidation.getStatus().incompleteSubmission());
            }
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
            _experimentAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);

            if(_experimentAnnotations == null)
            {
                return;
            }

            _journal = form.lookupJournal();
            if(_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find a journal with Id " + form.getJournalId());
            }

            if (form.isGetPxid())
            {
                if (form.getValidationId() == null)
                {
                    errors.reject(ERROR_MSG, "A ProteomeXchange Id was requested but a data validation Id was not found in the request.");
                }
                else
                {
                    _dataValidation = DataValidationManager.getValidation(form.getValidationId(), getContainer());
                    if (_dataValidation == null)
                    {
                        errors.reject(ERROR_MSG, "Could not find a data validation row for Id " + form.getValidationId());
                    }
                    else if (!DataValidationManager.isLatestValidation(_dataValidation, getContainer()))
                    {
                        errors.reject(ERROR_MSG, String.format("Data validation Id %d is not the latest validation in the folder '%s'.",
                                _dataValidation.getId(), getContainer().getPath()));
                    }
                    else
                    {
                        isDataValid(_experimentAnnotations, _dataValidation, form.isIncompletePxSubmission(), getUser(), errors);
                    }
                }
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
                    JournalManager.setupJournalAccess(_experimentAnnotations, _journal, getUser());

                    PanoramaPublicRequest request = new PanoramaPublicRequest(_experimentAnnotations, _journal, form);
                    // Save the short access URL
                    ActionURL accessUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_experimentAnnotations.getContainer());
                    ShortURLRecord accessUrlRecord = JournalManager.saveShortURL(accessUrl, request.getShortAccessUrl(), _journal, getUser());

                    // Save the short copy URL.
                    ActionURL copyUrl = PanoramaPublicController.getCopyExperimentURL(_experimentAnnotations.getId(), _journal.getId(), _experimentAnnotations.getContainer());
                    ShortURLRecord copyUrlRecord = JournalManager.saveShortURL(copyUrl, request.getShortCopyUrl(), null, getUser());

                    JournalSubmission js = SubmissionManager.createNewSubmission(request, accessUrlRecord, copyUrlRecord, getUser());
                    // Create notifications
                    PanoramaPublicNotification.notifyCreated(_experimentAnnotations, _journal, js.getJournalExperiment(), js.getLatestSubmission(), getUser());

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
                errors.reject(ERROR_MSG, "\"" + journal.getName() + "\" already has access to this experiment. Please select another publication target." );
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
    private static HtmlView getStartValidationView(@Nullable DOM.Renderable message, boolean forSubmit,
                                                   ExperimentAnnotations experimentAnnotations, Container container,
                                                   @Nullable ActionURL submitUrl)
    {
        var componentList = new ArrayList<DOM.Renderable>();
        if (message != null)
        {
            componentList.add(DIV(at(style, "margin-bottom:10px;"), message));
        }
        componentList.add(DIV(at(style, "margin-bottom:10px;"), "Click the button to start data validation.",
                SPAN(at(style, "margin-left:10px;"),
                        getStartDataValidationButton(experimentAnnotations, container, "Validate Data for ProteomeXchange", null, forSubmit).build())));

        if (forSubmit && submitUrl != null)
        {
            ActionURL noPxSubmissionUrl = submitUrl.clone().replaceParameter("getPxid", "false");
            componentList.add(DIV(at(style, "margin-bottom:10px;"), "If you do not want a ProteomeXchange ID click the link to ",
                    new Link.LinkBuilder("Submit without a ProteomeXchange ID").href(noPxSubmissionUrl).build()));
        }
        componentList.add(DIV(at(style, "top-bottom:10px;"), new Button.ButtonBuilder("Back to Experiment Details")
                .href(getViewExperimentDetailsURL(experimentAnnotations.getId(), container)).build()));
        HtmlView view = new HtmlView(DIV(componentList));
        view.setTitle("Data Validation For ProteomeXchange");
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }

    private static Button.ButtonBuilder getStartDataValidationButton(ExperimentAnnotations expAnnotations, Container container)
    {
        return getStartDataValidationButton("Start Data Validation", expAnnotations, container);
    }

    private static Button.ButtonBuilder getRerunDataValidationButton(ExperimentAnnotations expAnnotations, Container container)
    {
        return getStartDataValidationButton("Rerun Validation", expAnnotations, container);
    }

    private static Button.ButtonBuilder getStartDataValidationButton(String buttonText, ExperimentAnnotations expAnnotations, Container container)
    {
        return getStartDataValidationButton(expAnnotations, container,
                buttonText,
                "Are you sure you want to start data validation?", true);
    }

    private static Button.ButtonBuilder getStartDataValidationButton(ExperimentAnnotations expAnnotations, Container container,
                                                                     String buttonText, String confirmMessage, boolean forSubmit)
    {
        var validateDataUrl = getSubmitPxValidationJobUrl(expAnnotations, container);
        if (!forSubmit)
        {
            validateDataUrl.addParameter("forSubmit", false);
        }
        return new Button.ButtonBuilder(buttonText).href(validateDataUrl).usePost(confirmMessage);
    }

    @NotNull
    private static HtmlView getDataValidationIncompleteView(DataValidation dataValidation, boolean forSubmit, ExperimentAnnotations experimentAnnotations, Container container)
    {
        var pipelineJobStatus = DataValidationManager.getPipelineJobStatus(dataValidation);

        if (DataValidationManager.isRunningStatus(pipelineJobStatus))
        {
            if (forSubmit)
            {
                // Validation job is still running; redirect to validation status page
                throw new RedirectException(getPxValidationStatusUrl(experimentAnnotations.getId(),
                        dataValidation.getId(), container));
            }
            else
            {
                return new HtmlView(DIV("Validation job is not complete.", BR(),
                        new Button.ButtonBuilder("View Validation Status")
                                .href(getPxValidationStatusUrlNotForSubmit(dataValidation.getExperimentAnnotationsId(), dataValidation.getId(), container))
                                .build()));
            }
        }
        else
        {
            // The job errored out / was cancelled / deleted
            var jobDetailsUrl = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(container, dataValidation.getJobId());
            return getStartValidationView(DIV(
                    "Data validation with Id " + dataValidation.getId() + " is incomplete.",
                    pipelineJobStatus != null ?
                            SPAN("The status of the pipeline job (Id: " + dataValidation.getJobId() + ") is ",
                                    new Link.LinkBuilder(pipelineJobStatus.getStatus()).href(jobDetailsUrl).build())
                            : " The pipeline job (Id: " + dataValidation.getJobId() + ") may have been deleted."),
                    forSubmit, experimentAnnotations, container, null);
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
                .map(child -> LI(new Link.LinkBuilder(parent.getParsedPath().relativize(child.getParsedPath()).toString())
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
        private final PublishExperimentForm _form;
        private final List<Journal> _journalList;
        private final List<DataLicense> _dataLicenseList;
        private final ExperimentAnnotations _experimentAnnotations;
        private final boolean _accessUrlEditable; // flag which determines if the "Short Access URL" field in the form is editable

        public PublishExperimentFormBean(PublishExperimentForm form, List<Journal> journalList, List<DataLicense> dataLicenseList, ExperimentAnnotations experimentAnnotations)
        {
            _form = form;
            _journalList = journalList;
            _dataLicenseList = dataLicenseList;
            _experimentAnnotations = experimentAnnotations;
            JournalSubmission js = SubmissionManager.getJournalSubmission(experimentAnnotations.getId(), form.getJournalId(), experimentAnnotations.getContainer());
            // "Short Access URL" field in the form should not be editable if one or more copies of this experiment already exist in the journal project
            _accessUrlEditable = js == null ? true : js.getCopiedSubmissions().size() == 0;
        }

        public PublishExperimentForm getForm()
        {
            return _form;
        }

        public List<Journal> getJournalList()
        {
            return _journalList;
        }

        public List<DataLicense> getDataLicenseList()
        {
            return _dataLicenseList;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public boolean isAccessUrlEditable()
        {
            return _accessUrlEditable;
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

    public static class PublishExperimentForm extends ExperimentIdForm
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
        private boolean _requestConfirmed;
        private boolean _resubmit;
        private Integer _validationId;

        private boolean _doSubfolderCheck = true;

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

        public Integer getValidationId()
        {
            return _validationId;
        }

        public void setValidationId(Integer validationId)
        {
            _validationId = validationId;
        }

        public boolean isDoSubfolderCheck()
        {
            return _doSubfolderCheck;
        }

        public void setDoSubfolderCheck(boolean doSubfolderCheck)
        {
            _doSubfolderCheck = doSubfolderCheck;
        }

        public boolean skipPxCheck()
        {
            return !_getPxid;
        }
    }
    // ------------------------------------------------------------------------
    // END Action for publishing an experiment (provide copy access to a journal)
    // ------------------------------------------------------------------------

    public static ExperimentAnnotations getValidExperiment(ExperimentIdForm form, Container container, ViewContext viewContext, Errors errors)
    {
        ExperimentAnnotations experimentAnnotations = form.lookupExperiment();
        if (experimentAnnotations == null)
        {
            errors.reject(ERROR_MSG, "No experiment found for Id " + form.getId());
            return null;
        }

        ensureCorrectContainer(container, experimentAnnotations.getContainer(), viewContext);

        return experimentAnnotations;
    }

    public static class MissingMetadataBean
    {
        private final ExperimentAnnotations _expAnnotations;
        private final MissingMetadata _missingMetadata;
        private final boolean _pxSubmission;
        private final boolean _forSubmit;

        public MissingMetadataBean(ExperimentAnnotations expAnnotations, boolean pxSubmission, MissingMetadata missingMetadata, boolean forSubmit)
        {
            _expAnnotations = expAnnotations;
            _pxSubmission = pxSubmission;
            _missingMetadata = missingMetadata;
            _forSubmit = forSubmit;
        }

        public MissingMetadataBean(ExperimentAnnotations expAnnotations, boolean pxSubmission, MissingMetadata missingMetadata)
        {
            this(expAnnotations, pxSubmission, missingMetadata, true);
        }

        public ExperimentAnnotations getExpAnnotations()
        {
            return _expAnnotations;
        }

        public MissingMetadata getMissingMetadata()
        {
            return _missingMetadata;
        }

        public boolean isPxSubmission()
        {
            return _pxSubmission;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SubmitPxValidationJobAction extends FormHandlerAction<PxDataValidationForm>
    {
        private int _validationId;
        @Override
        public boolean handlePost(PxDataValidationForm form, BindException errors)
        {
            ExperimentAnnotations experimentAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);

            if (experimentAnnotations != null)
            {
                DataValidation validation = submitValidationJob(experimentAnnotations, getUser(), getViewContext(), errors);
                if (validation != null)
                {
                    _validationId = validation.getId();
                    return true;
                }
            }
            return false;
        }

        private DataValidation submitValidationJob(@NotNull ExperimentAnnotations experimentAnnotations, @NotNull User user,
                                                          @NotNull ViewContext viewContext, BindException errors)
        {
            try{
                Container container = experimentAnnotations.getContainer();
                PipeRoot root = PipelineService.get().findPipelineRoot(container);
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found for " + container.getPath());
                }
                ViewBackgroundInfo info = new ViewBackgroundInfo(container, user, viewContext.getActionURL());

                DataValidation validation = new DataValidation(experimentAnnotations.getId());
                try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
                {
                    validation = DataValidationManager.saveDataValidation(validation, user);
                    PxDataValidationPipelineJob job = new PxDataValidationPipelineJob(info, root, experimentAnnotations, validation.getId());
                    PipelineService.get().queueJob(job);
                    Integer jobId = PipelineService.get().getJobId(user, container, job.getJobGUID());
                    if (jobId == null)
                    {
                        errors.reject(ERROR_MSG, "Data validation job was not submitted for experiment Id: " + experimentAnnotations.getId());
                        return null;
                    }
                    else
                    {
                        validation.setJobId(jobId);
                        validation = DataValidationManager.updateDataValidation(validation, user);
                    }

                    transaction.commit();
                    return validation;
                }
            }
            catch (PipelineValidationException e)
            {
                errors.reject(ERROR_MSG, "There was an error starting a pipeline job to validate the data for ProteomeXchange.");
            }
            return null;
        }

        @Override
        public URLHelper getSuccessURL(PxDataValidationForm form)
        {
            return form.isForSubmit() ? getPxValidationStatusUrl(form.getId(), _validationId, getContainer())
                    : getPxValidationStatusUrlNotForSubmit(form.getId(), _validationId, getContainer());
        }

        @Override
        public void validateCommand(PxDataValidationForm form, Errors errors)
        {
            var validationJobs = PipelineService.get().getActivePipelineJobs(getUser(), getContainer(), PxValidationPipelineProvider.NAME);
            if (validationJobs.size() > 0)
            {
                var job = validationJobs.size() > 1 ? "jobs" : "job";
                errors.reject(ERROR_MSG, String.format("%d data validation %s %s already running in this folder. Wait for the running %s to finish, or cancel the %s and try again.",
                        validationJobs.size(), job, validationJobs.size() > 1 ? "are" : "is", job, job));
            }
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class PxValidationStatusAction extends SimpleViewAction<PxDataValidationForm>
    {
        ExperimentAnnotations _experimentAnnotations;
        @Override
        public ModelAndView getView(PxDataValidationForm form, BindException errors)
        {
            _experimentAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (_experimentAnnotations == null)
            {
                return new SimpleErrorView(errors);
            }

            var validation = DataValidationManager.getValidation(form.getValidationId(), getContainer());
            if (validation == null)
            {
                errors.reject(ERROR_MSG,
                        String.format("Could not find a data validation row with Id %d, in the folder '%s'.", form.getValidationId(), getContainer().getPath()));
                return new SimpleErrorView(errors);
            }

            if (validation.getExperimentAnnotationsId() != _experimentAnnotations.getId())
            {
                errors.reject(ERROR_MSG, "Validation Id in the form does not belong to experiment Id " + _experimentAnnotations.getId());
                return new SimpleErrorView(errors, false);
            }

            int jobId = validation.getJobId();
            PipelineStatusFile pipelineJobStatus = PipelineService.get().getStatusFile(jobId);
            JournalSubmission js = SubmissionManager.getNewestJournalSubmission(_experimentAnnotations);
            JspView view;
            if (validation.isComplete())
            {
                Status validationStatus = DataValidationManager.getStatus(validation, getUser());

                view = new JspView<>("/org/labkey/panoramapublic/view/publish/pxValidationStatus.jsp",
                        new PxValidationStatusBean(_experimentAnnotations, js, pipelineJobStatus, validationStatus));
            }
            else
            {
                view = new JspView<>("/org/labkey/panoramapublic/view/publish/pxValidationRunning.jsp",
                        new PxValidationStatusBean(_experimentAnnotations, validation, js, pipelineJobStatus));
            }
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Data Validation Result");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Data Validation");
        }
    }

    public static class PxValidationStatusBean
    {
        private final ExperimentAnnotations _expAnnotations;
        private final DataValidation _dataValidation;
        private final JournalSubmission _journalSubmission;
        private final PipelineStatusFile _pipelineJobStatus;
        private Status _pxValidationStatus;

        public PxValidationStatusBean(@NotNull ExperimentAnnotations expAnnotations, @NotNull DataValidation dataValidation,
                                      @Nullable JournalSubmission journalSubmission, @Nullable PipelineStatusFile pipelineJobStatus)
        {
            _expAnnotations = expAnnotations;
            _dataValidation = dataValidation;
            _journalSubmission = journalSubmission;
            _pipelineJobStatus = pipelineJobStatus;
        }

        public PxValidationStatusBean(@NotNull ExperimentAnnotations expAnnotations, @Nullable JournalSubmission journalSubmission,
                                      @Nullable PipelineStatusFile pipelineJobStatus, @NotNull Status validationStatus)
        {
            this(expAnnotations, validationStatus.getValidation(), journalSubmission, pipelineJobStatus);
            _pxValidationStatus = validationStatus;
        }

        public @NotNull ExperimentAnnotations getExpAnnotations()
        {
            return _expAnnotations;
        }

        public @NotNull DataValidation getDataValidation()
        {
            return _dataValidation;
        }

        public @Nullable JournalSubmission getJournalSubmission()
        {
            return _journalSubmission;
        }

        public @Nullable Submission getSubmission()
        {
            return _journalSubmission != null ? _journalSubmission.getLatestSubmission() : null;
        }

        public @Nullable Integer getJournalId()
        {
            return _journalSubmission != null ? _journalSubmission.getJournalId() : null;
        }

        public @Nullable PipelineStatusFile getPipelineJobStatus()
        {
            return _pipelineJobStatus;
        }

        public @Nullable Status getPxValidationStatus()
        {
            return _pxValidationStatus;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class PxValidationStatusApiAction extends ReadOnlyApiAction<PxDataValidationForm>
    {
        @Override
        public Object execute(PxDataValidationForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DataValidation validation = DataValidationManager.getValidation(form.getValidationId(), getContainer());
            if (validation != null)
            {
                int jobId = validation.getJobId();
                PipelineStatusFile status = PipelineService.get().getStatusFile(jobId);
                if (status != null)
                {
                    response.put("jobStatus", status.getStatus());
                }

                Status validationStatus = DataValidationManager.getStatus(form.getValidationId(), getContainer(), getUser());
                if (validationStatus != null)
                {
                    if (!validationStatus.getValidation().isComplete())
                    {
                        response.put("validationProgress", validationStatus.toProgressSummaryJSON());
                    }
                    else
                    {
                        response.put("validationStatus", validationStatus.getPxStatus().getLabel());
                    }
                }
            }
            else
            {
                response.put("error", String.format("Data validation for Id:%d was not found in the folder '%s'.", form.getValidationId(), getContainer().getPath()));
            }
            return response;
        }
    }

    public static class PxDataValidationForm extends ExperimentIdForm
    {
        private int _validationId;
        private boolean _forSubmit = true;

        public int getValidationId()
        {
            return _validationId;
        }

        public void setValidationId(int validationId)
        {
            _validationId = validationId;
        }

        public boolean isForSubmit()
        {
            return _forSubmit;
        }

        public void setForSubmit(boolean forSubmit)
        {
            _forSubmit = forSubmit;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ViewPxValidationsAction extends SimpleViewAction<ExperimentIdForm>
    {
        ExperimentAnnotations _experimentAnnotations;
        @Override
        public ModelAndView getView(ExperimentIdForm form, BindException errors)
        {
            _experimentAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (_experimentAnnotations == null)
            {
                return new SimpleErrorView(errors);
            }

            var view = new VBox();
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Data Validation Results");

            if (DataValidationManager.getValidationJobCount(_experimentAnnotations.getId()) == 0)
            {
                boolean hasProteomicData = ExperimentAnnotationsManager.hasProteomicData(_experimentAnnotations, getUser());
                if (hasProteomicData)
                {
                    view.addView(new HtmlView(DIV(
                            DIV("Data for the experiment has not been validated for a ProteomeXchange submission."),
                            getStartDataValidationButton(_experimentAnnotations, getContainer())
                            )));
                }
                else
                {
                    view.addView(new HtmlView(DIV("This experiment does not contain proteomic data.  Data validation for ProteomeXchange is not required.")));
                }
            }
            else
            {
                var latestValidation = DataValidationManager.getLatestValidation(_experimentAnnotations.getId(), getContainer());
                if (latestValidation != null)
                {
                    HtmlView details = getValidationSummary(DataValidationManager.getStatus(latestValidation, getUser()), _experimentAnnotations, true, getContainer(), getUser());
                    view.addView(details);
                }
                else
                {
                    view.addView(new HtmlView(DIV(cl("labkey-error"), "Could not find the latest data validation job")));
                }
                view.addView(new HtmlView(DIV(at(style, "margin:10px;"),
                        getRerunDataValidationButton(_experimentAnnotations, getContainer())
                       )));

                var qSettings = new QuerySettings(getViewContext(), "DataValidation", "DataValidation");
                Sort sort = new Sort();
                sort.appendSortColumn(FieldKey.fromParts("Id"), Sort.SortDirection.DESC, false);
                qSettings.setBaseSort(sort);
                view.addView(new PanoramaPublicSchema(getUser(), getContainer()).createView(getViewContext(), qSettings, errors));
            }

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Data Validation For ProteomeXchange");
        }
    }

    @NotNull
    private static HtmlView getValidationSummary(Status status, ExperimentAnnotations exptAnnotations, boolean displayExperimentTitle, Container container, User user)
    {
        DataValidation validation = status.getValidation();
        boolean outdated = DataValidationManager.isValidationOutdated(validation, exptAnnotations, user);

        if (outdated || validation.getStatus() == null)
        {
            return new HtmlView(DIV(at(style, "background-color: #FFF6D8;margin:2px;font-weight:bold;"),
                                    SPAN(cl("labkey-error"), outdated ?
                                            "The latest validation results are outdated. Please click the button below to re-run validation."
                                            : "Validation is incomplete.")));
        }

        var statusFile = PipelineService.get().getStatusFile(validation.getJobId());
        User createdByUser = UserManager.getUser(validation.getCreatedBy());
        String pxStatus = validation.getStatus() != null ? validation.getStatus().getLabel() : "Incomplete";
        ActionURL validationDetailsUrl = getPxValidationStatusUrl(exptAnnotations.getId(), validation.getId(), container);
        var color = PxStatus.Complete == validation.getStatus() ? "darkgreen" : PxStatus.IncompleteMetadata == validation.getStatus() ? "#ef771a" : "#d70101";
        return new HtmlView(TABLE(cl("lk-fields-table"),
                displayExperimentTitle ? row("Experiment: ", DIV(exptAnnotations.getTitle(), HtmlString.NBSP, new Link.LinkBuilder("View Details")
                        .href(getViewExperimentDetailsURL(exptAnnotations.getId(), container)).build())) : HtmlString.EMPTY_STRING,
                row("Last Validation Date: ", validation.getFormattedDate()),
                createdByUser != null ?
                        row("Created By: ", new Link.LinkBuilder(createdByUser.getDisplayName(user))
                                .href(PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(container, user.getUserId(), null))
                                .clearClasses().build()) :
                        row("Created By: ", "Unknown User " + validation.getCreatedBy()),
                row("ProteomeXchange Status:", SPAN(at(style, "color:" + color), B(pxStatus), HtmlString.NBSP, new Link.LinkBuilder("[Details]").href(validationDetailsUrl).build())),
                row("Validation Log:", statusFile != null ?
                        new Link.LinkBuilder("View log").href(PageFlowUtil.urlProvider(PipelineStatusUrls.class)
                                .urlDetails(container, validation.getJobId())).build()
                        : SPAN("Log file not found for job Id " + validation.getJobId()))
        ));
    }

    @NotNull
    private static DOM.Renderable row(String title, String value)
    {
        return TR(TD(cl("labkey-form-label"),title), TD(value));
    }

    @NotNull
    private static DOM.Renderable row(String title, DOM.Renderable value)
    {
        return TR(TD(cl("labkey-form-label"),title), TD(value));
    }


    // ------------------------------------------------------------------------
    // BEGIN Action for updating a row in the Submission and JournalExperiment tables
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class UpdateSubmissionAction extends AbstractResubmitAction
    {
        private Submission _submission;

        @Override
        Submission getSubmission()
        {
            return _submission;
        }

        @Override
        boolean foundValidSubmissionRequest(PublishExperimentForm form, Errors errors)
        {
            return super.foundValidSubmissionRequest(form, errors) && foundPendingSubmission(errors);
        }

        private boolean foundPendingSubmission(Errors errors)
        {
            // Get the latest submission request that hasn't yet been copied.
            _submission = _journalSubmission.getPendingSubmission();
            if (_submission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a pending submission request for experiment Id " + _experimentAnnotations.getId());
                return false;

            }
            return true;
        }

        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors)
        {
            form.setUpdate(true);
            return super.getView(form, reshow, errors);
        }

        @Override
        void populateForm(PublishExperimentForm form, ExperimentAnnotations exptAnnotations)
        {
            super.populateForm(form, exptAnnotations);
            form.setUpdate(true);
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
            JournalExperiment journalExperiment = _journalSubmission.getJournalExperiment();
            journalExperiment.setJournalId(form.getJournalId());
            setValuesInSubmission(form, _submission);

            try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                if(!journalExperiment.getShortAccessUrl().getShortURL().equalsIgnoreCase(form.getShortAccessUrl()))
                {
                    // Change the short copy URL to match the access URL.
                    assignShortCopyUrl(form);
                    SubmissionManager.updateShortUrls(_experimentAnnotations, _journal, journalExperiment, form.getShortAccessUrl(), form.getShortCopyUrl(), getUser());
                }
                SubmissionManager.updateJournalExperiment(journalExperiment, getUser());
                SubmissionManager.updateSubmission(_submission, getUser());

                // Create notifications
                PanoramaPublicNotification.notifyUpdated(_experimentAnnotations, _journal, journalExperiment, _submission, getUser());

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
    // ------------------------------------------------------------------------
    // END Action for updating a row in the Submission and JournalExperiment tables
    // ------------------------------------------------------------------------

    @RequiresPermission(AdminPermission.class)
    public abstract static class AbstractResubmitAction extends PublishExperimentAction
    {
        protected JournalSubmission _journalSubmission;

        abstract Submission getSubmission();

        boolean validateGetRequest(PublishExperimentForm form, BindException errors)
        {
            return super.validateGetRequest(form, errors) && foundValidSubmissionRequest(form, errors);
        }

        boolean foundValidSubmissionRequest(PublishExperimentForm form, Errors errors)
        {
            _journal = JournalManager.getJournal(form.getJournalId());
            if (_journal == null)
            {
                errors.reject(ERROR_MSG,"Could not find a journal for Id " + form.getJournalId());
                return false;
            }
            _journalSubmission = SubmissionManager.getJournalSubmission(_experimentAnnotations.getId(), _journal.getId(), getContainer());
            if (_journalSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a submission request for experiment Id " + _experimentAnnotations.getId()
                        + " to the journal '" + _journal.getName() + "' in the folder '" + getContainer().getPath() + "'");
                return false;
            }
            return true;
        }

        protected void checkForValidation(ExperimentAnnotations experimentAnnotations, PublishExperimentForm form)
        {
            if (form.getValidationId() == null)
            {
                var validation = DataValidationManager.getLatestValidation(experimentAnnotations.getId(), experimentAnnotations.getContainer());
                if (validation != null)
                {
                    if ((validation.isComplete() && !DataValidationManager.isValidationOutdated(validation, experimentAnnotations, getUser()))
                            || DataValidationManager.isPipelineJobRunning(validation))
                    {
                        // Redirect to validation status page
                        throw new RedirectException(getPxValidationStatusUrl(_experimentAnnotations.getId(),
                                validation.getId(), _experimentAnnotations.getContainer()));
                    }
                    form.setValidationId(validation.getId());
                }
            }
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
            if (_dataValidation != null)
            {
                form.setIncompletePxSubmission(_dataValidation.getStatus().incompleteSubmission());
            }
        }

        void setValuesInSubmission(PublishExperimentForm form, Submission submission)
        {
            submission.setKeepPrivate(form.isKeepPrivate());
            submission.setPxidRequested(form.isGetPxid());
            submission.setIncompletePxSubmission(form.isIncompletePxSubmission());
            submission.setDataLicense(DataLicense.resolveLicense(form.getDataLicense()));
            submission.setLabHeadName(form.getLabHeadName());
            submission.setLabHeadEmail(form.getLabHeadEmail());
            submission.setLabHeadAffiliation(form.getLabHeadAffiliation());
        }

        @Override
        void validateJournal(Errors errors, ExperimentAnnotations experiment, Journal journal)
        {
            Journal oldJournal = JournalManager.getJournal(_journalSubmission.getJournalId());
            if(oldJournal != null && (oldJournal.getId() != journal.getId()))
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

        @Override
        void validateForm(PublishExperimentForm form, Errors errors)
        {
            if (foundValidSubmissionRequest(form, errors))
            {
                super.validateForm(form, errors);;
            }
        }
    }

    // ------------------------------------------------------------------------
    // BEGIN Action for deleting a row in the panoramapublic.Submission table.
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class DeleteSubmissionAction extends ConfirmAction<IdForm>
    {
        private ExperimentAnnotations _experimentAnnotations;
        private Journal _journal;
        private JournalSubmission _journalSubmission;
        private Submission _submission;

        @Override
        public void validateCommand(IdForm form, Errors errors)
        {
            _submission = SubmissionManager.getSubmission(form.getId(), getContainer());
            if (_submission == null)
            {
                errors.reject(ERROR_MSG, "Could not find a submission for Id: " + form.getId() + " in the folder '" + getContainer().getPath() + "'");
                return;
            }
            if (_submission.hasCopy())
            {
                errors.reject(ERROR_MSG, "This submission request has already been copied. It cannot be deleted.");
                return;
            }

            _journalSubmission = SubmissionManager.getJournalSubmission(_submission.getJournalExperimentId(), getContainer());
            if (_journalSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find a JournalExperiment with Id: " + _submission.getJournalExperimentId() + " in the folder '" + getContainer().getPath() + "'");
                return;
            }

            _experimentAnnotations = ExperimentAnnotationsManager.get(_journalSubmission.getExperimentAnnotationsId());
            if (_experimentAnnotations == null)
            {
                errors.reject(ERROR_MSG,"Could not find an experiment with Id: " + _journalSubmission.getExperimentAnnotationsId());
                return;
            }

            ensureCorrectContainer(getContainer(), _experimentAnnotations.getContainer(), getViewContext());

            _journal = JournalManager.getJournal(_journalSubmission.getJournalId());
            if (_journal == null)
            {
                errors.reject(ERROR_MSG, "Could not find a journal with Id: " + _journalSubmission.getJournalId());
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
                PanoramaPublicNotification.notifyDeleted(_experimentAnnotations, _journal, _journalSubmission.getJournalExperiment(), getUser());

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
            return PanoramaPublicController.getViewExperimentDetailsURL(_experimentAnnotations.getId(), getContainer());
        }
    }

    // ------------------------------------------------------------------------
    // END Action for deleting a row in the panoramapublic.Submission table.
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // BEGIN Action for resubmitting an experiment
    // ------------------------------------------------------------------------
    @RequiresPermission(AdminPermission.class)
    public static class ResubmitExperimentAction extends AbstractResubmitAction
    {
        private Submission _lastCopiedSubmission;

        @Override
        Submission getSubmission()
        {
            return _lastCopiedSubmission;
        }

        @Override
        boolean foundValidSubmissionRequest(PublishExperimentForm form, Errors errors)
        {
            return super.foundValidSubmissionRequest(form, errors) && foundLastCopiedSubmission(errors) && canResubmit(form, errors);
        }

        private boolean foundLastCopiedSubmission(Errors errors)
        {
            if (_journalSubmission.hasPendingSubmission())
            {
                errors.reject(ERROR_MSG,"This experiment is already submitted and is pending copy. It cannot be resubmitted.");
                return false;
            }

            _lastCopiedSubmission = _journalSubmission.getLatestCopiedSubmission();
            if (_lastCopiedSubmission == null)
            {
                errors.reject(ERROR_MSG,"Could not find the last copied submission for experiment Id " + _experimentAnnotations.getId());
                return false;
            }
            return true;
        }

        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors)
        {
            form.setResubmit(true);
            return super.getView(form, reshow, errors);
        }

        private boolean canResubmit(PublishExperimentForm form, Errors errors)
        {
            ExperimentAnnotations journalCopy = ExperimentAnnotationsManager.get(_lastCopiedSubmission.getCopiedExperimentId());
            if(journalCopy == null)
            {
                errors.reject(ERROR_MSG,"This experiment does not have an existing copy on " + _journal.getName() + ".  It cannot be resubmitted.");
                return false;
            }
            if (journalCopy.getPxid() != null && !form.isGetPxid())
            {
                errors.reject(ERROR_MSG,"The existing copy on " + _journal.getName() + " was assigned a ProteomeXchange Id." +
                        " The experiment cannot be resubmitted without a ProteomeXchange Id.");
                return false;
            }
            if(journalCopy.isFinal())
            {
                Journal journal = JournalManager.getJournal(_journalSubmission.getJournalId());
                errors.reject(ERROR_MSG,"The experiment cannot be resubmitted. It has been copied to " + journal.getName()
                        + ", and the copy is final. The publication link is " + PageFlowUtil.filter(journalCopy.getPublicationLink()));
                return false;
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
        boolean doUpdates(PublishExperimentForm form, BindException errors)
        {
            try (DbScope.Transaction transaction = PanoramaPublicManager.getSchema().getScope().ensureTransaction())
            {
                Submission submission = createNewSubmission(_journalSubmission.getJournalExperiment(), form);

                // Give the journal permission to copy the folder
                Group journalGroup = org.labkey.api.security.SecurityManager.getGroup(_journal.getLabkeyGroupId());
                JournalManager.addJournalPermissions(_experimentAnnotations, journalGroup, getUser());

                ExperimentAnnotations copiedExpt = ExperimentAnnotationsManager.get(_lastCopiedSubmission.getCopiedExperimentId());
                PanoramaPublicNotification.notifyResubmitted(_experimentAnnotations, _journal, _journalSubmission.getJournalExperiment(), submission, copiedExpt, getUser());

                transaction.commit();
            }

            return true;
        }

        private Submission createNewSubmission(JournalExperiment journalExperiment, PublishExperimentForm form)
        {
            Submission submission = new Submission();
            submission.setJournalExperimentId(journalExperiment.getId());
            setValuesInSubmission(form, submission);
            return SubmissionManager.saveSubmission(submission, getUser());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Resubmit Request");
        }
    }

    // ------------------------------------------------------------------------
    // END Action for resubmitting an experiment
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
    public static class DataValidationCheckAction extends PublishExperimentAction
    {
        @Override
        public ModelAndView getView(PublishExperimentForm form, boolean reshow, BindException errors)
        {
            form.setDoSubfolderCheck(false);
            form.setGetPxid(true);
            return super.getView(form, reshow, errors);
        }

        @Override
        protected void checkIfResubmit(ExperimentAnnotations experimentAnnotations, PublishExperimentForm form, Errors errors)
        {
            return; // For this action we don't need to check if this is a 'resubmit'
        }

        @Override
        protected ModelAndView getMissingMetadataView(ExperimentAnnotations expAnnotations, MissingMetadata missingMedata, boolean pxSubmission, BindException errors)
        {
            return getMissingMetadataView(new MissingMetadataBean(expAnnotations, pxSubmission, missingMedata, false), "Missing Metadata in Experiment", errors);
        }

        @Override
        protected HtmlView getStartValidationView()
        {
            return getStartValidationView(null, false);
        }

        @Override
        protected HtmlView getStartValidationView(DOM.Renderable message)
        {
            return getStartValidationView(message, false);
        }

        @Override
        protected WebPartView getPublishFormView(PublishExperimentForm form, ExperimentAnnotations exptAnnotations, BindException errors)
        {
            // This action does not submit the form. This view will be displayed if the data is
            // 1. Small molecule data (not submitted to PX)
            // 2. OR The data is valid for a ProteomeXchange submission (either "complete" or "...missing data and/or metadata")
            if (ExperimentAnnotationsManager.hasProteomicData(_experimentAnnotations, getUser()))
            {
                return new HtmlView(DIV("Data is valid for a ProteomeXchange submission.",
                        BR(),
                        new Button.ButtonBuilder("View Validation Details")
                                .href(getPxValidationStatusUrlNotForSubmit(_experimentAnnotations.getId(), _dataValidation.getId(), getContainer()))
                                .build())
                );
            }
            else
            {
                return new HtmlView(DIV("This experiment does not have any proteomic data. Data validation for a ProteomeXchange submission is not required."));
            }
        }

        @Override
        public void validateCommand(PublishExperimentForm form, Errors errors)
        {
            return;
        }

        @Override
        public boolean handlePost(PublishExperimentForm form, BindException errors) throws Exception
        {
            errors.reject(ERROR_MSG, "Post method is not allowed for " + getActionName(DataValidationCheckAction.class));
            return false;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Data Validation Check");
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

    // ------------------------------------------------------------------------
    // BEGIN Actions for ProteomeXchange
    // ------------------------------------------------------------------------
    public enum PX_METHOD {GET_ID, VALIDATE, SUBMIT, UPDATE}

    @RequiresPermission(AdminOperationsPermission.class)
    public static class GetPxActionsAction extends FormViewAction<PxActionsForm>
    {
        private ExperimentAnnotations _expAnnot;
        private JournalExperiment _journalExperiment;
        private Submission _submission;
        private Status _validationStatus;
        private String _pxResponse;

        @Override
        public void validateCommand(PxActionsForm form, Errors errors)
        {
            _expAnnot = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if(_expAnnot == null)
            {
               return;
            }

            if(!_expAnnot.isJournalCopy())
            {
                errors.reject(ERROR_MSG, "ProteomeXchange actions can only be executed on a Panorama Public copy.");
                return;
            }

            JournalSubmission journalSubmission = SubmissionManager.getSubmissionForJournalCopy(_expAnnot);
            if (journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Cannot find the submission request for copied experiment ID: " + _expAnnot.getId());
                return;
            }
            _journalExperiment = journalSubmission.getJournalExperiment();
            _submission = journalSubmission.getSubmissionForCopiedExperiment(_expAnnot.getId());
            if (_submission == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a row in the submission table for copied experiment ID: " + _expAnnot.getId());
                return;
            }

            var validation = DataValidationManager.getLatestValidation(_expAnnot.getId(), getContainer());
            if (validation == null)
            {
                errors.reject(ERROR_MSG, "Data Validation for a ProteomeXchange submission was not found for experiment Id " + _expAnnot.getId());
                return;
            }
            if (!validation.isComplete())
            {
                errors.reject(ERROR_MSG, "Data Validation Id " + _expAnnot.getId() + " is incomplete. Please wait for the validation job to finish and try again.");
                return;
            }
            if (DataValidationManager.isValidationOutdated(validation, _expAnnot, getUser()))
            {
                errors.reject(ERROR_MSG, "Data Validation Id " + _expAnnot.getId() + " is outdated. Please run a new validation job.");
                return;
            }
            else if (validation.getStatus().invalidSubmission())
            {
                errors.reject(ERROR_MSG, "Data is not valid for a ProteomeXchange submission.");
                return;
            }
            _validationStatus = DataValidationManager.getStatus(validation, getUser());
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

            _expAnnot = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if(_expAnnot == null)
            {
                return new SimpleErrorView(errors);
            }

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

        private PxXml createPxXml(ExperimentAnnotations expAnnot, JournalExperiment je, Submission submission, Status validationStatus, String pxChanageLog, boolean submittingToPx) throws PxException
        {
            // Generate the PX XML
            int pxVersion = PxXmlManager.getNextVersion(je.getId());
            PxXmlWriter annWriter;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, je, submission, validationStatus);
                pxInfo.setPxChangeLog(pxChanageLog);
                pxInfo.setVersion(pxVersion);
                annWriter = new PxXmlWriter(baos, submittingToPx);
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
            File xml = getLocalFile(getContainer(), "px.xml"); // TODO: Add date time stamp

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
            File xmlFile = writePxXmlFile(createPxXml(_expAnnot, _journalExperiment, _submission, _validationStatus, pxChangeLog, true).getXml());
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
            if (_expAnnot.getPxid() == null)
            {
                errors.reject(ERROR_MSG, "Experiment does not have a PX ID. It cannot be submitted.");
                return;
            }
            PxXml pxXml = createPxXml(_expAnnot, _journalExperiment, _submission, _validationStatus, pxChangeLog, true);
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
            // File.createTempFile()
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
        private final Submission _submission;
        private final Status _validationStatus;
        private String _pxChangeLog;
        private int _version;

        public PxExperimentAnnotations(ExperimentAnnotations experimentAnnotations, JournalExperiment journalExperiment, Submission submission, Status status)
        {
            _experimentAnnotations = experimentAnnotations;
            _journalExperiment = journalExperiment;
            _submission = submission;
            _validationStatus = status;
        }

        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public JournalExperiment getJournalExperiment()
        {
            return _journalExperiment;
        }

        public Submission getSubmission()
        {
            return _submission;
        }

        public Status getValidationStatus()
        {
            return _validationStatus;
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
            ExperimentAnnotations expAnnot = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if(expAnnot == null)
            {
                out.write("Could not find a valid experiment. " + errors.getMessage());
            }

            JournalSubmission js = expAnnot.isJournalCopy() ? SubmissionManager.getSubmissionForJournalCopy(expAnnot)
                    : SubmissionManager.getNewestJournalSubmission(expAnnot);
            if (js == null)
            {
                out.write("Cannot find the submission request for " + (expAnnot.isJournalCopy() ? "copied " : "") + "experiment ID " + expAnnot.getId());
            }

            var dataValidation = DataValidationManager.getLatestValidation(expAnnot.getId(), getContainer());
            if (dataValidation != null)
            {
                if (!dataValidation.isComplete())
                {
                    out.write("Data validation (Id: " + dataValidation.getId() + ") is incomplete. Wait for it to complete and try again.");
                }
                else if (DataValidationManager.isValidationOutdated(dataValidation, expAnnot, getUser()))
                {
                    out.write("The last data validation (Id: " + dataValidation.getId() + ") is outdated.");
                }
                else
                {
                    var status = DataValidationManager.getStatus(dataValidation, getUser());
                    PxXmlWriter annWriter = new PxXmlWriter(out, false);
                    Submission submission = expAnnot.isJournalCopy() ? js.getSubmissionForCopiedExperiment(expAnnot.getId()) : js.getLatestCopiedSubmission();
                    PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, js.getJournalExperiment(), submission, status);
                    pxInfo.setVersion(PxXmlManager.getNextVersion(js.getJournalExperimentId()));
                    pxInfo.setPxChangeLog(form.getChangeLog());
                    annWriter.write(pxInfo);
                }
            }
            else
            {
                out.write("No data validation rows were not found for experiment Id " + expAnnot.getId());
            }
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
            var expAnnot = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (expAnnot == null)
            {
                return new SimpleErrorView(errors);
            }

            JournalSubmission js = expAnnot.isJournalCopy() ? SubmissionManager.getSubmissionForJournalCopy(expAnnot)
                    : SubmissionManager.getNewestJournalSubmission(expAnnot);
            if (js == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a submission request for " + (expAnnot.isJournalCopy() ? " journal copy " : "") + "experiment ID: " + expAnnot.getId());
                return new SimpleErrorView(errors, true);
            }

            var dataValidation = DataValidationManager.getLatestValidation(expAnnot.getId(), getContainer());
            if (dataValidation != null)
            {
                if (!dataValidation.isComplete())
                {
                    return getDataValidationIncompleteView(dataValidation, false, expAnnot, getContainer());
                }
                else if (DataValidationManager.isValidationOutdated(dataValidation, expAnnot, getUser()))
                {
                    return getStartDataValidationView("The last data validation (Id: " + dataValidation.getId() + ") is outdated. Cannot get the PX XML summary.",
                            expAnnot, getContainer());
                }
                var status = DataValidationManager.getStatus(dataValidation, getUser());
                StringBuilder summaryHtml = new StringBuilder();
                PxHtmlWriter writer = new PxHtmlWriter(summaryHtml);
                Submission submission = expAnnot.isJournalCopy() ? js.getSubmissionForCopiedExperiment(expAnnot.getId()) : js.getLatestSubmission();
                if (submission == null)
                {
                    errors.reject(ERROR_MSG, "Could not find a " + (expAnnot.isJournalCopy() ? "copied" : "current") + " submission request for experiment Id: " + expAnnot.getId());
                    return new SimpleErrorView(errors);
                }
                PxExperimentAnnotations pxInfo = new PxExperimentAnnotations(expAnnot, js.getJournalExperiment(), submission, status);
                pxInfo.setPxChangeLog(form.getChangeLog());
                pxInfo.setVersion(PxXmlManager.getNextVersion(js.getJournalExperimentId()));
                writer.write(pxInfo);

                return new HtmlView(HtmlString.unsafe(summaryHtml.toString()));
            }
            else
            {
                return getStartDataValidationView("No data validation rows were not found for experiment Id " + expAnnot.getId() + ". Cannot get the PX XML summary.",
                        expAnnot, getContainer());
            }
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
            if (_journalSubmission == null)
            {
                errors.reject(ERROR_MSG, "Could not find the submission request for copied experiment Id: " + _experimentAnnotations.getId());
                return;
            }

            _submission = _journalSubmission.getSubmissionForCopiedExperiment(_experimentAnnotations.getId());
            if (_submission == null)
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

            if (actionAllowedInLatestCopy())
            {
                JournalSubmission js = SubmissionManager.getSubmissionForJournalCopy(_expAnnot);
                if (js == null)
                {
                    errors.reject(ERROR_MSG, "Cannot find a submission request for copied experiement Id " + _expAnnot.getId());
                    return;
                }
                if (!js.isLatestExperimentCopy(_expAnnot.getId()))
                {
                    errors.reject(ERROR_MSG, "Experiment id " + _expAnnot.getId() + " is not the last copied submission. "
                            + getActionName(this.getClass()) + " is only allowed in the last copy of the submitted data");
                    return;
                }
            }
        }

        protected boolean actionAllowedInLatestCopy()
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
        protected boolean actionAllowedInLatestCopy()
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
        protected boolean actionAllowedInLatestCopy()
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
            if (_journalSubmission == null)
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
            List<PsiInstrumentParser.PsiInstrument> instruments = ExperimentAnnotationsManager.getContainerInstruments(getContainer(), getUser());
            if (instruments.size() > 0)
            {
                view.setInitialValue("instrument", StringUtils.join(instruments.stream().map(i -> i.getName()).collect(Collectors.toList()), ","));
            }
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

            // Published versions, if any
            Integer sourceExperimentId = exptAnnotations.isJournalCopy() ? exptAnnotations.getSourceExperimentId() : exptAnnotations.getId();
            if (sourceExperimentId != null)
            {
                var publishedVersionsView = getPublishedVersionsView(sourceExperimentId);
                if (publishedVersionsView != null)
                {
                    result.addView(publishedVersionsView);
                }
            }

            // If the data has been validated for a ProteomeXchange submission, show the summary of the last validation
            var latestValidation = DataValidationManager.getLatestValidation(exptAnnotations.getId(), getContainer());
            if (latestValidation != null && getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                HtmlView details = getValidationSummary(DataValidationManager.getStatus(latestValidation, getUser()), exptAnnotations, false, getContainer(), getUser());
                VBox view = new VBox(details);
                Link viewAllLink = null;
                if (getViewContext().hasPermission(AdminOperationsPermission.class) && DataValidationManager.getValidationJobCount(exptAnnotations.getId()) > 1)
                {
                    ActionURL url = new ActionURL(ViewPxValidationsAction.class, getContainer()).addParameter("id", exptAnnotations.getId());
                    viewAllLink = new Link.LinkBuilder("View All Validation Jobs").href(url).build();
                }
                view.addView(new HtmlView(DIV(at(style, "margin-top:15px;"),
                        getRerunDataValidationButton(exptAnnotations, getContainer()),
                        viewAllLink != null ? DIV(viewAllLink) : HtmlString.EMPTY_STRING
                        )));

                view.setTitle("Data Validation for ProteomeXchange");
                view.setFrame(WebPartView.FrameType.PORTAL);
                result.addView(view);
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

            // Add a table of spectral libraries, if there are any
            List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(exptAnnotations);
            TargetedMSService tmsSvc = TargetedMSService.get();
            if (runs.stream().anyMatch(run -> tmsSvc.getLibraries(run).size() > 0))
            {
                result.addView(new SpecLibView(getViewContext(), exptAnnotations));
            }

            if (runs.size() > 0)
            {
                // Structural modifications
                List<Long> runIds = runs.stream().map(ITargetedMSRun::getId).collect(Collectors.toList());
                if (ModificationInfoManager.runsHaveStructuralModifications(runIds, getUser(), getContainer()))
                {
                    result.addView(new ModificationsView.StructuralModsView(getViewContext(), exptAnnotations));
                }
                // Isotope modifications
                if (ModificationInfoManager.runsHaveIsotopeModifications(runIds, getUser(), getContainer()))
                {
                    result.addView(new ModificationsView.IsotopeModsView(getViewContext(), exptAnnotations));
                }
            }

            // If this experiment has been submitted show the submission requests
            List<JournalSubmission> jsList = SubmissionManager.getAllJournalSubmissions(exptAnnotations);
            if (jsList.size() > 0 && getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                QuerySettings qSettings = new QuerySettings(getViewContext(), "Submission", "Submission");
                qSettings.setBaseFilter(new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("JournalExperimentId"),
                        jsList.stream().map(js -> js.getJournalExperimentId()).collect(Collectors.toList()))));
                QueryView submissionList = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, errors);
                submissionList.setShowRecordSelectors(false);
                submissionList.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
                submissionList.disableContainerFilterSelection();
                submissionList.setShowExportButtons(false);
                submissionList.setShowPagination(false);
                submissionList.setPrintView(false);
                VBox vBox = new VBox();
                vBox.setTitle("Submission");
                vBox.setFrame(WebPartView.FrameType.PORTAL);
                vBox.addView(submissionList);

                ActionURL url = PanoramaPublicController.getDataValidationCheckUrl(exptAnnotations.getId(), exptAnnotations.getContainer(), true);
                url.addReturnURL(getViewExperimentDetailsURL(exptAnnotations.getId(), exptAnnotations.getContainer()));
                vBox.addView(new HtmlView(DIV(new Link.LinkBuilder("Validate for ProteomeXchange").href(url).build())));

                result.addView(vBox);
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
        private final ExperimentAnnotations _experimentAnnotations;
        private JournalSubmission _lastPublishedRecord;
        private final boolean _fullDetails;
        private boolean _canPublish;
        private String _version;
        private boolean _isCurrentVersion;
        private ActionURL _versionsUrl;
        private ExperimentAnnotations _journalCopy;

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
                _canPublish = c.hasPermission(user, AdminPermission.class) && (ExperimentAnnotationsManager.canSubmitExperiment(_experimentAnnotations, _lastPublishedRecord));
                if (_lastPublishedRecord != null)
                {
                    Submission lastCopiedSubmission = _lastPublishedRecord.getLatestCopiedSubmission();
                    _journalCopy = lastCopiedSubmission != null ? ExperimentAnnotationsManager.get(lastCopiedSubmission.getCopiedExperimentId()) : null;
                }
            }

            if (_experimentAnnotations.isJournalCopy() && _experimentAnnotations.getSourceExperimentId() != null)
            {
                Integer maxVersion = ExperimentAnnotationsManager.getMaxVersionForExperiment(_experimentAnnotations.getSourceExperimentId());
                List<ExperimentAnnotations> publishedVersions = ExperimentAnnotationsManager.getPublishedVersionsOfExperiment(_experimentAnnotations.getSourceExperimentId());
                if (publishedVersions.size() > 1)
                {
                    // Display the version only if there is more than one version of this dataset on Panorama Public
                    _version = _experimentAnnotations.getStringVersion(maxVersion);
                    if (_experimentAnnotations.getDataVersion().equals(maxVersion))
                    {
                        // This is the current version; Display a link to see all published versions
                        _versionsUrl = new ActionURL(PanoramaPublicController.ShowPublishedVersions.class, _experimentAnnotations.getContainer());
                        _versionsUrl.addParameter("id", _experimentAnnotations.getId());
                        _isCurrentVersion = true;
                    }
                    else
                    {
                        // This is not the current version; Display a link to the current version
                        ExperimentAnnotations maxExpt = publishedVersions.stream().filter(e -> e.getDataVersion().equals(maxVersion)).findFirst().orElse(null);
                        _versionsUrl = maxExpt != null ? PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(maxExpt.getContainer()) : null;
                    }
                }
            }
        }
        public ExperimentAnnotations getExperimentAnnotations()
        {
            return _experimentAnnotations;
        }

        public boolean isFullDetails()
        {
            return _fullDetails;
        }

        public boolean isCanPublish()
        {
            return _canPublish;
        }

        public JournalSubmission getLastPublishedRecord()
        {
            return _lastPublishedRecord;
        }

        public boolean hasVersion()
        {
            return !StringUtils.isBlank(_version);
        }

        public String getVersion()
        {
            return _version;
        }

        public boolean hasVersionsLink()
        {
            return _versionsUrl != null;
        }

        public ActionURL getVersionsLink()
        {
            return _versionsUrl;
        }

        public boolean isCurrentVersion()
        {
            return _isCurrentVersion;
        }

        public boolean canAddPublishLink(User user)
        {
           return _experimentAnnotations.getContainer().hasPermission(user, AdminPermission.class)
                && _lastPublishedRecord != null && !_lastPublishedRecord.hasPendingSubmission() // There is no pending submission request
                && _journalCopy != null // There exists a copy of the data on Panorama Public
                && !(_journalCopy.isPublic() && _journalCopy.hasCompletePublicationInfo());  // The copy is not already public with a PubMed Id
        }

        public String getPublishButtonText()
        {
            if (_journalCopy != null)
            {
                return !_journalCopy.isPublic()       ? "Make Public" :
                       !_journalCopy.isPublished()    ? "Add Publication" :
                       !_journalCopy.isPeerReviewed() ? "Edit Publication" :
                       !_journalCopy.hasPubmedId()    ? "Add PubMed ID" : "";
            }
            return "";
        }

        public @Nullable String getLabHeadName()
        {
            var labHeadName = _experimentAnnotations.getLabHeadName(); // User selected as the lab head in the experiment annotations
            if (labHeadName == null)
            {
                // If the lab head does not have an account on the server, the submitter can enter the lab head's name etc.
                // in the submission form. Check if this was the case.
                if (_experimentAnnotations.isJournalCopy())
                {
                    var js  = SubmissionManager.getSubmissionForJournalCopy(_experimentAnnotations);
                    labHeadName = js != null ? js.getLabHeadNameForCopy(_experimentAnnotations.getId()) : null;
                }
                else if (_lastPublishedRecord != null)
                {
                    labHeadName = _lastPublishedRecord.getLabHeadName();
                }
            }
            return labHeadName;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowPublishedVersions extends SimpleViewAction<IdForm>
    {
        @Override
        public ModelAndView getView(final IdForm form, BindException errors)
        {
            ExperimentAnnotations exptAnnotations = ExperimentAnnotationsManager.get(form.getId());
            if (exptAnnotations == null)
            {
                errors.reject(ERROR_MSG, "Could not find experiment annotations with Id " + form.getId());
                return new SimpleErrorView(errors, true);
            }

            // Check container
            ensureCorrectContainer(getContainer(), exptAnnotations.getContainer(), getViewContext());

            if (exptAnnotations.isJournalCopy() && exptAnnotations.getSourceExperimentId() == null)
            {
                errors.reject(ERROR_MSG, "SourceExperimentId was not set on the experiment " + exptAnnotations.getId());
                return new SimpleErrorView(errors, true);
            }

            VBox result = new VBox();
            int sourceExperimentId = exptAnnotations.isJournalCopy() ? exptAnnotations.getSourceExperimentId() : exptAnnotations.getId();
            var publishedVersionsView = getPublishedVersionsView(sourceExperimentId);
            result.addView(publishedVersionsView != null ? publishedVersionsView:
                    new HtmlView(DIV("Did not find any published versions related to the experiment " + exptAnnotations.getId())));
            result.addView(new HtmlView(PageFlowUtil.generateBackButton()));
            result.setFrame(WebPartView.FrameType.PORTAL);
            return result;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Published Versions");
        }
    }

    private @Nullable WebPartView getPublishedVersionsView(int sourceExperimentId)
    {
        List<ExperimentAnnotations> publishedVersions = ExperimentAnnotationsManager.getPublishedVersionsOfExperiment(sourceExperimentId);
        if (publishedVersions.size() > 0)
        {
            QuerySettings qSettings = new QuerySettings(getViewContext(), "PublishedVersions", "ExperimentAnnotations");
            qSettings.setBaseFilter(new SimpleFilter(new SimpleFilter(FieldKey.fromParts("SourceExperimentId"), sourceExperimentId)));

            List<FieldKey> columns = new ArrayList<>();
            columns.addAll(List.of(FieldKey.fromParts("Version"), FieldKey.fromParts("Created"), FieldKey.fromParts("Link"), FieldKey.fromParts("Share")));
            if (publishedVersions.stream().anyMatch(ExperimentAnnotations::isPublished))
            {
                columns.add(FieldKey.fromParts("Citation"));
            }
            qSettings.setFieldKeys(columns);
            // Set the container filter to All Folders since we want to be able to see rows from other other containers that contain
            // copies of the source experiment. This should be OK since we are applying a filter on the SourceExperimentId
            qSettings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());
            QueryView publishedList = new QueryView(new PanoramaPublicSchema(getUser(), getContainer()), qSettings, null);
            publishedList.setShowRecordSelectors(false);
            publishedList.setShowDetailsColumn(false);
            publishedList.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            publishedList.disableContainerFilterSelection();
            publishedList.setShowExportButtons(false);
            publishedList.setShowPagination(false);
            publishedList.setPrintView(false);
            VBox vBox = new VBox();
            vBox.setTitle("Published Versions");
            vBox.setFrame(WebPartView.FrameType.PORTAL);
            vBox.addView(publishedList);
            return vBox;
        }

        return null;
    }

    private static boolean isSupportedSubFolderType(Container container)
    {
        TargetedMSService.FolderType folderType = TargetedMSService.get().getFolderType(container);
        return folderType == Undefined // This is not a TargetedMS folder type; could be a Collaboration folder
                || isSupportedFolderType(folderType); // If the subfolder is a TargetedMS folder it should be
                                                      // one of the supported types.
    }

    private static boolean isSupportedFolderType(TargetedMSService.FolderType folderType)
    {
        return folderType == Experiment || isLibraryFolder(folderType) || folderType == QC;
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
            if (je != null && je.getLatestSubmission().isPxidRequested())
            {
                List<String> missingFields = DataValidationManager.getMissingExperimentMetadataFields(form.getBean());
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
    public static class IncludeSubFoldersInExperimentAction extends FormViewAction<ExperimentForm>
    {
        private ExperimentAnnotations _expAnnot;
        private ActionURL _returnPublishExptUrl;

        public ModelAndView getView(ExperimentForm form, boolean reshow, BindException errors)
        {
            // This action does not support GET requests. We should be here only if there are errors to show
            // We could extend FormHandlerAction instead of FormViewAction but we want a "Back to Folder" button
            // in the errors view instead of the default buttons we get with a SimpleErrorView.
            if (!errors.hasErrors())
            {
                errors.addError(new LabKeyError("This action does not support HTTP GET"));
            }

            if (_expAnnot != null)
            {
                VBox view = new VBox(new SimpleErrorView(errors, false));
                view.addView(new HtmlView(DIV(BR(), BR(), new Button.ButtonBuilder("Back to Folder")
                        .href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_expAnnot.getContainer())))));
                return view;
            }
            else
            {
                return new SimpleErrorView(errors);
            }
        }

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
                return;
            }

            List<Container> unsupportedFolderTypes = allSubfolders.stream().filter(container -> !isSupportedSubFolderType(container)).collect(Collectors.toList());
            if (unsupportedFolderTypes.size() > 0)
            {
                org.labkey.api.util.Path parentPath = _expAnnot.getContainer().getParsedPath();
                errors.reject(ERROR_MSG, "The folder type of the following subfolders is not supported. Only 'Experimental Data' and 'Library' folders can be included in an experiment.");
                unsupportedFolderTypes.stream().forEach(f -> errors.reject(ERROR_MSG, parentPath.relativize(f.getParsedPath()).toString()));
                return;
            }

            ActionURL returnUrl = form.getReturnActionURL();
            if (returnUrl != null)
            {
                String action = returnUrl.getAction();
                if(SpringActionController.getActionName(PublishExperimentAction.class).equals(action) ||
                        SpringActionController.getActionName(ResubmitExperimentAction.class).equals(action) ||
                        SpringActionController.getActionName(UpdateSubmissionAction.class).equals(action))
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

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Error Including Subfolders");
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

    /**
     * Action class for making data submitted to Panorama Public public
     * - A PubMed ID or publication link and citation can be added OR the data can be made public
     *   without any publication information.
     *   If a PubMed ID is provided, the citation in NLM format is determined using NCBI's Literature Citation Exporter.
     * - The DOI associated with the data is made "findable".
     * This action can be invoked by a folder administrator in the submitted folder in a user project.
     */
    @RequiresPermission(AdminPermission.class)
    public static class MakePublicAction extends FormViewAction<PublicationDetailsForm>
    {
        private ExperimentAnnotations _expAnnot;
        private ExperimentAnnotations _copiedExperiment;
        private Journal _journal;
        private JournalSubmission _journalSubmission;
        private DataCiteException _doiError;
        private boolean _madePublic;
        private boolean _addedPublication;

        private boolean validateExperiment(PublicationDetailsForm form, Errors errors)
        {
            _expAnnot = form.lookupExperiment();
            if (_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find an experiment with Id " + form.getId());
                return false;
            }

            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());
            return true;
        }

        private ExperimentAnnotations getCopiedExperimentFor(ExperimentAnnotations expAnnot, Errors errors)
        {
            _journalSubmission = getJournalSubmissionFor(expAnnot, errors);
            if (_journalSubmission != null)
            {
                _journal = JournalManager.getJournal(_journalSubmission.getJournalId());
                if (_journal == null)
                {
                    errors.reject(ERROR_MSG, "Cannot find a journal with Id " + _journalSubmission.getJournalId());
                    return null;
                }

                if (expAnnot.isJournalCopy())
                {
                    if (!_journalSubmission.isLatestExperimentCopy(_expAnnot.getId()))
                    {
                        errors.reject(ERROR_MSG, "Experiment Id " + _expAnnot.getId() + " is not the most recent copy of the data");
                        return null;
                    }
                    return expAnnot;
                }
                else
                {
                    Submission submission = _journalSubmission.getLatestSubmission();
                    ExperimentAnnotations copiedExperiment = submission != null ? ExperimentAnnotationsManager.get(submission.getCopiedExperimentId()) : null;
                    if (copiedExperiment == null)
                    {
                        errors.reject(ERROR_MSG, String.format("Cannot find a copy of experiment Id %d on '%s'", expAnnot.getId(), _journal.getName()));
                        return null;
                    }
                    return copiedExperiment;
                }
            }
            return null;
        }

        private JournalSubmission getJournalSubmissionFor(ExperimentAnnotations expAnnot, Errors errors)
        {
            JournalSubmission js = expAnnot.isJournalCopy() ? SubmissionManager.getSubmissionForJournalCopy(expAnnot)
                    : SubmissionManager.getNewestJournalSubmission(expAnnot);
            if (js == null)
            {
                errors.reject(ERROR_MSG, "Unable to find a submission request for"
                            + (expAnnot.isJournalCopy() ? " copied " : " ") +  "experiment Id " + expAnnot.getId());
            }
            return js;
        }

        @Override
        public ModelAndView getView(PublicationDetailsForm form, boolean reshow, BindException errors)
        {
            if (reshow)
            {
                if (errors.hasErrors())
                {
                    return _copiedExperiment != null ? getPublicationDetailsView(form, errors)
                            : new SimpleErrorView(errors, false);
                }

                return getConfirmView(form, errors);
            }

            if (!validateExperiment(form, errors))
            {
                return new SimpleErrorView(errors, false);
            }

            _copiedExperiment = getCopiedExperimentFor(_expAnnot, errors);
            if (_copiedExperiment == null)
            {
                return new SimpleErrorView(errors);
            }
            if (!canUpdatePublicationDetails(errors))
            {
                return new SimpleErrorView(errors);
            }

            form.setPubmedId(_copiedExperiment.getPubmedId());
            form.setLink(_copiedExperiment.getPublicationLink());
            form.setCitation(_copiedExperiment.getCitation());
            return getPublicationDetailsView(form, errors);
        }

        private ModelAndView getPublicationDetailsView(PublicationDetailsForm form, BindException errors)
        {
            PublicationDetailsBean bean = new PublicationDetailsBean(form, _copiedExperiment);
            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/publicationDetails.jsp", bean, errors);
            view.setTitle("Publication Details");
            return view;
        }

        @NotNull
        private ModelAndView getConfirmView(PublicationDetailsForm form, BindException errors)
        {
            PublicationDetailsBean bean = new PublicationDetailsBean(form, _copiedExperiment);
            JspView view = new JspView("/org/labkey/panoramapublic/view/publish/confirmPublish.jsp", bean, errors);
            view.setTitle("Confirm Publication Details");
            return view;
        }

        @Override
        public void validateCommand(PublicationDetailsForm form, Errors errors)
        {
            if (!validateExperiment(form, errors))
            {
                return;
            }
            _copiedExperiment = getCopiedExperimentFor(_expAnnot, errors);
            if (_copiedExperiment == null)
            {
                return;
            }

            if (!canUpdatePublicationDetails(errors))
            {
                return;
            }

            if (!form.isUnpublished())
            {
                // User did not check the "Unpublished" checkbox so we need the publication details
                if (form.hasPubmedId())
                {
                    if(!form.getPubmedId().matches(PUBMED_ID))
                    {
                        errors.reject(ERROR_MSG, "PubMed ID should be a number with 1 to 8 digits");
                        return;
                    }
                }
                else
                {
                    if (!form.hasLinkAndCitation())
                    {
                        errors.reject(ERROR_MSG, "Please provide either a PubMed ID or both a publication link and citation for the paper");
                        return;
                    }
                    UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
                    if (!urlValidator.isValid(form.getLink()))
                    {
                        errors.reject(ERROR_MSG, "Publication Link is not valid");
                        return;
                    }
                }
                if (!form.hasNewPublicationDetails(_copiedExperiment))
                {
                    errors.reject(ERROR_MSG, String.format("Publication details are the same as the ones associated with the data on %s at %s",
                            _journal.getName(), _copiedExperiment.getShortUrl().renderShortURL()));
                    return;
                }
            }
            else if (_copiedExperiment.isPublic())
            {
                // The "Unpublished" box was checked, but the data on Panorama Public is already public so nothing will change
                // as a result of this action
                errors.reject(ERROR_MSG, String.format("Data on %s at %s is already public",
                        _journal.getName(), _copiedExperiment.getShortUrl().renderShortURL()));
                return;
            }
        }

        private boolean canUpdatePublicationDetails(Errors errors)
        {
            if (_copiedExperiment.isPublic() && _copiedExperiment.hasCompletePublicationInfo())
            {
                errors.reject(ERROR_MSG, String.format("Data on %s at %s is already public and has complete publication details, including a PubMed ID",
                        _journal.getName(), _copiedExperiment.getShortUrl().renderShortURL()));
                return false;
            }
            return true;
        }

        @Override
        public boolean handlePost(PublicationDetailsForm form, BindException errors)
        {
            if (!form.isConfirmed())
            {
                if (form.hasPubmedId())
                {
                    Pair<String, String> linkAndCitation = NcbiUtils.getLinkAndCitation(form.getPubmedId());
                    if (linkAndCitation != null)
                    {
                        form.setLink(linkAndCitation.first);
                        form.setCitation(linkAndCitation.second);
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Unable to get a citation for PubMed ID: " + form.getPubmedId());
                    }
                }
                return false;
            }

            if (!_copiedExperiment.isPublic())
            {
                JournalManager.PublicDataUser publicDataUser = JournalManager.getPublicDataUser(_journal);

                makeFolderPublic(publicDataUser); // Make the folder public

                // If the "Raw Data" tab is displayed, add the data download information webpart
                if (publicDataUser != null)
                {
                    addDownloadDataWebpart(_copiedExperiment.getContainer());
                }

                // Publish the DOI, if one was assigned
                publishDoi();

                _madePublic = true;
            }

            if (!form.isUnpublished())
            {
                // Add the publication link and citation to the copied experiment
                _copiedExperiment.setPublicationLink(form.getLink());
                _copiedExperiment.setCitation(form.getCitation());
                if (form.hasPubmedId())
                {
                    _copiedExperiment.setPubmedId(form.getPubmedId());
                }
                ExperimentAnnotationsManager.save(_copiedExperiment, getUser());

                _addedPublication = true;
            }


            // TODO: announce to ProteomeXchange.  This will be added after updates to PX data validation process


            // Post to the message thread associated with this submission
            PanoramaPublicNotification.notifyDataPublished(_expAnnot, _copiedExperiment, _journal, _journalSubmission.getJournalExperiment(), _doiError, getUser());

            return true;
        }

        private void makeFolderPublic(JournalManager.PublicDataUser publicDataUser)
        {
            Container container = _copiedExperiment.getContainer();
            MutableSecurityPolicy newPolicy = new MutableSecurityPolicy(container, container.getPolicy());
            newPolicy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), ReaderRole.class);
            if (publicDataUser != null)
            {
                newPolicy.addRoleAssignment(publicDataUser.getUser(), ReaderRole.class);
            }
            SecurityPolicyManager.savePolicy(newPolicy);
        }

        private void addDownloadDataWebpart(Container container)
        {
            String webpartName = PanoramaPublicModule.DOWNLOAD_DATA_INFO_WP;
            List<Portal.WebPart> parts = Portal.getParts(container, RAW_FILES_TAB);
            if (parts.size() != 0)
            {
                if (!parts.stream().anyMatch(p -> webpartName.equals(p.getName())))
                {
                    WebPartFactory webPartFactory = Portal.getPortalPart(webpartName);
                    if(webPartFactory != null)
                    {
                        Portal.addPart(container, RAW_FILES_TAB, webPartFactory, WebPartFactory.LOCATION_BODY);
                    }
                }
            }
        }

        private void publishDoi()
        {
            try
            {
                DataCiteService.publishIfDraftDoi(_copiedExperiment);
            }
            catch (DataCiteException e)
            {
                _doiError = e;
            }
        }

        @Override
        public ModelAndView getSuccessView(PublicationDetailsForm form)
        {
            var shortUrl = _copiedExperiment.getShortUrl().renderShortURL();
            var successMsg = _madePublic ? String.format("Data on %s at %s was made public%s",
                                                               _journal.getName(),
                                                               shortUrl,
                                                               (_addedPublication ? " and publication details were added." : "."))
                                   : _addedPublication ?  String.format("Publication details were updated for data on %s at %s.", _journal.getName(), shortUrl) : "";
            Link viewDataLink = new Link.LinkBuilder("View Data")
                    .href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_copiedExperiment.getContainer()))
                    .target("_blank").build();
            Button backToFolderBtn = new Button.ButtonBuilder("Back to Folder")
                    .href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_expAnnot.getContainer())).build();

            return new HtmlView(
                    DIV(successMsg, SPAN(at(style, "margin-left:10px;"), viewDataLink),
                            _copiedExperiment.getPxid() != null ?
                                    DIV(at(style, "margin-top:10px;"),
                                            String.format("Accession %s will be %s on ProteomeXchange by a %s administrator.",
                                                    _copiedExperiment.getPxid(), _madePublic ? "made public" : "updated", _journal.getName()))
                                    : "",
                            DIV(at(style, "margin-top:10px;"), backToFolderBtn)
                    )
            );
        }

        @Override
        public ActionURL getSuccessURL(PublicationDetailsForm form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Publish Data");
        }
    }

    public static class PublicationDetailsBean
    {
        private final PublicationDetailsForm _form;
        private final String _accessUrl;
        private final boolean _isPublic;
        private final boolean _isPeerReviewed;

        public PublicationDetailsBean(PublicationDetailsForm form, ExperimentAnnotations copiedExperiment)
        {
            _form = form;
            _isPublic = copiedExperiment.isPublic();
            _isPeerReviewed = copiedExperiment.isPeerReviewed();
            _accessUrl = copiedExperiment.getShortUrl().renderShortURL();
        }

        public PublicationDetailsForm getForm()
        {
            return _form;
        }

        public boolean isPublic()
        {
            return _isPublic;
        }

        public boolean isPeerReviewed()
        {
            return _isPeerReviewed;
        }

        public String getAccessUrl()
        {
            return _accessUrl;
        }
    }
    public static class PublicationDetailsForm extends ExperimentIdForm
    {
        private String _link;
        private String _citation;
        private String _pubmedId;
        private boolean _unpublished;
        private boolean _confirmed;

        public String getLink()
        {
            return _link;
        }

        public void setLink(String link)
        {
            _link = link;
        }

        public String getCitation()
        {
            return _citation;
        }

        public HtmlString getHtmlCitation()
        {
            return ExperimentAnnotations.getHtmlCitation(_citation);
        }

        public void setCitation(String citation)
        {
            _citation = citation;
        }

        public String getPubmedId()
        {
            return _pubmedId;
        }

        public void setPubmedId(String pubmedId)
        {
            _pubmedId = pubmedId;
        }

        public boolean isUnpublished()
        {
            return _unpublished;
        }

        public void setUnpublished(boolean unpublished)
        {
            _unpublished = unpublished;
        }

        public boolean isConfirmed()
        {
            return _confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            _confirmed = confirmed;
        }

        public boolean hasPubmedId()
        {
            return !StringUtils.isBlank(_pubmedId);
        }

        public boolean hasLinkAndCitation()
        {
            return !(StringUtils.isBlank(_link) || StringUtils.isBlank(_citation));
        }

        public boolean hasNewPublicationDetails(ExperimentAnnotations copiedExperiment)
        {
            return !(Objects.equals(_pubmedId, copiedExperiment.getPubmedId())
                    && Objects.equals(_link, copiedExperiment.getPublicationLink())
                    && Objects.equals(_citation, copiedExperiment.getCitation()));
        }
    }

    public static abstract class PanoramaPublicExperimentAction<T extends ExperimentIdForm> extends FormViewAction<T>
    {
        protected ExperimentAnnotations _expAnnot;

        protected abstract ModelAndView getModelAndView(T form, boolean reshow, BindException errors);
        protected abstract void validatePostCommand(T form, Errors errors);

        private boolean validateExperiment(T form, Errors errors)
        {
            _expAnnot = form.lookupExperiment();
            if (_expAnnot == null)
            {
                errors.reject(ERROR_MSG, "Cannot find an experiment with Id " + form.getId());
                return false;
            }

            ensureCorrectContainer(getContainer(), _expAnnot.getContainer(), getViewContext());
            return true;
        }

        @Override
        public ModelAndView getView(T form, boolean reshow, BindException errors)
        {
            if (!validateExperiment(form, errors))
            {
                return new SimpleErrorView(errors, false);
            }

            return getModelAndView(form, reshow, errors);
        }

        @Override
        public void validateCommand(T form, Errors errors)
        {
            if (!validateExperiment(form, errors))
            {
                return;
            }

            validatePostCommand(form, errors);
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class EditSpecLibInfoAction extends PanoramaPublicExperimentAction<EditSpecLibInfoForm>
    {
        private SpecLibInfo _specLibInfo;
        private SpectralLibrary _spectralLibrary;

        @Override
        public ModelAndView getModelAndView(EditSpecLibInfoForm form, boolean reshow, BindException errors)
        {
            if (!reshow)
            {
                _spectralLibrary = getLibrary(form, errors);
                if (_spectralLibrary != null)
                {
                    form.setSpecLibId(_spectralLibrary.getId());
                }
                _specLibInfo = getSpecLibInfo(form, errors);
                if (errors.hasErrors())
                {
                    return new SimpleErrorView(errors);
                }

                if ( _specLibInfo != null)
                {
                    form.setSpecLibInfoId(_specLibInfo.getId());
                    if (_specLibInfo.getSourceType() != null)
                    {
                        form.setSourceType(_specLibInfo.getSourceType().name());
                    }
                    form.setSourceUrl(_specLibInfo.getSourceUrl());
                    form.setSourceAccession(_specLibInfo.getSourceAccession());
                    form.setSourceUsername(_specLibInfo.getSourceUsername());
                    form.setSourcePassword(_specLibInfo.getSourcePassword());
                    if (_specLibInfo.getDependencyType() != null)
                    {
                        form.setDependencyType(_specLibInfo.getDependencyType().name());
                    }
                }
            }

            if (_spectralLibrary == null)
            {
                return new SimpleErrorView(errors);
            }

            JspView view = new JspView<>("/org/labkey/panoramapublic/view/editSpecLibInfo.jsp", new SpecLibInfoBean(form, _spectralLibrary), errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Edit Spectral Library Information");
            return view;
        }

        private SpecLibInfo getSpecLibInfo(EditSpecLibInfoForm form, Errors errors)
        {
            SpecLibInfo specLibInfo = null;
            if (form.getSpecLibInfoId() != null)
            {
                specLibInfo = SpecLibInfoManager.get(form.getSpecLibInfoId(), getContainer());
                if (specLibInfo == null)
                {
                    errors.reject(ERROR_MSG, "Could not find a row in SpecLibInfo for Id: " + form.getSpecLibInfoId());
                }
            }
            return specLibInfo;
        }

        private SpectralLibrary getLibrary(EditSpecLibInfoForm form, Errors errors)
        {
            var spectralLibrary = SpecLibInfoManager.getSpectralLibrary(form.getSpecLibId(),
                    null, // Do not use the container in the request as the library may be in a subfolder if the
                                  // ExperimentAnnotations is configured to include subfolders.
                    getUser());
            if (spectralLibrary == null)
            {
                errors.reject(ERROR_MSG, "Could not find a spectral library with Id: " + form.getSpecLibId());
                return null;
            }
            var run = spectralLibrary.getRun(getUser());
            if (run == null)
            {
                errors.reject(ERROR_MSG, String.format("Could not find the Skyline document (Id: %d) linked to the spectral library with Id: %d",
                        spectralLibrary.getRunId(), spectralLibrary.getId()));
                return null;
            }
            if (!ExperimentAnnotationsManager.getTargetedMSRuns(_expAnnot).stream().anyMatch(r -> run.getId() == r.getId()))
            {
                errors.reject(ERROR_MSG, String.format("Skyline document (Id: %d) that contains the spectral library (Id: %d) does not belong to the " +
                                "experiment (Id: %d) in the folder '%s'",
                        run.getId(), spectralLibrary.getId(), _expAnnot.getId(), run.getContainer().getPath()));
                return null;
            }
            if (!run.getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                errors.reject(ERROR_MSG, String.format("You do not have permissions to add details for spectral library (Id: %d) in the folder '%s'",
                        spectralLibrary.getId(), run.getContainer().getPath()));
                return null;
            }

            return spectralLibrary;
        }

        @Override
        public void validatePostCommand(EditSpecLibInfoForm form, Errors errors)
        {
            _specLibInfo = getSpecLibInfo(form, errors);
            _spectralLibrary = getLibrary(form, errors);
            if (errors.hasErrors())
            {
                return;
            }

            if (_specLibInfo != null && !_specLibInfo.getLibraryKey().equals(_spectralLibrary.getKey()))
            {
                errors.reject(ERROR_MSG, String.format("Saved library key '%s' does not match the key '%s' for spectral library with Id: %d",
                        _specLibInfo.getLibraryKey(), _spectralLibrary.getKey(), _spectralLibrary.getId()));
                return ;
            }
            if (form.getSourceType() == null)
            {
                errors.reject(ERROR_MSG, "A value for Library Source is required");
            }
            else
            {
                var sourceType = SpecLibSourceType.getForName(form.getSourceType());
                if (sourceType == null)
                {
                    errors.reject(ERROR_MSG, "Invalid value for Library Source: " + form.getSourceType());
                }
                else if (sourceType == SpecLibSourceType.PUBLIC_LIBRARY)
                {
                    var libraryUrl = form.getSourceUrl();
                    if (StringUtils.isBlank(libraryUrl))
                    {
                        errors.reject(ERROR_MSG, "Source URL for a publicly available library cannot be blank");
                    }
                    else
                    {
                        UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
                        if (!urlValidator.isValid(libraryUrl))
                        {
                            errors.reject(ERROR_MSG, "Source URL for library is not valid");
                        }
                    }
                }
                else if (sourceType == SpecLibSourceType.OTHER_REPOSITORY)
                {
                    // We should at least have an accession / identifier
                    var accession = form.getSourceAccession();
                    if (StringUtils.isBlank(accession))
                    {
                        errors.reject(ERROR_MSG, "Accession / identifier of the data in the other repository cannot be blank");
                    }
                    else if (!validAccession(accession))
                    {
                        errors.reject(ERROR_MSG, "Invalid accession. Accepted accessions are ProteomeXchange IDs (e.g. PXD000001)" +
                                ", MassIVE identifiers (e.g. MSV000000001) or PeptideAtlas identifiers (e.g. PASS00001)");
                    }
                    if (!StringUtils.isBlank(form.getSourceUsername()) && StringUtils.isBlank(form.getSourcePassword()))
                    {
                        // Require a password if a username was entered. We do not display the previously saved password in the
                        // form. User has to re-enter it each time they edit the details.
                        errors.reject(ERROR_MSG, "Password to access data in the other repository cannot be blank");
                    }
                }
            }

            if (form.getDependencyType() == null)
            {
                errors.reject(ERROR_MSG, "A value for Dependency Type is required");
            }
            else if (SpecLibDependencyType.getFromName(form.getDependencyType()) == null)
            {
                errors.reject(ERROR_MSG, "Invalid value for Dependency Type: " + form.getDependencyType());
            }
        }

        public static final String PXD = "^PXD[0-9]{6}$";
        private static final String MASSIVE_ID = "^MSV[0-9]{9}$";
        private static final String PEPTIDEATLAS_ID = "^PASS[0-9]{5}$";
        private boolean validAccession(@NotNull String accession)
        {
            return accession.matches(PXD) || accession.matches(MASSIVE_ID) || accession.matches(PEPTIDEATLAS_ID);
        }

        @Override
        public boolean handlePost(EditSpecLibInfoForm form, BindException errors)
        {
            if (errors.getErrorCount() > 0)
            {
                return false;
            }

            if (_specLibInfo != null)
            {
                copyValues(_specLibInfo, form);
                try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
                {
                    _specLibInfo = SpecLibInfoManager.update(_specLibInfo, getUser());
                    DataValidationManager.specLibInfoChanged(_expAnnot, _specLibInfo, getUser());
                    transaction.commit();
                }
            }
            else
            {
                var specLibInfo = new SpecLibInfo();
                specLibInfo.setExperimentAnnotationsId(form.getId());
                specLibInfo.setName(_spectralLibrary.getName());
                specLibInfo.setFileNameHint(_spectralLibrary.getFileNameHint());
                specLibInfo.setSkylineLibraryId(_spectralLibrary.getSkylineLibraryId());
                specLibInfo.setRevision(_spectralLibrary.getRevision());
                specLibInfo.setLibraryType(_spectralLibrary.getLibraryType());
                copyValues(specLibInfo, form);
                try (DbScope.Transaction transaction = PanoramaPublicSchema.getSchema().getScope().ensureTransaction())
                {
                    specLibInfo = SpecLibInfoManager.save(specLibInfo, getUser());
                    DataValidationManager.specLibInfoAdded(_expAnnot, specLibInfo, getUser());
                    transaction.commit();
                }
            }

            return true;
        }

        private void copyValues(SpecLibInfo specLibInfo, EditSpecLibInfoForm form)
        {
            specLibInfo.setSourceType(SpecLibSourceType.getForName(form.getSourceType()));
            specLibInfo.setSourceUrl(form.getSourceUrl());
            specLibInfo.setSourceAccession(form.getSourceAccession());
            specLibInfo.setSourceUsername(form.getSourceUsername());
            specLibInfo.setSourcePassword(form.getSourcePassword());
            specLibInfo.setDependencyType(SpecLibDependencyType.getFromName(form.getDependencyType()));
        }

        @Override
        public URLHelper getSuccessURL(EditSpecLibInfoForm form)
        {
            return form.getReturnActionURL(getViewExperimentDetailsURL(form.getId(), getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Edit Spectral Library Information");
        }
    }

    public static class SpecLibInfoBean
    {
        private final SpectralLibrary _library;
        private final EditSpecLibInfoForm _form;


        public SpecLibInfoBean(EditSpecLibInfoForm form, SpectralLibrary library)
        {
            _library = library;
            _form = form;
        }

        public EditSpecLibInfoForm getForm()
        {
            return _form;
        }

        public SpectralLibrary getLibrary()
        {
            return _library;
        }
    }

    public static class EditSpecLibInfoForm extends ExperimentIdForm
    {
        private long _specLibId; // row id from targetedms.spectrumlibrary table
        private Integer _specLibInfoId; // row id from the panoramapublic.speclibinfo table

        private String _sourceType;
        private String _sourceUrl;
        private String _sourceAccession;
        private String _sourceUsername;
        private String _sourcePassword;

        private String _dependencyType;

        public long getSpecLibId()
        {
            return _specLibId;
        }

        public void setSpecLibId(long specLibId)
        {
            _specLibId = specLibId;
        }

        public Integer getSpecLibInfoId()
        {
            return _specLibInfoId;
        }

        public void setSpecLibInfoId(Integer specLibInfoId)
        {
            _specLibInfoId = specLibInfoId;
        }

        public String getSourceUrl()
        {
            return _sourceUrl;
        }

        public void setSourceUrl(String sourceUrl)
        {
            _sourceUrl = sourceUrl;
        }

        public String getSourceType()
        {
            return _sourceType;
        }

        public void setSourceType(String sourceType)
        {
            _sourceType = sourceType;
        }

        public String getSourceAccession()
        {
            return _sourceAccession;
        }

        public void setSourceAccession(String sourceAccession)
        {
            _sourceAccession = sourceAccession;
        }

        public String getSourceUsername()
        {
            return _sourceUsername;
        }

        public void setSourceUsername(String sourceUsername)
        {
            _sourceUsername = sourceUsername;
        }

        public String getSourcePassword()
        {
            return _sourcePassword;
        }

        public void setSourcePassword(String sourcePassword)
        {
            _sourcePassword = sourcePassword;
        }

        public String getDependencyType()
        {
            return _dependencyType;
        }

        public void setDependencyType(String dependencyType)
        {
            _dependencyType = dependencyType;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class DeleteSpecLibInfoAction extends PanoramaPublicExperimentAction<DeleteSpecLibInfoForm>
    {
        private SpecLibInfo _specLibInfo;

        @Override
        protected ModelAndView getModelAndView(DeleteSpecLibInfoForm form, boolean reshow, BindException errors)
        {
            if (errors.hasErrors())
            {
                return new SimpleErrorView(errors);
            }
            errors.reject(ERROR_MSG, "Action does not support GET requests");
            return new SimpleErrorView(errors);
        }

        @Override
        protected void validatePostCommand(DeleteSpecLibInfoForm form, Errors errors) {}

        @Override
        public boolean handlePost(DeleteSpecLibInfoForm form, BindException errors) throws Exception
        {
            _specLibInfo = SpecLibInfoManager.getSpecLibInfo(form.getSpecLibInfoId());
            if (_specLibInfo == null)
            {
                errors.reject(ERROR_MSG, "Could not find saved spectral library information with Id " + form.getSpecLibInfoId());
                return false;
            }
            if (_specLibInfo.getExperimentAnnotationsId() != form.getId())
            {
                // It is enough to check that the experimentAnnotationsId in the form matches the id saved with the spec lib info.
                // The container check on the ExperimentAnnotations has already happened in the super class - PanoramaPublicExperimentAction.validateCommand()
                errors.reject(ERROR_MSG, String.format("Spectral library information with Id %d does not belong to experiment Id %d ",
                        form.getSpecLibInfoId(), form.getId()));
                return false;
            }

            SpecLibInfoManager.deleteSpecLibInfo(_specLibInfo, _expAnnot, getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(DeleteSpecLibInfoForm form)
        {
            return form.getReturnActionURL(getViewExperimentDetailsURL(form.getId(), getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Delete Spectral Library Information");
        }
    }

    public static class DeleteSpecLibInfoForm extends ExperimentIdForm
    {
        private int _specLibInfoId;

        public int getSpecLibInfoId()
        {
            return _specLibInfoId;
        }

        public void setSpecLibInfoId(int specLibInfoId)
        {
            _specLibInfoId = specLibInfoId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class ViewSpecLibAction extends SimpleViewAction<SpecLibForm>
    {
        @Override
        public ModelAndView getView(SpecLibForm form, BindException errors) throws Exception
        {
            var specLib = SpecLibInfoManager.getSpectralLibrary(form.getSpecLibId(), getContainer(), getUser());
            if (specLib == null)
            {
                errors.reject(ERROR_MSG, String.format("Could not find a spectral library with Id: %d in the folder '%s'",
                        form.getSpecLibId(), getContainer().getPath()));
                return new SimpleErrorView(errors);
            }

            ITargetedMSRun run = specLib.getRun(getUser());
            if (run == null)
            {
                errors.reject(ERROR_MSG, "Could find a Skyline document for Id: " + specLib.getRunId());
                return new SimpleErrorView(errors);
            }

            var expAnnotations = ExperimentAnnotationsManager.getExperimentIncludesContainer(getContainer());

            SpecLibInfo specLibInfo = null;
            if (form.getSpecLibInfoId() != null && expAnnotations != null)
            {
                specLibInfo = SpecLibInfoManager.get(form.getSpecLibInfoId(), expAnnotations.getContainer());
                if (specLibInfo == null)
                {
                    errors.reject(ERROR_MSG, "Could find a SpecLibInfo row for Id: " + form.getSpecLibInfoId());
                    return new SimpleErrorView(errors);
                }
            }

            VBox view = new VBox();
            view.setTitle("Library Details");
            view.addView(getLibrarySummary(form, specLib, run, specLibInfo, expAnnotations));
            view.addView(getSourceFilesView(specLib, run));
            return view;
        }

        private ModelAndView getLibrarySummary(SpecLibForm form, SpectralLibrary library, ITargetedMSRun run,
                                               @Nullable SpecLibInfo specLibInfo, @Nullable ExperimentAnnotations expAnnotations)
        {
            List<DOM.Renderable> rows = new ArrayList<>();
            rows.add(getSummaryRow("Name:", library.getName()));
            rows.add(getSummaryRow("File Name: ", SPAN(library.getFileNameHint(),
                    SPAN(at(style, "margin-left:5px; white-space: nowrap;"), library.getDownloadLink(getUser())))));
            rows.add(getSummaryRow("Skyline Document: ",  new Link.LinkBuilder(run.getFileName())
                    .href(PageFlowUtil.urlProvider(TargetedMSUrls.class).getShowRunUrl(run.getContainer(), run.getId()))
                    .clearClasses().build()));

            if (specLibInfo != null)
            {
                if (specLibInfo.getSourceType() != null)
                {
                    if (SpecLibSourceType.PUBLIC_LIBRARY == specLibInfo.getSourceType())
                    {
                        rows.add(TR(TD(cl("labkey-form-label"), "Public Library: "),
                                TD(A(at(href, specLibInfo.getSourceUrl()), specLibInfo.getSourceUrl()))));
                    }
                    else
                    {
                        rows.add(getSummaryRow("Library Source: ", specLibInfo.getSourceType().getLabel()));
                        if (specLibInfo.getSourceType() == SpecLibSourceType.OTHER_REPOSITORY)
                        {
                            rows.add(getSummaryRow("Repository Accession: ", specLibInfo.getSourceAccession()));
                        }
                    }
                }
                if (specLibInfo.getDependencyType() != null)
                {
                    rows.add(getSummaryRow("Library Dependency: ", specLibInfo.getDependencyType().getLabel()));
                }

            }

            if (form.getAllSpecLibIds() != null)
            {
                Set<Long> ids = Arrays.stream(form.getAllSpecLibIds().split(","))
                        .map(s -> NumberUtils.toLong(s, 0))
                        .filter(l -> l != 0 && library.getId() != l)
                        .collect(Collectors.toSet());
                List<SpectralLibrary> otherLibraries = SpecLibInfoManager.getLibraries(ids, getUser());
                if (otherLibraries.size() > 0)
                {
                    List<DOM.Renderable> otherDocs = new ArrayList<>();
                    for (SpectralLibrary otherLib: otherLibraries)
                    {
                        var params = new HashMap<String, String>();
                        params.put("allSpecLibIds", form.getAllSpecLibIds());
                        if (specLibInfo != null)
                        {
                            params.put("specLibInfoId", String.valueOf(specLibInfo.getId()));
                        }
                        otherDocs.add(DIV(otherLib.getRunLibraryLink(getUser(), params)));
                    }

                    rows.add(TR(TD(cl("labkey-form-label"), String.format("Other Document%s With Library: ", otherDocs.size() > 1 ? "s" : ""),
                            TD(otherDocs))));
                }
            }

            if (expAnnotations != null)
            {
                ActionURL editSpecLibUrl = getEditSpecLibInfoURL(expAnnotations.getId(), library.getId(), form.getSpecLibInfoId(), expAnnotations.getContainer());
                ActionURL viewExperimentUrl = getViewExperimentDetailsURL(expAnnotations.getId(), expAnnotations.getContainer());
                String linkText = specLibInfo != null ? "Edit Library Details" : "Add Library Details";
                rows.add(TR(TD(at(colspan, 2), DIV(at(style, "margin-top: 10px;"),
                        new Button.ButtonBuilder(linkText).href(editSpecLibUrl).build(),
                        HtmlString.NBSP,
                        new Button.ButtonBuilder("Experiment Details").href(viewExperimentUrl).build()))));
            }
            HtmlView view = new HtmlView(TABLE(cl("lk-fields-table"), rows));
            view.setTitle("Summary");
            return view;
        }

        @NotNull
        private DOM.Renderable getSummaryRow(String fieldLabel, Object value)
        {
            return TR(TD(cl("labkey-form-label"), fieldLabel), TD(at(style, "padding:5px;"), value));
        }

        private ModelAndView getSourceFilesView(SpectralLibrary specLib, ITargetedMSRun run)
        {
            VBox view = new VBox();
            view.setTitle("Library Source Files");
            view.setFrame(WebPartView.FrameType.PORTAL);
            Path libPath = specLib.getLibPath(getUser());
            if (libPath == null)
            {
                view.addView(new HtmlView(DIV(cl("labkey-error"), "Library file is missing from the Skyline document")));
            }
            else
            {
                SpecLibReader reader = SpecLibReader.getReader(specLib);
                if (reader == null)
                {
                    view.addView(new HtmlView(DIV(cl("labkey-error"), "Unsupported library. Could not read library source file names.")));
                }
                else
                {
                    try
                    {
                        List<LibSourceFile> libSourceFiles = reader.readLibSourceFiles(run, specLib);
                        if (libSourceFiles == null || libSourceFiles.size() == 0)
                        {
                            view.addView(new HtmlView(DIV(cl("labkey-error"), "No library source file names were found.")));
                        }
                        else
                        {
                            List<String> spectrumFileNames = getSourceFiles(libSourceFiles, LibSourceFile::getSpectrumSourceFile);
                            List<String> idFileNames = getSourceFiles(libSourceFiles, LibSourceFile::getIdFile);
                            view.addView(new HtmlView(DIV(
                                    TABLE(TR(
                                            TD(at(valign, "top", style, "padding:5px;"),
                                                    createTable("Spectrum Files", spectrumFileNames)),
                                            TD(at(valign, "top", style, "padding:5px;"),
                                                    createTable("Search Results Files", idFileNames)))
                                    ))));
                        }
                    }
                    catch (SpecLibReaderException e)
                    {
                        view.addView(new HtmlView(DIV(DIV(cl("labkey-error"), "Error reading library source files."),
                                DIV(ExceptionUtil.renderException(e)))));
                    }
                }
            }
            return view;
        }

        private DOM.Renderable createTable(String title, List<String> fileNames)
        {
            if (fileNames.size() > 0)
            {
                return TABLE(at(border, 1),
                        THEAD(TR(TH(at(style, "padding:5px;"), STRONG(title)))),
                        TBODY(fileNames.stream().map(name -> TR(TD(at(style, "padding:5px;"), name)))));
            }
            return HtmlString.EMPTY_STRING;
        }

        @NotNull
        private List<String> getSourceFiles(List<LibSourceFile> libSourceFiles, Function<LibSourceFile, String> mappingFunction)
        {
            return libSourceFiles.stream().map(mappingFunction).collect(Collectors.toList()).stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Spectral Library Details");
        }
    }

    public static class SpecLibForm
    {
        private long _specLibId; // row id from targetedms.spectrumlibrary table
        private Integer _specLibInfoId; // row id from panoramapublic.speclibinfo table
        private String _allSpecLibIds; // comma separated row ids of all spectral libraries with the same attributes

        public Integer getSpecLibInfoId()
        {
            return _specLibInfoId;
        }

        public void setSpecLibInfoId(Integer specLibInfoId)
        {
            _specLibInfoId = specLibInfoId;
        }

        public long getSpecLibId()
        {
            return _specLibId;
        }

        public void setSpecLibId(long specLibId)
        {
            _specLibId = specLibId;
        }

        public String getAllSpecLibIds()
        {
            return _allSpecLibIds;
        }

        public void setAllSpecLibIds(String allSpecLibIds)
        {
            _allSpecLibIds = allSpecLibIds;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public abstract static class AbstractMatchToUnimodAction<T extends ExperimentModInfo> extends PanoramaPublicExperimentAction<UnimodMatchForm>
    {
        private IModification _modification;

        protected abstract IModification getModification(UnimodMatchForm form, Errors errors);
        protected abstract boolean hasModInfo(UnimodMatchForm form, IModification modification, Errors errors);
        protected abstract ExperimentModificationGetter.PxModification getModificationMatch(IModification modification, UnimodModifications unimodModifications);
        protected abstract T saveModInfo(UnimodMatchForm form, UnimodModification matchedMod);
        protected abstract String getModTypeString();

        @Override
        public ModelAndView getModelAndView(UnimodMatchForm form, boolean reshow, BindException errors)
        {
            if (reshow)
            {
                errors.reject(ERROR_MSG, "An error occurred while saving the Unimod match.");
                return new SimpleErrorView(errors);
            }

            _modification = getValidModification(form, errors);
            if (_modification == null)
            {
                return getErrorsView(errors, _expAnnot, form);
            }

            UnimodModifications uMods = readUnimod(); // Read the Unimod modifications
            if (uMods == null)
            {
                return new SimpleErrorView(errors);
            }

            ExperimentModificationGetter.PxModification matchedMod;
            try
            {
                matchedMod = getModificationMatch(_modification, uMods);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, "Error finding Unimod match. " + e.getMessage());
                return getErrorsView(errors, _expAnnot, form);
            }

            UnimodMatchBean bean = new UnimodMatchBean(form, _modification, matchedMod);
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/unimodMatchInfo.jsp", bean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Unimod Match");
            return view;
        }

        private IModification getValidModification(UnimodMatchForm form, Errors errors)
        {
            IModification mod = getModification(form, errors);

            if (mod != null)
            {
                if (mod.getUnimodId() != null)
                {
                    errors.reject(ERROR_MSG, "Modification already has a Unimod Id");
                    return null;
                }
                if (hasModInfo(form, mod, errors)) // A Unimod match has already been saved for this modification
                {
                    return null;
                }
            }
            return mod;
        }

        @Override
        protected void validatePostCommand(UnimodMatchForm form, Errors errors)
        {
            _modification = getValidModification(form, errors);
            if (_modification == null)
            {
                return;
            }

            if (form.getUnimodId() == null)
            {
                errors.reject(ERROR_MSG, "Request does not contain a Unimod match Id.");
            }
        }

        @Override
        public boolean handlePost(UnimodMatchForm form, BindException errors) throws Exception
        {
            UnimodModifications uMods = readUnimod(); // Read the Unimod modifications
            if (uMods == null) return false;

            UnimodModification matchedMod = uMods.getById(form.getUnimodId());
            if (matchedMod == null)
            {
                errors.reject(ERROR_MSG, "Did not find a Unimod modification for the selected Unimod Id " + form.getUnimodId());
                return false;
            }

            saveModInfo(form, matchedMod);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(UnimodMatchForm form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public ModelAndView getSuccessView(UnimodMatchForm form) throws Exception
        {
            // If there was no return URL in the form, return this view.
            HtmlView hView = getModInfoSavedSuccessView(_modification.getName(), getModTypeString(), _expAnnot);
            hView.setFrame(WebPartView.FrameType.PORTAL);
            return hView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Find Unimod Match");
        }
    }

    @NotNull
    private static HtmlView getErrorsView(BindException errors, ExperimentAnnotations expAnnotations, ReturnUrlForm returnUrlForm)
    {
        String buttonText = returnUrlForm.getCancelActionURL() != null ? "Back" : "Back to Experiment Details";
        ActionURL returnUrl = returnUrlForm.getCancelActionURL();
        if (returnUrl == null)
        {
            returnUrl = getViewExperimentDetailsURL(expAnnotations.getId(), expAnnotations.getContainer());
        }
        return new HtmlView(
                DIV(
                        ERRORS(errors),
                        DIV(at(style, "margin-top:20px;)"), new Button.ButtonBuilder(buttonText).href(returnUrl))
                ));
    }

    @RequiresPermission(UpdatePermission.class)
    public static class MatchToUnimodStructuralAction extends AbstractMatchToUnimodAction<ExperimentStructuralModInfo>
    {
        @Override
        protected IModification getModification(UnimodMatchForm form, Errors errors)
        {
            var modification = TargetedMSService.get().getStructuralModification(form.getModificationId());
            if (modification == null)
            {
                errors.reject(ERROR_MSG, "Could not find a structural modification with Id " + form.getModificationId());
            }
            if (StringUtils.isBlank(modification.getFormula()))
            {
                errors.reject(ERROR_MSG, "Cannot find a Unimod match for a structural modification that does not have a formula");
                return null;
            }
            if (StringUtils.isBlank(modification.getAminoAcid()) && StringUtils.isBlank(modification.getTerminus()))
            {
                errors.reject(ERROR_MSG, "Cannot find a Unimod match for a structural modification that does not have modified amino acids or a modified terminus");
                return null;
            }
            return modification;
        }

        @Override
        protected boolean hasModInfo(UnimodMatchForm form, IModification mod, Errors errors)
        {
            var modInfo = ModificationInfoManager.getStructuralModInfo(mod.getId(), form.getId());
            if (modInfo != null)
            {
                errors.reject(ERROR_MSG, modInfo.isCombinationMod() ?
                        String.format("Structural modification Id %d (%s) is already defined as a combination of Unimod Id %d (%s) and Unimod Id %d (%s)",
                                mod.getId(), mod.getName(), modInfo.getUnimodId(), modInfo.getUnimodName(), modInfo.getUnimodId2(), modInfo.getUnimodName2()) :
                        String.format("Structural modification Id %d (%s) is already assigned the Unimod Id %d (%s).",
                                mod.getId(), mod.getName(), modInfo.getUnimodId(), modInfo.getUnimodName()));
                return true;
            }

            return false;
        }

        @Override
        protected ExperimentModificationGetter.PxModification getModificationMatch(IModification modification, UnimodModifications unimodModifications)
        {
            return ExperimentModificationGetter.getStructuralUnimodMod(modification, unimodModifications);
        }

        @Override
        protected ExperimentStructuralModInfo saveModInfo(UnimodMatchForm form, UnimodModification matchedMod)
        {
            ExperimentStructuralModInfo modInfo = new ExperimentStructuralModInfo();
            modInfo.setExperimentAnnotationsId(form.getId());
            modInfo.setModId(form.getModificationId());
            modInfo.setUnimodId(matchedMod.getId());
            modInfo.setUnimodName(matchedMod.getName());
            return ModificationInfoManager.saveStructuralModInfo(modInfo, _expAnnot, getContainer(), getUser());
        }

        @Override
        protected String getModTypeString()
        {
            return "structural";
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class MatchToUnimodIsotopeAction extends AbstractMatchToUnimodAction<ExperimentModInfo>
    {
        @Override
        protected IModification getModification(UnimodMatchForm form, Errors errors)
        {
            var modification = TargetedMSService.get().getIsotopeModification(form.getModificationId());
            if (modification == null)
            {
                errors.reject(ERROR_MSG, "Could not find an isotope modification with Id " + form.getModificationId());
            }
            if (StringUtils.isBlank(modification.getAminoAcid()))
            {
                errors.reject(ERROR_MSG, "Cannot find a Unimod match for an isotope modification that does not have modified amino acids");
                return null;
            }
            return modification;
        }

        @Override
        protected boolean hasModInfo(UnimodMatchForm form, IModification mod, Errors errors)
        {
            var modInfo = ModificationInfoManager.getIsotopeModInfo(mod.getId(), form.getId());
            if (modInfo != null)
            {
                errors.reject(ERROR_MSG, String.format("Isotope modification Id %d (%s) is already assigned the Unimod Id %d (%s).",
                        mod.getId(), mod.getName(), modInfo.getUnimodId(), modInfo.getUnimodName()));
                return true;
            }
            return false;
        }

        @Override
        protected ExperimentModificationGetter.PxModification getModificationMatch(IModification modification, UnimodModifications unimodModifications)
        {
            return ExperimentModificationGetter.getIsotopicUnimodMod((IModification.IIsotopeModification) modification, unimodModifications);
        }

        @Override
        protected ExperimentModInfo saveModInfo(UnimodMatchForm form, UnimodModification matchedMod)
        {
            var modInfo = new ExperimentIsotopeModInfo();
            modInfo.setExperimentAnnotationsId(form.getId());
            modInfo.setModId(form.getModificationId());
            modInfo.setUnimodId(matchedMod.getId());
            modInfo.setUnimodName(matchedMod.getName());
            return ModificationInfoManager.saveIsotopeModInfo(modInfo, _expAnnot, getContainer(), getUser());
        }

        @Override
        protected String getModTypeString()
        {
            return "isotope";
        }
    }

    public static class UnimodMatchBean
    {
        private final UnimodMatchForm _form;
        private final IModification _modification;
        private final ExperimentModificationGetter.PxModification _matchedMod;

        public UnimodMatchBean(UnimodMatchForm form, IModification modification, ExperimentModificationGetter.PxModification matchedMod)
        {
            _form = form;
            _modification = modification;
            _matchedMod = matchedMod;
        }

        public UnimodMatchForm getForm()
        {
            return _form;
        }

        public IModification getModification()
        {
            return _modification;
        }

        public List<UnimodModification> getUnimodMatches()
        {
            return _matchedMod.hasUnimodId() ? List.of(_matchedMod.getUnimodMatch())
                    : _matchedMod.getPossibleUnimodMatches();
        }

        public String getLabels()
        {
            String labels = "";
            if (isIsotopicMod())
            {
                var iMod = (IModification.IIsotopeModification) _modification;
                if (Boolean.TRUE.equals(iMod.getLabel13C())) labels += "13C ";
                if (Boolean.TRUE.equals(iMod.getLabel15N())) labels += "15N ";
                if (Boolean.TRUE.equals(iMod.getLabel18O())) labels += "18O ";
                if (Boolean.TRUE.equals(iMod.getLabel2H())) labels += "2H ";
            }
            return labels.trim();
        }

        public boolean isIsotopicMod()
        {
            return _modification instanceof IModification.IIsotopeModification;
        }
    }

    public static class UnimodMatchForm extends ExperimentIdForm
    {
        private long _modificationId;
        private Integer _unimodId;

        public long getModificationId()
        {
            return _modificationId;
        }

        public void setModificationId(long modificationId)
        {
            _modificationId = modificationId;
        }

        public Integer getUnimodId()
        {
            return _unimodId;
        }

        public void setUnimodId(Integer unimodId)
        {
            _unimodId = unimodId;
        }
    }

    /*
    Present two options to associate a structural modification with Unimod modifications:
    1. Find a match in Unimod for the modification's formula, modified site(s) and terminus
    2. Create a custom modification that combines two Unimod modifications
     */
    @RequiresPermission(UpdatePermission.class)
    public static class StructuralModToUnimodOptionsAction extends SimpleViewAction<UnimodMatchForm>
    {
        @Override
        public ModelAndView getView(UnimodMatchForm form, BindException errors) throws Exception
        {
            ExperimentAnnotations expAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (expAnnotations == null)
            {
                return new SimpleErrorView(errors);
            }
            var modification = TargetedMSService.get().getStructuralModification(form.getModificationId());
            if (modification == null)
            {
                errors.reject(ERROR_MSG, "Could not find a structural modification with Id " + form.getModificationId());
                return new SimpleErrorView(errors);
            }

            var findMatchUrl = new ActionURL(MatchToUnimodStructuralAction.class, getContainer())
                    .addParameter("id", form.getId())
                    .addParameter("modificationId", form.getModificationId());
            if (form.getReturnActionURL() != null)
            {
                findMatchUrl.addReturnURL(form.getReturnActionURL());
            }
            if (form.getCancelActionURL() != null)
            {
                findMatchUrl.addCancelURL(form.getCancelActionURL());
            }

            var comboModUrl = new ActionURL(DefineCombinationModificationAction.class, getContainer())
                    .addParameter("id", form.getId())
                    .addParameter("modificationId", form.getModificationId());
            if (form.getReturnActionURL() != null)
            {
                comboModUrl.addReturnURL(form.getReturnActionURL());
            }
            if (form.getCancelActionURL() != null)
            {
                comboModUrl.addCancelURL(form.getCancelActionURL());
            }

            var view = new HtmlView(DIV(at(style, "margin:20px;"),
                    DIV(at(style, "margin:15px;"),
                            SPAN(at(style, "margin-right: 10px;"),"Find a"),
                            SPAN(new Button.ButtonBuilder("Unimod Match").href(findMatchUrl).build()),
                            SPAN(at(style, "margin-left: 10px;"), "for modification ", B(modification.getName()))),
                    DIV(at(style, "margin:15px;"), "OR"),
                    DIV(at(style, "margin:15px;"),
                            SPAN(at(style, "margin-right:10px;"), "Define a custom"),
                            SPAN(new Button.ButtonBuilder("Combination Modification").href(comboModUrl).build()))));
            view.setTitle("Unimod Match Options");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Unimod Match Options for Structural Modification");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class DefineCombinationModificationAction extends PanoramaPublicExperimentAction<CombinationModificationFrom>
    {
        private UnimodModifications _unimodModifications;
        private IModification.IStructuralModification _modification;
        private Formula _modFormula;

        @Override
        protected ModelAndView getModelAndView(CombinationModificationFrom form, boolean reshow, BindException errors)
        {
            if (!reshow)
            {
                initModifications(form, errors);
                if (errors.hasErrors())
                {
                    return getErrorsView(errors, _expAnnot, form);
                }
            }

            CombinationModificationBean bean = new CombinationModificationBean(
                    form,
                    _modification,
                    _modFormula,
                    _unimodModifications.getStructuralModifications(),
                    Arrays.stream(ChemElement.values()).map(el -> el.getSymbol()).collect(Collectors.toList())
                    );
            JspView view = new JspView<>("/org/labkey/panoramapublic/view/combinationModInfo.jsp", bean, errors);
            view.setFrame(WebPartView.FrameType.PORTAL);
            view.setTitle("Combination Modification");
            return view;
        }

        private void initModifications(CombinationModificationFrom form, Errors errors)
        {
            _modification = TargetedMSService.get().getStructuralModification(form.getModificationId());
            if (_modification == null)
            {
                errors.reject(ERROR_MSG, "Did not find a structural modification with Id: " + form.getModificationId());
                return;
            }
            if (StringUtils.isBlank(_modification.getFormula()))
            {
                errors.reject(ERROR_MSG, "Modification '" + _modification.getName() + "' does not have a formula. " +
                        "To define a modification as a combination of two Unimod modifications it must have a formula.");
                return;
            }
            if (StringUtils.isBlank(_modification.getAminoAcid()) && StringUtils.isBlank(_modification.getTerminus()))
            {
                errors.reject(ERROR_MSG, "Modification '" + _modification.getName() + "' does not have any modified amino acids or a modified terminus. " +
                        "To define a modification as a combination of two Unimod modifications it must include the amino acid(s) or the terminus where the modification occurs.");
                return;
            }
            List<String> parseErrors = new ArrayList<>();
            _modFormula = Formula.tryParse(_modification.getFormula(), parseErrors);
            if (_modFormula == null)
            {
                // We cannot define a combination modification if the formula could not be parsed.
                errors.reject(ERROR_MSG, "Modification formula '" + _modification.getFormula() + "' could not be parsed.");
                parseErrors.forEach(err -> errors.reject(ERROR_MSG, err));
                return;
            }

            _unimodModifications = readUnimod();
        }

        @Override
        protected void validatePostCommand(CombinationModificationFrom form, Errors errors)
        {
            initModifications(form, errors);

            if (errors.hasErrors())
            {
                return;
            }

            if (form.getUnimodId1() == null || form.getUnimodId2() == null)
            {
                errors.reject(ERROR_MSG, "Please select two Unimod modifications to define a combination modification.");
                return;
            }
        }

        @Override
        public boolean handlePost(CombinationModificationFrom form, BindException errors) throws Exception
        {
            UnimodModification mod1 = _unimodModifications.getById(form.getUnimodId1());
            if (mod1 == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a Unimod modification for Unimod Id: " + form.getUnimodId1());
                return false;
            }

            UnimodModification mod2 = _unimodModifications.getById(form.getUnimodId2());
            if (mod2 == null)
            {
                errors.reject(ERROR_MSG, "Cannot find a Unimod modification for Unimod Id: " + form.getUnimodId2());
                return false;
            }

            Formula combinedFormula = UnimodModification.getCombinedFormula(mod1,mod2);

            Formula diff = _modFormula.subtractFormula(combinedFormula);
            if (!diff.isEmpty())
            {
                errors.reject(ERROR_MSG, "Selected Unimod modification formulas do not add up to the formula of the modification. " +
                        "Combined formula is " + combinedFormula.getFormula() +
                        ". Difference from the modification formula is " + diff.getFormula() + ".");
                return false;
            }

            ExperimentStructuralModInfo modInfo = new ExperimentStructuralModInfo();
            modInfo.setModId(form.getModificationId());
            modInfo.setExperimentAnnotationsId(form.getId());
            modInfo.setUnimodId(mod1.getId());
            modInfo.setUnimodName(mod1.getName());
            modInfo.setUnimodId2(mod2.getId());
            modInfo.setUnimodName2(mod2.getName());
            ModificationInfoManager.saveStructuralModInfo(modInfo, _expAnnot, getContainer(), getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(CombinationModificationFrom form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public ModelAndView getSuccessView(CombinationModificationFrom form) throws Exception
        {
            // If there was no return URL in the form, return this view.
            HtmlView hView = getModInfoSavedSuccessView(_modification.getName(), "combination", _expAnnot);
            hView.setFrame(WebPartView.FrameType.PORTAL);
            return hView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Define Combination Modification");
        }
    }

    public static class CombinationModificationBean
    {
        private final CombinationModificationFrom _form;
        private final IModification.IStructuralModification _modification;
        private final Formula _modFormula;
        private final List<UnimodModification> _unimodModificationList;
        private final List<String> _elementOrder;

        public CombinationModificationBean(CombinationModificationFrom form, IModification.IStructuralModification modification,
                                           Formula modFormula, List<UnimodModification> unimodList, List<String> elementOrder)
        {
            _form = form;
            _modification = modification;
            _modFormula = modFormula;
            _unimodModificationList = unimodList;
            _elementOrder = elementOrder;
        }

        public CombinationModificationFrom getForm()
        {
            return _form;
        }

        public IModification.IStructuralModification getModification()
        {
            return _modification;
        }

        public Formula getModFormula()
        {
            return _modFormula;
        }

        public List<UnimodModification> getUnimodModificationList()
        {
            return _unimodModificationList;
        }

        public List<String> getElementOrder()
        {
            return _elementOrder;
        }
    }

    public static class CombinationModificationFrom extends ExperimentIdForm
    {
        private long _modificationId;
        private Integer _unimodId1;
        private String _unimodName1;
        private Integer _unimodId2;
        private String _unimodName2;

        public long getModificationId()
        {
            return _modificationId;
        }

        public void setModificationId(long modificationId)
        {
            _modificationId = modificationId;
        }

        public Integer getUnimodId1()
        {
            return _unimodId1;
        }

        public void setUnimodId1(Integer unimodId1)
        {
            _unimodId1 = unimodId1;
        }

        public String getUnimodName1()
        {
            return _unimodName1;
        }

        public void setUnimodName1(String unimodName1)
        {
            _unimodName1 = unimodName1;
        }

        public Integer getUnimodId2()
        {
            return _unimodId2;
        }

        public void setUnimodId2(Integer unimodId2)
        {
            _unimodId2 = unimodId2;
        }

        public String getUnimodName2()
        {
            return _unimodName2;
        }

        public void setUnimodName2(String unimodName2)
        {
            _unimodName2 = unimodName2;
        }
    }

    @NotNull
    private static HtmlView getModInfoSavedSuccessView(String modificationName, String modificationType, ExperimentAnnotations expAnnotations)
    {
        return getModInfoChangedSuccessView(modificationName, modificationType, "saved", expAnnotations);
    }

    @NotNull
    private static HtmlView getModInfoChangedSuccessView(String modificationName, String modificationType, String action, ExperimentAnnotations expAnnotations)
    {
        ActionURL exptModsUrl = new ActionURL(ViewExperimentModifications.class, expAnnotations.getContainer()).addParameter("id", expAnnotations.getId());
        HtmlView hView = new HtmlView(DIV(DIV(SPAN("Unimod information for " + (modificationType != null ? modificationType : "") + " modification "),
                SPAN(at(style, "font-weight:bold;"), modificationName),
                SPAN(" was successfully " + action + ".")),
                BR(),
                DIV("View all the structural and isotope modifications in the experiment: ",
                        new Button.ButtonBuilder("View Experiment Modifications").href(exptModsUrl).build()),
                DIV(new Link.LinkBuilder("[View Experiment Details]").href(getViewExperimentDetailsURL(expAnnotations.getId(), expAnnotations.getContainer())).build()
                )));
        return hView;
    }

    private static UnimodModifications readUnimod()
    {
        return UnimodUtil.getUnimod();
    }

    @RequiresPermission(UpdatePermission.class)
    public static abstract class DeleteModInfoAction<T extends ExperimentModInfo> extends PanoramaPublicExperimentAction<DeleteModInfoForm>
    {
        T _modInfo;

        protected abstract T getModInfo(int modInfoId);
        protected abstract void deleteModInfo(T modInfo, ExperimentAnnotations expAnnotations);
        protected abstract IModification getModification();

        @Override
        protected ModelAndView getModelAndView(DeleteModInfoForm form, boolean reshow, BindException errors)
        {
            if (errors.hasErrors())
            {
                return new SimpleErrorView(errors);
            }
            errors.reject(ERROR_MSG, "Action does not support GET requests");
            return new SimpleErrorView(errors);
        }

        @Override
        protected void validatePostCommand(DeleteModInfoForm form, Errors errors) {}

        @Override
        public boolean handlePost(DeleteModInfoForm form, BindException errors) throws Exception
        {
            _modInfo = getModInfo(form.getModInfoId());
            if (_modInfo == null)
            {
                errors.reject(ERROR_MSG, "Could not find saved modification information for Id " + form.getModInfoId());
                return false;
            }
            if (_modInfo.getExperimentAnnotationsId() != form.getId())
            {
                // It is enough to check that the experimentAnnotationsId in the form matches the id saved with the mod info.
                // The container check on the ExperimentAnnotations has already happened in the super class - PanoramaPublicExperimentAction.validateCommand()
                errors.reject(ERROR_MSG, String.format("Modification information with Id %d does not belong to experiment Id %d ",
                        form.getModInfoId(), form.getId()));
                return false;
            }

            deleteModInfo(_modInfo, _expAnnot);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(DeleteModInfoForm form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public ModelAndView getSuccessView(DeleteModInfoForm form) throws Exception
        {
            // If there was no return URL in the form, return this view.
            IModification mod = getModification();
            HtmlView hView = getModInfoChangedSuccessView(mod != null ? mod.getName() : "", null, "deleted", _expAnnot);
            hView.setFrame(WebPartView.FrameType.PORTAL);
            return hView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Delete Modification Information");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class DeleteStructuralModInfoAction extends DeleteModInfoAction<ExperimentStructuralModInfo>
    {
        @Override
        protected ExperimentStructuralModInfo getModInfo(int modInfoId)
        {
            return ModificationInfoManager.getStructuralModInfo(modInfoId);
        }

        @Override
        protected void deleteModInfo(ExperimentStructuralModInfo modInfo, ExperimentAnnotations expAnnotations)
        {
            ModificationInfoManager.deleteStructuralModInfo(modInfo, expAnnotations, getContainer(), getUser());
        }

        @Override
        protected IModification getModification()
        {
            return TargetedMSService.get().getStructuralModification(_modInfo.getModId());
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class DeleteIsotopeModInfoAction extends DeleteModInfoAction<ExperimentIsotopeModInfo>
    {
        @Override
        protected ExperimentIsotopeModInfo getModInfo(int modInfoId)
        {
            return ModificationInfoManager.getIsotopeModInfo(modInfoId);
        }

        @Override
        protected void deleteModInfo(ExperimentIsotopeModInfo modInfo, ExperimentAnnotations expAnnotations)
        {
            ModificationInfoManager.deleteIsotopeModInfo(modInfo, expAnnotations, getContainer(), getUser());
        }

        @Override
        protected IModification getModification()
        {
            return TargetedMSService.get().getIsotopeModification(_modInfo.getModId());
        }
    }

    public static class DeleteModInfoForm extends ExperimentIdForm
    {
        private int _modInfoId;

        public int getModInfoId()
        {
            return _modInfoId;
        }

        public void setModInfoId(int modInfoId)
        {
            _modInfoId = modInfoId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ViewExperimentModifications extends SimpleViewAction<ExperimentIdForm>
    {
        @Override
        public ModelAndView getView(final ExperimentIdForm form, BindException errors)
        {
            ExperimentAnnotations exptAnnotations = getValidExperiment(form, getContainer(), getViewContext(), errors);
            if (exptAnnotations == null)
            {
                return new SimpleErrorView(errors, true);
            }

            TargetedMSService svc = TargetedMSService.get();
            List<ITargetedMSRun> runs = ExperimentAnnotationsManager.getTargetedMSRuns(exptAnnotations);

            VBox result = new VBox();

            result.addView(new HtmlView(DIV(at(style, "margin: 10px 0 20px 0;"),
                    new Button.ButtonBuilder("View Experiment Details").href(getViewExperimentDetailsURL(exptAnnotations.getId(), getContainer())).build()))
            );

            if (hasStructuralMods(svc, runs))
            {
                result.addView(new HtmlView(H3(at(style, "margin-top:10px;"),"Structural Modifications")));
                if (ModificationInfoManager.getStructuralModInfosForExperiment(exptAnnotations.getId(), getContainer()).size() > 0)
                {
                    result.addView(getAssignedUnimodMatchesMessage());
                }
                QueryView strModsView = new ModificationsView.StructuralModsView(getViewContext(), exptAnnotations);
                strModsView.setFrame(WebPartView.FrameType.NONE);
                result.addView(strModsView);
            }
            else
            {
                result.addView(new HtmlView(DIV(at(style, "margin:10px;"),EM("Skyline documents in the experiment do not have any structural modifications"))));
            }

            if (hasIsotopeMods(svc, runs))
            {
                result.addView(new HtmlView(H3(at(style, "margin-top:10px;"),"Isotope Modifications")));
                if (ModificationInfoManager.getIsotopeModInfosForExperiment(exptAnnotations.getId(), getContainer()).size() > 0)
                {
                    result.addView(getAssignedUnimodMatchesMessage());
                }
                QueryView isotopeModsView = new ModificationsView.IsotopeModsView(getViewContext(), exptAnnotations);
                 isotopeModsView.setFrame(WebPartView.FrameType.NONE);
                result.addView(isotopeModsView);
            }
            else
            {
                result.addView(new HtmlView(DIV(at(style, "margin:10px;"),EM("Skyline documents in the experiment do not have any isotope modifications"))));
            }
            result.setFrame(WebPartView.FrameType.PORTAL);
            return result;
        }

        private HtmlView getAssignedUnimodMatchesMessage()
        {
            return new HtmlView(DIV(cl("labkey-error"),
                    EM("Unimod Ids starting with ", STRONG("**"), " in the Unimod Match column were assigned based on the formula, "
                            + "modification site(s) and terminus in the modification definition.")));
        }
        private boolean hasStructuralMods(TargetedMSService svc, List<ITargetedMSRun> runs)
        {
            return runs.stream().anyMatch(run -> svc.getStructuralModificationsUsedInRun(run.getId()).size() > 0);
        }

        private boolean hasIsotopeMods(TargetedMSService svc, List<ITargetedMSRun> runs)
        {
            return runs.stream().anyMatch(run -> svc.getIsotopeModificationsUsedInRun(run.getId()).size() > 0);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Experiment Modifications");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public abstract static class UploadMissingFilesAction extends SimpleViewAction<ExperimentIdForm>
    {
        abstract @Nullable Status getValidationStatus(DataValidation validation, BindException errors);
        abstract @NotNull String getMissingFilesMessage();
        abstract @NotNull String getViewTitle();

        @Override
        public ModelAndView getView(ExperimentIdForm form, BindException errors) throws Exception
        {
            ExperimentAnnotations expAnnotations = form.lookupExperiment();
            if (expAnnotations == null)
            {
                errors.reject(ERROR_MSG, "No experiment found for Id " + form.getId());
                return new SimpleErrorView(errors);
            }

            if (!expAnnotations.getContainer().equals(getContainer()))
            {
                // The user may be uploading files in a sub-folder of the experiment
                ExperimentAnnotations experimentForSubfolder = ExperimentAnnotationsManager.getExperimentIncludesContainer(getContainer());
                if (experimentForSubfolder == null || experimentForSubfolder.getId() != expAnnotations.getId())
                {
                    errors.reject(ERROR_MSG, String.format("Experiment with Id %d does not include folder '%s'.", expAnnotations.getId(), getContainer().getPath()));
                    return new SimpleErrorView(errors);
                }
            }

            DataValidation validation = DataValidationManager.getLatestValidation(expAnnotations.getId(), expAnnotations.getContainer());
            if (validation == null)
            {
                errors.reject(ERROR_MSG, "Could not find the latest validation row for experiment Id " + expAnnotations.getId());
                return new SimpleErrorView(errors);
            }
            Status validationStatus = getValidationStatus(validation, errors);
            if (validationStatus == null)
            {
                return getErrorsView(errors, expAnnotations, form);
            }

            HtmlView htmlView = new HtmlView(DIV(at(style, "margin: 15px 0 15px 0;"),
                    "Please upload the missing files in the files browser below. ",
                    getMissingFilesMessage(),
                    DIV(at(style, "margin: 0px 0 0 0;"), "Click the button to rerun validation after all the files have been uploaded."),
                    DIV(at(style, "margin:15px;"),
                            getRerunDataValidationButton(expAnnotations, expAnnotations.getContainer()),
                            SPAN(at(style, "margin:10x;"), HtmlString.NBSP),
                            new Button.ButtonBuilder("Cancel").href(form.getReturnActionURL(getViewExperimentDetailsURL(expAnnotations.getId(), expAnnotations.getContainer()))))));

            VBox view = new VBox();
            String fileRootString = FileContentService.FILES_LINK + "/" + RAW_FILES_DIR + "/";
            FilesWebPart fwp = new FilesWebPart(getContainer(), null, fileRootString);
            fwp.setFrame(WebPartView.FrameType.NONE);
            fwp.setShowTitle(false);

            view.addView(htmlView);
            view.addView(fwp);
            view.addView(new JspView<>("/org/labkey/panoramapublic/view/publish/pxMissingFiles.jsp",
                                new PxValidationStatusBean(expAnnotations, null, null, validationStatus)));
            view.setTitle(getViewTitle());
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Upload Files");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class UploadSampleFilesAction extends UploadMissingFilesAction
    {
        @Override
        @Nullable Status getValidationStatus(DataValidation validation, BindException errors)
        {
            Status validationStatus = DataValidationManager.getIncompleteSkyDocsInContainer(validation, getContainer(), getUser());
            if (validationStatus.getSkylineDocs().size() == 0)
            {
                errors.reject(ERROR_MSG, "Could not find Skyline documents in the folder with missing sample files.");
                return null;
            }
            return validationStatus;
        }

        @Override
        @NotNull String getMissingFilesMessage()
        {
            return "The table below the files browser lists all the Skyline documents in this folder with missing sample files.";
        }

        @Override
        @NotNull String getViewTitle()
        {
            return "Upload Missing Sample Files";
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class UploadSpecLibSourceFilesAction extends UploadMissingFilesAction
    {
        @Override
        @Nullable Status getValidationStatus(DataValidation validation, BindException errors)
        {
            Status validationStatus = DataValidationManager.getIncompleteSpecLibs(validation, getUser());
            if (validationStatus.getSpectralLibraries().size() == 0)
            {
                errors.reject(ERROR_MSG, "Could not find spectral libraries in the experiment with missing source files.");
                return null;
            }
            return validationStatus;
        }

        @Override
        @NotNull String getMissingFilesMessage()
        {
            return "The table below the files browser lists all the spectral libraries with missing source files.";
        }

        @Override
        @NotNull String getViewTitle()
        {
            return "Upload Missing Spectral Library Source Files";
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

    public static ActionURL getViewExperimentModificationsURL(int experimentAnnotationsId, Container container)
    {
        ActionURL result = new ActionURL(PanoramaPublicController.ViewExperimentModifications.class, container);
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

    public static ActionURL getSubmitExperimentURL(int experimentAnnotationsId, Container container, boolean keepPrivate, boolean getPxId)
    {
        ActionURL result = new ActionURL(PublishExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getSubmitExperimentURL(int experimentAnnotationsId, Container container)
    {
        return getSubmitExperimentURL(experimentAnnotationsId, container, true, true);
    }

    public static ActionURL getUpdateSubmissionURL(int experimentAnnotationsId, int journalId, Container container, boolean keepPrivate, boolean getPxId)
    {
        ActionURL result = new ActionURL(UpdateSubmissionAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getDataValidationCheckUrl(int experimentAnnotationsId, Container container, boolean notSubmitting)
    {
        ActionURL result = new ActionURL(DataValidationCheckAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("notSubmitting", notSubmitting);
        return result;
    }

    public static ActionURL getResubmitExperimentURL(int experimentAnnotationsId, int journalId, Container container, boolean keepPrivate , boolean getPxId)
    {
        ActionURL result = new ActionURL(ResubmitExperimentAction.class, container);
        result.addParameter("id", experimentAnnotationsId);
        result.addParameter("journalId", journalId);
        result.addParameter("keepPrivate", keepPrivate);
        result.addParameter("getPxid", getPxId);
        return result;
    }

    public static ActionURL getEditSpecLibInfoURL(int experimentAnnotationsId, long specLibId, @Nullable Integer specLibInfoId, Container container)
    {
        ActionURL editUrl = new ActionURL(PanoramaPublicController.EditSpecLibInfoAction.class, container);
        editUrl.addParameter("id", experimentAnnotationsId);
        editUrl.addParameter("specLibId", specLibId);
        if (specLibInfoId != null)
        {
            editUrl.addParameter("specLibInfoId", specLibInfoId);
        }
        return editUrl;
    }

    public static ActionURL getDeleteSpecLibInfoURL(int experimentAnnotationsId, int specLibInfoId, Container container)
    {
        ActionURL deleteUrl = new ActionURL(PanoramaPublicController.DeleteSpecLibInfoAction.class, container);
        deleteUrl.addParameter("id", experimentAnnotationsId);
        deleteUrl.addParameter("specLibInfoId", specLibInfoId);
        return deleteUrl;
    }

    public static ActionURL getSubmitPxValidationJobUrl(ExperimentAnnotations exptAnnotations, Container container)
    {
        return new ActionURL(SubmitPxValidationJobAction.class, container).addParameter("id", exptAnnotations.getId());
    }

    public static ActionURL getPxValidationStatusUrl(int experimentAnnotationsId, int validationId, Container container)
    {
        return getPxValidationStatusUrl(experimentAnnotationsId, validationId, container, true);
    }

    public static ActionURL getPxValidationStatusUrlNotForSubmit(int experimentAnnotationsId, int validationId, Container container)
    {
        return getPxValidationStatusUrl(experimentAnnotationsId, validationId, container, false);
    }

    private static ActionURL getPxValidationStatusUrl(int experimentAnnotationsId, int validationId, Container container, boolean forSubmit)
    {
        ActionURL url = new ActionURL(PxValidationStatusAction.class, container);
        url.addParameter("id", experimentAnnotationsId);
        url.addParameter("validationId", validationId);
        if (!forSubmit)
        {
            url.addParameter("forSubmit", false);
        }
        return url;
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
                new UpdateSubmissionAction(),
                new DeleteSubmissionAction(),
                new ResubmitExperimentAction(),
                new PxXmlSummaryAction(),
                new DataValidationCheckAction()
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
