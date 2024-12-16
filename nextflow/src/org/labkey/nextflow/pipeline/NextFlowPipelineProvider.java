package org.labkey.nextflow.pipeline;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.nextflow.NextFlowController;
import org.labkey.nextflow.NextFlowManager;
import org.labkey.nextflow.NextFlowModule;

public class NextFlowPipelineProvider extends PipelineProvider
{

    public static final String NAME = "NextFlow";

    public NextFlowPipelineProvider(NextFlowModule owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public boolean isShowActionsIfModuleInactive()
    {
        // We rely on a setting that folder admins can't control to determine if NextFlow is available
        return true;
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;
        if (!NextFlowManager.get().isEnabled(context.getContainer()))
            return;

        String actionId = createActionId(NextFlowController.NextFlowRunAction.class, "Analyze with NextFlow");
        addAction(actionId,
                NextFlowController.NextFlowRunAction.class,
                "Analyze with NextFlow",
                directory,
                directory.listPaths(new FileTypesEntryFilter(NextFlowProtocol.INPUT_TYPES)),
                true,
                true,
                includeAll);
    }
}
