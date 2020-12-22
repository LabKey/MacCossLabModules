package org.labkey.lincs.cromwell;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.util.FileType;
import org.labkey.lincs.LincsController;
import org.labkey.lincs.LincsModule;
import org.labkey.lincs.psp.LincsPspJobSupport;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class CromwellGctTask extends PipelineJob.Task<CromwellGctTask.Factory>
{
    public CromwellGctTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        var job = getJob();
        LincsPspJobSupport support = job.getJobSupport(LincsPspJobSupport.class);

        job.getLogger().info("Starting task to create L2 GCT for " + support.getRun().getFileName());

        submitCromwellJob(support, job.getLogger());

        job.getLogger().info("Finished creating L2 GCT.");

        return new RecordedActionSet();
    }

    private void submitCromwellJob(LincsPspJobSupport jobSupport, Logger log) throws PipelineJobException
    {
        var container = this.getJob().getContainer();

        ITargetedMSRun run = jobSupport.getRun();

        if(l2GctExistsForRun(run, container))
        {
            log.info("L2 GCT for run " + run.getFileName() + " exists. Skipping Cromwell job submission.");
            return;
        }

        CromwellConfig cromwellConfig;
        try
        {
            cromwellConfig = CromwellConfig.getValidConfig(container);
        }
        catch(CromwellException e)
        {
            throw new PipelineJobException("Could not get a valid Cromwell configuration. Error was: " + e.getMessage(), e);
        }

        LincsModule.LincsAssay assayType = LincsController.getLincsAssayType(container);
        if(assayType == null)
        {
            throw new PipelineJobException("Lincs assay type could not be determined for container " + container.getPath());
        }

        CromwellJobSubmitter submitter = new CromwellJobSubmitter(cromwellConfig, assayType);
        CromwellUtil.CromwellJobStatus cromwellStatus = submitter.submitJob(getJob().getContainer(), run.getFileName(), log);
        if(cromwellStatus == null)
        {
            throw new PipelineJobException("Unable to submit Cromwell job. Did not get a job status.");
        }

        URI jobStatusUri = cromwellConfig.getJobStatusUri(cromwellStatus.getJobId());
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

            // If the user has cancelled the job, send abort request for the job running on the Cromwell server.
            var pipelineJobStatus = PipelineService.get().getStatusFile(getJob().getJobGUID()).getStatus();
            if(PipelineJob.TaskStatus.cancelling.matches(pipelineJobStatus))
            {
                try
                {
                    CromwellUtil.abortJob(cromwellConfig.getAbortUri(cromwellStatus.getJobId()), log);
                }
                catch (CromwellException e)
                {
                    log.warn("Error aborting cromwell job");
                }
                break;
            }

            try
            {
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
                    log.info("Cromwell job completed successfully");
                    break;
                }
                if(status.failed())
                {
                    throw new PipelineJobException("Cromwell job failed");
                }
            }
            catch (CromwellException e)
            {
                throw new PipelineJobException("An error occurred getting Cromwell job status", e);
            }
        }
    }

    private boolean l2GctExistsForRun(ITargetedMSRun run, Container container)
    {
        FileContentService fcs = FileContentService.get();
        if(fcs != null)
        {
            Path fileRootPath = fcs.getFileRootPath(container, FileContentService.ContentType.files);
            if (fileRootPath != null)
            {
                Path l2Gct = fileRootPath.resolve(LincsController.GCT_DIR).resolve(run.getBaseName() + LincsModule.getExt(LincsModule.LincsLevel.Two));
                return Files.exists(l2Gct);
            }
        }
        return false;
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, CromwellGctTask.Factory>
    {
        public Factory()
        {
            super(CromwellGctTask.class);
        }

        @Override
        public CromwellGctTask createTask(PipelineJob job)
        {
            return new CromwellGctTask(this, job);
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
