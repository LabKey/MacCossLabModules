package org.labkey.nextflow;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.DOM;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.Select;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.nextflow.pipeline.NextFlowPipelineJob;
import org.labkey.nextflow.pipeline.NextFlowProtocol;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.labkey.api.util.DOM.Attribute.checked;
import static org.labkey.api.util.DOM.Attribute.hidden;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.at;
import static org.labkey.nextflow.NextFlowManager.NEXTFLOW_CONFIG;

public class NextFlowController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(NextFlowController.class);
    public static final String NAME = "nextflow";

    protected static final Logger LOG = LogHelper.getLogger(NextFlowPipelineJob.class, "LabKey UI and API for NextFlow usage");

    public NextFlowController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(SiteAdminPermission.class)
    public static class DeleteNextFlowConfigurationAction extends MutatingApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            if (!getContainer().isRoot())
            {
                throw new UnauthorizedException();
            }
            PropertyStore store = PropertyManager.getEncryptedStore();
            store.deletePropertySet(NEXTFLOW_CONFIG);
            return new ApiSimpleResponse("success", true);
        }
    }


    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public static class NextFlowConfigurationAction extends FormViewAction<NextFlowConfiguration>
    {

        @Override
        public void validateCommand(NextFlowConfiguration target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(NextFlowConfiguration newConfig, boolean reshow, BindException errors)
        {
            NextFlowConfiguration existingConfig = NextFlowManager.get().getConfiguration();
            if (existingConfig != null)
            {
                if (StringUtils.isEmpty(newConfig.getNextFlowConfigFilePath()))
                {
                    newConfig.setNextFlowConfigFilePath(existingConfig.getNextFlowConfigFilePath());
                }
                if (StringUtils.isEmpty(newConfig.getAccountName()))
                {
                    newConfig.setAccountName(existingConfig.getAccountName());
                }
                if (StringUtils.isEmpty(newConfig.getIdentity()))
                {
                    newConfig.setIdentity(existingConfig.getIdentity());
                }
                if (StringUtils.isEmpty(newConfig.getCredential()))
                {
                    newConfig.setCredential(existingConfig.getCredential());
                }
                if (StringUtils.isEmpty(newConfig.getS3BucketPath()))
                {
                    newConfig.setS3BucketPath(existingConfig.getS3BucketPath());
                }
                if (StringUtils.isEmpty(newConfig.getApiKey()))
                {
                    newConfig.setApiKey(existingConfig.getApiKey());
                }
            }

            return new JspView<>("/org/labkey/nextflow/nextFlowConfiguration.jsp", newConfig, errors);
        }

        @Override
        public boolean handlePost(NextFlowConfiguration newConfig, BindException errors)
        {
            NextFlowConfiguration existingConfig = NextFlowManager.get().getConfiguration();
            if (existingConfig != null)
            {
                if (StringUtils.isEmpty(newConfig.getApiKey()))
                {
                    newConfig.setApiKey(existingConfig.getApiKey());
                }
                if (StringUtils.isEmpty(newConfig.getCredential()))
                {
                    newConfig.setCredential(existingConfig.getCredential());
                }
            }
            NextFlowManager.get().saveConfig(newConfig, errors);
            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(NextFlowConfiguration nextFlowConfiguration)
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());
            root.addChild("Configure NextFlow");
        }
    }

    public static class EnabledForm
    {
        Boolean _enabled;

        public Boolean getEnabled()
        {
            return _enabled;
        }

        public void setEnabled(Boolean enabled)
        {
            _enabled = enabled;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends FormViewAction<EnabledForm>
    {
        @Override
        public void validateCommand(EnabledForm target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(EnabledForm form, boolean reshow, BindException errors)
        {
            if (getUser().hasSiteAdminPermission())
            {
                Boolean status = NextFlowManager.get().getEnabledState(getContainer());

                return new HtmlView("Enable or Disable NextFlow",
                        FORM(at(method, "POST"),
                                DIV(INPUT(at(type, "radio", name, "enabled", value, Boolean.TRUE.toString(), (status == Boolean.TRUE ? checked : null), null)),
                                        "Enabled"),
                                DIV(INPUT(at(type, "radio", name, "enabled", value, Boolean.FALSE.toString(), (status == Boolean.FALSE ? checked : null), null)),
                                        "Disabled"),
                                DIV(INPUT(at(type, "radio", name, "enabled", value, "", (status == null ? checked : null), null)),
                                        getContainer().isRoot() ?
                                                "Unset" :
                                                "Inherited from " + getContainer().getParent().getPath() + " (currently " + (NextFlowManager.get().isEnabled(getContainer().getParent()) ? "enabled" : "disabled") + ")"),
                                new Button.ButtonBuilder("Save").submit(true).build(), " ",
                                new Button.ButtonBuilder("Cancel").href(getContainer().getStartURL(getUser())).build()));
            }
            else
            {
                return new HtmlView("NextFlow Integration Status",
                    DIV("NextFlow integration is " + (NextFlowManager.get().isEnabled(getContainer()) ? "enabled" : "disabled") + " in this " + (getContainer().isProject() ? "project" : "folder") + ".")
                );
            }
        }

        @Override
        public boolean handlePost(EnabledForm form, BindException errors)
        {
            NextFlowManager.get().saveEnabledState(getContainer(), form.getEnabled());
            return true;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("NextFlow Integration Status");
        }

        @Override
        public URLHelper getSuccessURL(EnabledForm o)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @Getter @Setter
    public static class AnalyzeForm extends PipelinePathForm
    {
        private boolean launch = false;
        private String configFile;
    }

    @RequiresPermission(InsertPermission.class)
    public class NextFlowRunAction extends FormViewAction<AnalyzeForm>
    {
        @Override
        public void validateCommand(AnalyzeForm o, Errors errors)
        {
            if (!NextFlowManager.get().isEnabled(getContainer()))
            {
                errors.reject(ERROR_MSG, "NextFlow is not enabled");
            }
        }

        @Override
        public ModelAndView getView(AnalyzeForm o, boolean b, BindException errors)
        {
            List<File> selectedFiles = o.getValidatedFiles(getContainer(), false);
            if (selectedFiles.isEmpty())
            {
                return new HtmlView(HtmlString.of("Couldn't find input file(s)"));
            }
            // NextFlow operates on the full directory so show the list to the user, regardless of what they selected
            // from the file listing
            File inputDir = selectedFiles.get(0).getParentFile();

            File[] inputFiles = inputDir.listFiles(new PipelineProvider.FileTypesEntryFilter(NextFlowProtocol.INPUT_TYPES));
            if (inputFiles == null || inputFiles.length == 0)
            {
                return new HtmlView(HtmlString.of("Couldn't find input file(s)"));
            }

            NextFlowConfiguration config = NextFlowManager.get().getConfiguration();
            if (config.getNextFlowConfigFilePath() != null)
            {
                File configDir = new File(config.getNextFlowConfigFilePath());
                if (configDir.isDirectory())
                {
                    File[] configFiles = configDir.listFiles();
                    if (configFiles != null && configFiles.length > 0)
                    {
                        return new HtmlView("NextFlow Runner", DIV(
                                FORM(at(method, "POST"),
                                        INPUT(at(hidden, true, name, "launch", value, true)),
                                        Arrays.stream(o.getFile()).map(f -> INPUT(at(hidden, true, name, "file", value, f))).toList(),
                                        "Files: ",
                                        UL(Arrays.stream(inputFiles).map(File::getName).map(DOM::LI)),
                                        "Config: ",
                                        new Select.SelectBuilder().name("configFile").addOptions(Arrays.stream(configFiles).filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".config")).map(File::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList()).build(),
                                        DOM.BR(),
                                        new Button.ButtonBuilder("Start NextFlow").submit(true).build())));
                    }
                }
            }
            return new HtmlView(HtmlString.of("Couldn't find NextFlow config file(s)"));
        }

        @Override
        public boolean handlePost(AnalyzeForm form, BindException errors) throws Exception
        {
            if (!form.isLaunch())
            {
                return false;
            }

            NextFlowConfiguration config = NextFlowManager.get().getConfiguration();
            File configDir = new File(config.getNextFlowConfigFilePath());
            File configFile = FileUtil.appendPath(configDir, Path.parse(form.getConfigFile()));
            if (!configFile.exists())
            {
                errors.reject(ERROR_MSG, "Config file does not exist");
            }
            else
            {
                List<File> inputFiles = form.getValidatedFiles(getContainer());
                if (inputFiles.isEmpty())
                {
                    errors.reject(ERROR_MSG, "No input files");
                }
                else
                {
                    ViewBackgroundInfo info = getViewBackgroundInfo();
                    PipeRoot root = PipelineService.get().findPipelineRoot(info.getContainer());
                    NextFlowPipelineJob job = NextFlowPipelineJob.create(info, root, configFile.toPath(), inputFiles.stream().map(File::toPath).toList());
                    PipelineService.get().queueJob(job);
                    LOG.info("NextFlow job queued: {}", job.getJsonJobInfo(false));
                }
            }

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(AnalyzeForm o)
        {
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }

        @Override
        public void addNavTrail(NavTree navTree)
        {
            navTree.addChild("NextFlow Runner");
        }
    }
}
