package org.labkey.lincs;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.lincs.psp.LincsPspException;
import org.labkey.lincs.psp.LincsPspJob;
import org.labkey.lincs.psp.LincsPspPipelineJob;
import org.labkey.lincs.psp.LincsPspUtil;
import org.labkey.lincs.psp.PspEndpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class DocImportListenter implements ExperimentListener
{
    private static Logger _log = Logger.getLogger(DocImportListenter.class);

    @Override
    public void beforeRunCreated(Container container, User user, ExpProtocol protocol, ExpRun run)
    {
        // Check if the LINCS module is enabled.
        if (!container.getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
        {
            return;
        }

        try
        {
            PipelineJobService service = PipelineJobService.get();
            if(service == null)
            {
                _log.error("LINCS: Could not get PipelineJobService.");
                return;
            }
            PipelineStatusFile.JobStore jobStore = service.getJobStore();
            if(jobStore == null)
            {
                _log.error("LINCS: Could not get Pipeline JobStore.");
                return;
            }
            PipelineJob job = jobStore.getJob(run.getJobId());
            if (job == null)
            {
                _log.error("LINCS: Could not find a job with ID " + run.getJobId());
                return;
            }
            if (job.getLogger() == null)
            {
                _log.error("LINCS: Could not get logger from job: " + job.getJobGUID());
                return;
            }
            _log = job.getLogger();
        }
        catch(Exception e)
        {
            _log.error("LINCS: Error looking up job for jobId: " + run.getJobId(), e);
            return;
        }

        PspEndpoint pspEndpoint = null;
        try
        {
            pspEndpoint = LincsPspUtil.getPspEndpoint(container);
        }
        catch(LincsPspException e)
        {
            if(e.noSavedPspConfig())
            {
                _log.info("LINCS: " + e.getMessage());
            }
            else
            {
                _log.error("LINCS: " + e.getMessage());
            }
            return;
        }

        ITargetedMSRun skylineRun = TargetedMSService.get().getRunByFileName(run.getName(), run.getContainer());
        if(skylineRun == null)
        {
            _log.error("LINCS: Could not find a targetedms run with filename " + run.getName());
        }

        try{
            PipeRoot root = PipelineService.get().findPipelineRoot(container);
            if (root == null || !root.isValid())
            {
                _log.error("LINCS: No valid pipeline root found for " + container.getPath());
                return;
            }
            ViewBackgroundInfo info = new ViewBackgroundInfo(container, user, null);

            LincsPspJob pspJob = LincsManager.get().saveNewLincsPspJob(skylineRun, user);

            LincsPspPipelineJob job = new LincsPspPipelineJob(info, root, skylineRun, pspJob,null, pspEndpoint);
            PipelineService.get().queueJob(job);

            int jobId = PipelineService.get().getJobId(user, container, job.getJobGUID());
            _log.info("LINCS: Queued job Id " + jobId +" for submitting POST request to PSP.");

            pspJob.setPipelineJobId(jobId);
            LincsManager.get().updatePipelineJobId(pspJob);
        }
        catch (PipelineValidationException e){
            _log.error("LINCS: Could not queue pipeline job for submitting POST request to PSP.", e);
        }
        catch(Exception e)
        {
            _log.error("LINCS: Error queueing pipeline job for submitting POST request to PSP.", e);
        }
    }

    @Override
    public void beforeRunDelete(ExpProtocol protocol, ExpRun run)
    {
        Container c = run.getContainer();
        // Check if the LINCS module is enabled.
        if (!c.getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
        {
            return;
        }

        ITargetedMSRun tRun = TargetedMSService.get().getRunByFileName(run.getName(), run.getContainer());
        if(tRun != null)
        {
            // Delete saved entries in lincs.lincspspjob table for this runId
            LincsManager.get().deleteLincsPspJobsForRun(tRun.getId());
        }

        // Get the file root for the container
        java.nio.file.Path gctDir = LincsController.getGCTDir(c);
        if(Files.exists(gctDir))
        {
            if(tRun != null)
            {
                String baseName = tRun.getBaseName();
                Set<String> lincsFilesLowerCase = getLincsFilesLowerCase(c, baseName);
                List<java.nio.file.Path> toDelete = new ArrayList<>();

                try (Stream<java.nio.file.Path> paths = Files.list(gctDir))
                {
                    paths.forEach(path -> {
                        String filename = FileUtil.getFileName(path).toLowerCase();
                        if (lincsFilesLowerCase.contains(filename))
                        {
                            toDelete.add(path);
                        }
                    });
                }
                catch (IOException e)
                {
                    _log.warn("LINCS: Error listing files in folder " + FileUtil.getAbsolutePath(gctDir), e);
                    return;
                }

                toDelete.forEach(path -> {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (IOException e)
                    {
                        _log.warn("LINCS: Error deleting file " + FileUtil.getAbsolutePath(path), e);
                    }
                });
            }
        }
    }

    private Set<String> getLincsFilesLowerCase(Container c, String baseName)
    {
        Set<String> files = new HashSet<>();
        boolean processOnClue = LincsModule.processGctOnClueServer(c);
        baseName = baseName.toLowerCase();
        files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Two).toLowerCase());
        if(processOnClue)
        {
            files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Three).toLowerCase());
            files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Four).toLowerCase());
            files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Config).toLowerCase());
        }
        else
        {
            files.add(baseName + ".processed.gct");
        }
        files.add(baseName + ".console.txt");
        files.add(baseName + ".script.rout");
        return files;
    }
}
