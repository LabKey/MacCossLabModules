package org.labkey.nextflow;

import org.apache.logging.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

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
}
