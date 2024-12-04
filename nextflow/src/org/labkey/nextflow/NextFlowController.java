package org.labkey.nextflow;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.nextflow.pipeline.NextFlowPipelineJob;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.util.DOM.Attribute.checked;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.at;
import static org.labkey.nextflow.NextFlowManager.NEXTFLOW_CONFIG;

public class NextFlowController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(NextFlowController.class);
    public static final String NAME = "nextflow";

    private static final Logger LOG = LogHelper.getLogger(NextFlowController.class, NAME);

    public NextFlowController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            boolean enabled = NextFlowManager.get().isEnabled(getContainer());
            return new HtmlView("NextFlow",
                    DIV(
                        DIV("NextFlow integration is " + (enabled ? "enabled" : "disabled") + " in this " + (getContainer().isProject() ? "project" : "folder") + "."),
                        DIV(
                                getContainer().hasPermission(getUser(), SiteAdminPermission.class) ?
                                new Button.ButtonBuilder("Enable/Disable").href(new ActionURL(NextFlowEnableAction.class, getContainer())).build() : null,
                            " ",
                            enabled && getContainer().hasPermission(getUser(), InsertPermission.class) ?
                                    new Button.ButtonBuilder("Run NextFlow Analysis").href(new ActionURL(NextFlowRunAction.class, getContainer())).build() : null)));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("NextFlow");
        }
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

    @RequiresPermission(SiteAdminPermission.class)
    public static class NextFlowEnableAction extends FormViewAction<EnabledForm>
    {
        @Override
        public void validateCommand(EnabledForm target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(EnabledForm form, boolean reshow, BindException errors)
        {
            Boolean status = NextFlowManager.get().getEnabledState(getContainer());
            boolean inheritedStatus = NextFlowManager.get().isEnabled(getContainer().getParent());

            return new HtmlView("Enable/Disable NextFlow",
                    FORM(at(method, "POST"),
                        DIV(INPUT(at(type, "radio", name, "enabled", value, Boolean.TRUE.toString(), (status == Boolean.TRUE ? checked : null), null)),
                            "Enabled"),
                        DIV(INPUT(at(type, "radio", name, "enabled", value, Boolean.FALSE.toString(), (status == Boolean.FALSE ? checked : null), null)),
                            "Disabled"),
                            DIV(INPUT(at(type, "radio", name, "enabled", value, "", (status == null ? checked : null), null)),
                                    getContainer().isRoot() ?
                                            "Unset" :
                                            "Inherited from " + getContainer().getParent().getPath() + " (currently " + (inheritedStatus ? "enabled" : "disabled") + ")"),
                        new Button.ButtonBuilder("Save").submit(true).build(), " ",
                        new Button.ButtonBuilder("Cancel").href(getContainer().getStartURL(getUser())).build()));
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
            root.addChild("Enable/Disable NextFlow");
        }

        @Override
        public URLHelper getSuccessURL(EnabledForm o)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class NextFlowRunAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object o, Errors errors)
        {
            if (!NextFlowManager.get().isEnabled(getContainer()))
            {
                errors.reject(ERROR_MSG, "NextFlow is not enabled");
            }
        }

        @Override
        public ModelAndView getView(Object o, boolean b, BindException errors)
        {
            return new HtmlView("NextFlow Runner", DIV("Run NextFlow Pipeline",
                    FORM(at(method, "POST"),
                            new Button.ButtonBuilder("Start NextFlow").submit(true).build())));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            ViewBackgroundInfo info = getViewBackgroundInfo();
            PipeRoot root = PipelineService.get().findPipelineRoot(info.getContainer());
            PipelineJob job = new NextFlowPipelineJob(info, root);
            PipelineService.get().queueJob(job);

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(Object o)
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
