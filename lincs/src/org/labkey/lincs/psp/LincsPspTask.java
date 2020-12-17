package org.labkey.lincs.psp;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.FileType;
import org.labkey.lincs.LincsModule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LincsPspTask extends PipelineJob.Task<LincsPspTask.Factory>
{
    public LincsPspTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        LincsPspJobSupport support = job.getJobSupport(LincsPspJobSupport.class);

        job.getLogger().info("Running LINCS PSP pipeline for " + support.getRun().getBaseName());

        postToPsp(support, job.getUser(), job.getLogger());

        job.getLogger().info("Finished running LINCS PSP pipeline.");

        return new RecordedActionSet();
    }

    private void postToPsp(LincsPspJobSupport jobSupport, User user, Logger log)
    {
        Container container = this.getJob().getContainer();

        PspEndpoint endpoint = jobSupport.getPspEndpoint();

        LincsPspJob pspJob = jobSupport.getPspJob();
        LincsPspJob oldPspJob = jobSupport.getOldPspJob();
        ITargetedMSRun run = jobSupport.getRun();
        try
        {
            if (oldPspJob != null)
            {
                log.info("Resubmitting job for runId: " + run.getId() + ", name: " + run.getBaseName());
                log.info("Old job details: ");
                log.info("Id: " + oldPspJob.getId());
                log.info("Pipeline job Id: " + oldPspJob.getPipelineJobId());
                log.info("Run Id: " + oldPspJob.getRunId());
                log.info("PSP Job Id: " + oldPspJob.getPspJobId());
                log.info("PSP Job name: " + oldPspJob.getPspJobName());
                log.info("PSP Job status: " + oldPspJob.getStatus());
                log.info("JSON: " + oldPspJob.getJson());

                log.info(("Resubmitting..."));
            }
            else
            {
                log.info("Submitting job for runId: " + run.getId() + ", name: " + run.getBaseName());
            }

            if(pspJob.getPipelineJobId() == null)
            {
                Integer pipelineJobId = (PipelineService.get().getJobId(getJob().getUser(), getJob().getContainer(), getJob().getJobGUID()));
                pspJob.setPipelineJobId(pipelineJobId);
            }

            LincsModule module = ModuleLoader.getInstance().getModule(LincsModule.class);
            String suffix = module.PSP_JOB_NAME_SUFFIX_PROPERTY.getEffectiveValue(container);

            pspJob.setPspJobName(LincsPspUtil.getJobName(run, endpoint, suffix, log));
            log.info("PSP job name: " + pspJob.getPspJobName());

            LincsPspUtil.submitPspJob(endpoint, pspJob, run, user, log);
        }
        catch(LincsPspException e)
        {
            log.error(e.getMessage(), e);
        }

        if (pspJob == null)
        {
            log.error("Error submitting PSP job");
            return;
        }

        final int sleepTime = 1 * 20 * 1000;
        final long thirtyMin = 30 * 60 * 1000;

        long timeWaited = 0;

        while(true)
        {
            try
            {
                log.info("Checking status of job " + pspJob.getPspJobId());
                LincsPspUtil.updateJobStatus(endpoint, pspJob, user);
                log.info("Status: " + pspJob.getStatus());

                if(pspJob.isSuccess())
                {
                    log.info("PSP job completed with status: " + pspJob.getStatus());
                    break;
                }
                if(pspJob.hasError())
                {
                    log.error("Error in PSP job. Error: " + pspJob.getError());
                    break;
                }
            }
            catch (IOException e)
            {
                log.error("An error occurred getting PSP job status", e);
                break;
            }
            catch (LincsPspException e)
            {
                log.error("Error parsing PSP job status", e);
                break;
            }

            if(timeWaited > thirtyMin)
            {
                log.error("Exceeded 30 minute wait time.");
                pspJob.setJobCheckTimeout();
                break;
            }

            try
            {
                Thread.sleep(sleepTime);
                timeWaited += sleepTime;
            }
            catch (InterruptedException e)
            {
                log.error("Cancelled task.", e);
                break;
            }

            var pipelineJobStatus = PipelineService.get().getStatusFile(getJob().getJobGUID()).getStatus();
            if(PipelineJob.TaskStatus.cancelling.matches(pipelineJobStatus))
            {
                break;
            }
        }
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(LincsPspTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new LincsPspTask(this, job);
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
