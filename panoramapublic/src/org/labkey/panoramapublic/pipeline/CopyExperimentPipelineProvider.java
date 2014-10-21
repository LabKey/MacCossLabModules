package org.labkey.targetedms.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;

/**
 * User: vsharma
 * Date: 8/27/2014
 * Time: 11:03 PM
 */
public class CopyExperimentPipelineProvider extends PipelineProvider
{
    static String NAME = "PublishExperiment";

    public CopyExperimentPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll) {}
}
