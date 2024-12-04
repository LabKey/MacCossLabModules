package org.labkey.nextflow.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.FileType;
import org.labkey.nextflow.NextFlowConfiguration;
import org.labkey.nextflow.NextFlowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

        SecurityManager.TransformSession session = null;

        try
        {
            NextFlowConfiguration config = NextFlowManager.get().getConfiguration();
            if (config == null)
            {
                throw new PipelineJobException("No NextFlow configuration found");
            }

            // Use the configured API key if set
            String apiKey = config.getApiKey();
            if (apiKey == null)
            {
                session = SecurityManager.createTransformSession(getJob().getUser());
                apiKey = session.getApiKey();
            }

            // Need to pass to the main process directly in the future to allow concurrent execution for different users
            ProcessBuilder secretsPB = new ProcessBuilder("nextflow", "secrets", "set", "PANORAMA_API_KEY", apiKey);
            log.info("Job Started");
            File dir = getJob().getLogFile().getParentFile();
            getJob().runSubProcess(secretsPB, dir);

            ProcessBuilder executionPB = new ProcessBuilder(getArgs());
            getJob().runSubProcess(executionPB, dir);
            log.info("Job Finished");
            return new RecordedActionSet();
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
        }
    }

    private boolean hasAwsSection(File configFile) throws PipelineJobException
    {
        try (FileInputStream fIn = new FileInputStream(configFile);
             InputStreamReader isReader = new InputStreamReader(fIn, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isReader))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                // Ignore comments
                if (!line.startsWith("//"))
                {
                    if (line.startsWith("aws"))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }


    private @NotNull List<String> getArgs() throws PipelineJobException
    {
        NextFlowConfiguration config = NextFlowManager.get().getConfiguration();
        String nextFlowConfigFilePath = config.getNextFlowConfigFilePath();

        if (nextFlowConfigFilePath == null)
        {
            throw new PipelineJobException("No NextFlow config file specified");
        }

        File configFile = new File(nextFlowConfigFilePath);
        if (!configFile.isFile())
        {
            throw new PipelineJobException("NextFlow config file not found");
        }

        boolean aws = hasAwsSection(configFile);

        List<String> args = new ArrayList<>(Arrays.asList("nextflow", "run", "-resume", "-r", "main"));
        if (aws)
        {
            args.add("-profile");
            args.add("aws");
        }
        args.add("mriffle/nf-skyline-dia-ms");
        if (aws)
        {
            String s3BucketPath = config.getS3BucketPath();
            String s3Path = "s3://" + s3BucketPath;

            args.add("-bucket-dir");
            args.add(s3Path);
        }
        args.add("-c");
        args.add(nextFlowConfigFilePath);
        return args;
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
