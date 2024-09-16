package org.labkey.nextflow.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.IOException;

public class NextFlowPipelineJob extends PipelineJob
{
    // For serialization
    protected NextFlowPipelineJob()
    {}

    public NextFlowPipelineJob(ViewBackgroundInfo info, @NotNull PipeRoot root)
    {
        super(null, info, root);
        try
        {
            setLogFile(FileUtil.createTempFile("NextFlowPipelineJob", ".txt"));
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }


    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "NextFlow Job";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(NextFlowPipelineJob.class));
    }
}
