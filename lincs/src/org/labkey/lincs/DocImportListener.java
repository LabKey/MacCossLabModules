package org.labkey.lincs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.SkylineDocumentImportListener;
import org.labkey.api.targetedms.TargetedMSService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.lincs.psp.LincsPspJob;
import org.labkey.lincs.psp.LincsPspPipelineJob;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class DocImportListener implements ExperimentListener, SkylineDocumentImportListener
{
    private static Logger _log = LogManager.getLogger(DocImportListener.class);

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
                Set<String> lincsFilesLowerCase = getLincsFilesLowerCase(baseName);
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

    private Set<String> getLincsFilesLowerCase(String baseName)
    {
        Set<String> files = new HashSet<>();
        baseName = baseName.toLowerCase();
        files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Two).toLowerCase());

        files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Three).toLowerCase());
        files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Four).toLowerCase());
        files.add(baseName + LincsModule.getExt(LincsModule.LincsLevel.Config).toLowerCase());

        files.add(baseName + ".console.txt");
        files.add(baseName + ".script.rout");
        return files;
    }

    @Override
    public void onDocumentImport(Container container, User user, ITargetedMSRun skylineRun)
    {
        // Check if the LINCS module is enabled.
        if (!container.getActiveModules().contains(ModuleLoader.getInstance().getModule(LincsModule.class)))
        {
            return;
        }

        PipeRoot root = PipelineService.get().findPipelineRoot(container);
        if (root == null || !root.isValid())
        {
            _log.error("LINCS: No valid pipeline root found for " + container.getPath());
            return;
        }

        LincsPspJob pspJob = LincsManager.get().saveNewLincsPspJob(skylineRun, user);
        ViewBackgroundInfo info = new ViewBackgroundInfo(container, user, null);
        LincsPspPipelineJob job = new LincsPspPipelineJob(info, root, skylineRun, pspJob,null);

        try
        {
            PipelineService.get().queueJob(job);
        }
        catch (PipelineValidationException e)
        {
            _log.error("Error adding LINCS pipeline job to queue. Message: " + e.getMessage(), e);
            if(pspJob != null)
            {
                LincsManager.get().deleteLincsPspJob(pspJob);
            }
            return;
        }

        int jobId = PipelineService.get().getJobId(user, container, job.getJobGUID());
        _log.info("LINCS: Queued job Id " + jobId +" for creating GCT files for " + skylineRun.getFileName() + ". Container: " + container.getPath());

        pspJob.setPipelineJobId(jobId);
        LincsManager.get().updatePipelineJobId(pspJob);
    }
}
