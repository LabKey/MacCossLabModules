package org.labkey.lincs.cromwell;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.FileType;
import org.labkey.lincs.psp.LincsPspJobSupport;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public class LincsL2GctCromwellTask extends PipelineJob.Task<LincsL2GctCromwellTask.Factory>
{
    public LincsL2GctCromwellTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        var job = getJob();
        LincsPspJobSupport support = job.getJobSupport(LincsPspJobSupport.class);

        job.getLogger().info("Submitting Cromwell job to create L2 GCT for " + support.getRun().getFileName());

        submitCromwellJob(support, job.getLogger());

        job.getLogger().info("Finished creating L2 GCT.");

        return new RecordedActionSet();
    }

    private void submitCromwellJob(LincsPspJobSupport jobSupport, Logger log) throws PipelineJobException
    {
        Container container = this.getJob().getContainer();

        ITargetedMSRun run = jobSupport.getRun();
        CromwellConfig cromwellConfig;

        try
        {
            cromwellConfig = CromwellConfig.getValidConfig(container);
        }
        catch(CromwellException e)
        {
            throw new PipelineJobException("Could not get a valid Cromwell configuration. Error was: " + e.getMessage(), e);
        }

        CromwellJobSubmitter submitter = new CromwellJobSubmitter(cromwellConfig);
        CromwellUtil.CromwellJobStatus cromwellStatus = submitter.submitJob(getJob().getContainer(), run.getFileName(), log);
        if(cromwellStatus == null)
        {
            throw new PipelineJobException("Unable to submit Cromwell job. Did not get a job status.");
        }

        URI jobStatusUri = cromwellConfig.getJobStatusUri(cromwellStatus.getJobId());
        URI jobMetadataUri = cromwellConfig.getMetadataUri(cromwellStatus.getJobId());
        final int sleepTime = 20 * 1000;

        int attempts = 5;
        String lastStatus = "";

        log.info("Checking status of job at " + jobStatusUri);
        while(true)
        {
            try
            {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e)
            {
                log.error("Cancelled task.", e);
                break;
            }

            try
            {
                var pipelineJobStatus = PipelineService.get().getStatusFile(getJob().getJobGUID()).getStatus();
                if(PipelineJob.TaskStatus.cancelling.matches(pipelineJobStatus))
                {
                    // TODO: Send abort signal to Cromwell
                    break;
                }

                CromwellUtil.CromwellJobStatus status = CromwellUtil.getJobStatus(jobStatusUri, log);
                if(status == null)
                {
                    if(attempts > 0)
                    {
                        log.info("Did not get job status.  Job may not yet have been put in the queue. Trying again...");
                        attempts--;
                        continue;
                    }
                    else
                    {
                        throw new PipelineJobException("Cannot get status of job at " + jobStatusUri);
                    }
                }
                if(!lastStatus.equalsIgnoreCase(status.getJobStatus()))
                {
                    log.info("Cromwell job status: " + status.getJobStatus());
                    lastStatus = status.getJobStatus();
                }

                if(status.success())
                {
                    log.info("Cromwell job completed successfully. Job details at " + jobMetadataUri);
                    break;
                }
                if(status.failed())
                {
                    throw new PipelineJobException("Cromwel job failed. Get details at " + jobMetadataUri);
                }
            }
            catch (CromwellException e)
            {
                throw new PipelineJobException("An error occurred getting Cromwell job status", e);
            }
        }
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, LincsL2GctCromwellTask.Factory>
    {
        public Factory()
        {
            super(LincsL2GctCromwellTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new LincsL2GctCromwellTask(this, job);
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
            return "SUBMIT PSP JOB";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
