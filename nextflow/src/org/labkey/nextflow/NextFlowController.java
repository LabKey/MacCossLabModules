package org.labkey.nextflow;

import org.apache.logging.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.nextflow.pipeline.NextFlowPipelineJob;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.LK.FORM;
import static org.labkey.api.util.DOM.at;

public class NextFlowController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(NextFlowController.class);
    public static final String NAME = "nextflow";

    private static final Logger LOG = LogHelper.getLogger(NextFlowController.class, NAME);

    public NextFlowController()
    {
        setActionResolver(_actionResolver);
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
        public ModelAndView getView(NextFlowConfiguration nextFlowConfiguration, boolean reshow, BindException errors) throws Exception
        {
            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule(NextFlowModule.class), "nextFlowConfiguration");
        }

        @Override
        public boolean handlePost(NextFlowConfiguration nextFlowConfiguration, BindException errors) throws Exception
        {
            NextFlowManager.get().addConfiguration(nextFlowConfiguration, errors);
            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(NextFlowConfiguration nextFlowConfiguration)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {

        }
    }

    public static class NextFlowConfiguration
    {
        private String nextFlowConfigFilePath;
        private String accountName;
        private String identity;
        private String s3BucketPath;
        private String credential;

        public String getNextFlowConfigFilePath()
        {
            return nextFlowConfigFilePath;
        }

        public void setNextFlowConfigFilePath(String nextFlowConfigFilePath)
        {
            this.nextFlowConfigFilePath = nextFlowConfigFilePath;
        }

        public String getAccountName()
        {
            return accountName;
        }

        public void setAccountName(String accountName)
        {
            this.accountName = accountName;
        }

        public String getIdentity()
        {
            return identity;
        }

        public void setIdentity(String identity)
        {
            this.identity = identity;
        }

        public String getS3BucketPath()
        {
            return s3BucketPath;
        }

        public void setS3BucketPath(String s3BucketPath)
        {
            this.s3BucketPath = s3BucketPath;
        }

        public String getCredential()
        {
            return credential;
        }

        public void setCredential(String credential)
        {
            this.credential = credential;
        }
    }

    @RequiresPermission(SiteAdminPermission.class)
    public static class NextFlowEnableAction extends FormViewAction<NextFlowEnableForm>
    {
        @Override
        public void validateCommand(NextFlowEnableForm target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(NextFlowEnableForm form, boolean reshow, BindException errors) throws Exception
        {
            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule(NextFlowModule.class), "nextFlowEnable");
        }

        @Override
        public boolean handlePost(NextFlowEnableForm form, BindException errors) throws Exception
        {
            Module module = ModuleLoader.getInstance().getModule("nextflow");
            if (form.isEnable())
            {
                getContainer().setActiveModules(Set.of(module), getUser());
            }
            else
            {
                Set<Module> activeModules = new HashSet<>(getContainer().getActiveModules());
                activeModules.remove(module);
                getContainer().setActiveModules(activeModules, getUser());
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(NextFlowEnableForm form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {

        }
    }

    public static class NextFlowEnableForm
    {
        private boolean enable;

        public boolean isEnable()
        {
            return enable;
        }

        public void setEnable(boolean enable)
        {
            this.enable = enable;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class NextFlowRunAction extends FormViewAction
    {
        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(Object o, boolean b, BindException errors) throws Exception
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
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree navTree)
        {

        }
    }
}
