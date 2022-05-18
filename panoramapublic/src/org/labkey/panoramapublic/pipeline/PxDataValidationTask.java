package org.labkey.panoramapublic.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.panoramapublic.model.ExperimentAnnotations;
import org.labkey.panoramapublic.model.validation.DataValidation;
import org.labkey.panoramapublic.proteomexchange.validator.ValidatorStatus;
import org.labkey.panoramapublic.proteomexchange.validator.DataValidator;
import org.labkey.panoramapublic.query.DataValidationManager;

import java.util.Collections;
import java.util.List;

public class PxDataValidationTask extends PipelineJob.Task<PxDataValidationTask.Factory>
{
    private PxDataValidationTask(PxDataValidationTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        PxDataValidationJobSupport support = job.getJobSupport(PxDataValidationJobSupport.class);
        doValidation(job, support);

        return new RecordedActionSet();
    }

    public void doValidation(PipelineJob job, PxDataValidationJobSupport jobSupport) throws PipelineJobException
    {
        Logger log = job.getLogger();
        try
        {
            ExperimentAnnotations exptAnnotations = jobSupport.getExpAnnotations();
            DataValidation validation = DataValidationManager.getValidation(jobSupport.getValidationId(), exptAnnotations.getContainer());
            if (validation == null)
            {
                throw new PipelineJobException(String.format("Could not find a data validation row for Id %d in folder '%s'.",
                        jobSupport.getValidationId(), exptAnnotations.getContainer().getPath()));
            }
            log.info(String.format("Validating data for experiment Id: %d, validation Id: %d", exptAnnotations.getId(), validation.getId()));
            Integer pipelineJobId = (PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getJobGUID()));
            if (pipelineJobId != null && pipelineJobId != validation.getJobId())
            {
                throw new PipelineJobException(String.format("Unexpected pipeline job Id %d.  Job Id saved in the validation row (Id: %d) is %d.",
                        pipelineJobId, validation.getId(), validation.getJobId()));
            }
            // If this is a retry, clear out any previously saved validation rows
            DataValidationManager.clearValidation(validation);

            ValidatorListener listener = new ValidatorListener(job);
            DataValidator validator = new DataValidator(exptAnnotations, validation, listener);
            ValidatorStatus status = validator.validateExperiment(job.getUser());

            log.info("Data validation is complete. Status is " + status.getValidation().getStatus());
        }
        catch (CancelledException e)
        {
            log.info("Data validation job was cancelled.");
            throw e;
        }
        catch (PipelineJobException e)
        {
            throw e;
        }
        catch (Throwable t)
        {
            log.fatal("");
            log.fatal("Error validating experiment data", t);
            log.fatal("Data validation failed");
            throw new PipelineJobException(t) {};
        }
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, PxDataValidationTask.Factory>
    {
        public Factory()
        {
            super(PxDataValidationTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new PxDataValidationTask(this, job);
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
            return "VALIDATE EXPERIMENT";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
