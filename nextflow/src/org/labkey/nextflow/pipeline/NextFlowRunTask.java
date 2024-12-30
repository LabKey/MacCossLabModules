package org.labkey.nextflow.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileType;
import org.labkey.nextflow.NextFlowConfiguration;
import org.labkey.nextflow.NextFlowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class NextFlowRunTask extends WorkDirectoryTask<NextFlowRunTask.Factory>
{
    public static final String SPECTRA_INPUT_ROLE = "Spectra";

    public static final String ACTION_NAME = "NextFlow";

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

            RecordedAction action = new RecordedAction(ACTION_NAME);
            for (Path inputFile : getJob().getInputFilePaths())
            {
                action.addInput(inputFile.toFile(), SPECTRA_INPUT_ROLE);
            }
            addOutputs(action, getJob().getLogFilePath().getParent().resolve("reports"), log);
            addOutputs(action, getJob().getLogFilePath().getParent().resolve("results"), log);
            return new RecordedActionSet(action);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
        }
    }

    private void addOutputs(RecordedAction action, Path path, Logger log) throws IOException
    {
        if (Files.isRegularFile(path))
        {
            action.addOutput(path.toFile(), "Output", false);
            if (path.toString().toLowerCase().endsWith(".sky.zip"))
            {
                try
                {
                    log.info("Queueing import for {}", path);
                    // Make sure that the TargetedMS runs get wrapped with their experiment run counterparts
                    TargetedMSService.get().importSkylineDocument(getJob().getInfo(), path);
                }
                catch (XarFormatException | PipelineValidationException e)
                {
                    log.error("Error queuing import of Skyline document", e);
                }
            }
        }
        else if (Files.isDirectory(path))
        {
            try (Stream<Path> listing = Files.list(path))
            {
                for (Path child : listing.toList())
                {
                    addOutputs(action, child, log);
                }
            }
        }
    }

    private boolean hasAwsSection(Path configFile) throws PipelineJobException
    {
        try (InputStream in = Files.newInputStream(configFile);
             InputStreamReader isReader = new InputStreamReader(in, StandardCharsets.UTF_8);
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
        Path configFile = getJob().getConfig();

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
        args.add(configFile.toAbsolutePath().toString());
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
        public NextFlowRunTask createTask(PipelineJob job)
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
            return List.of(ACTION_NAME);
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
