package org.labkey.nextflow.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.nextflow.NextFlowController;
import org.labkey.nextflow.NextFlowManager;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class NextFlowRunTask extends PipelineJob.Task<NextFlowRunTask.Factory>
{
    public NextFlowRunTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        Logger log = getJob().getLogger();
        NextFlowController.NextFlowConfiguration config = NextFlowManager.get().getConfiguration();
        String nextFlowConfigFilePath = config.getNextFlowConfigFilePath();
        String s3BucketPath = config.getS3BucketPath();
        String s3Path = "s3://" + s3BucketPath;
        ProcessBuilder pb = new ProcessBuilder( "nextflow" , "run", "-resume", "-r", "main", "-profile", "aws", "mriffle/nf-skyline-dia-ms", "-bucket-dir", s3Path, "-c", nextFlowConfigFilePath);
        log.info("Job Started");
        File dir = new File(nextFlowConfigFilePath);
        getJob().runSubProcess(pb, dir);
        log.info("Job Finished");
        return new RecordedActionSet();
    }

    @Override
    public NextFlowPipelineJob getJob()
    {
        return (NextFlowPipelineJob) super.getJob();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(NextFlowRunTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new NextFlowRunTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "NextFlow Run";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
