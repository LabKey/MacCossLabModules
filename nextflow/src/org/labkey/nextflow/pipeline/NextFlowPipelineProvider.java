package org.labkey.nextflow.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.nextflow.NextFlowManager;
import org.labkey.nextflow.NextFlowModule;

public class NextFlowPipelineProvider extends PipelineProvider
{
    public NextFlowPipelineProvider(NextFlowModule owningModule)
    {
        super("NextFlow", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;
        if (!NextFlowManager.get().isEnabled(context.getContainer()))
            return;
    }


}
