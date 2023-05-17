package org.labkey.panoramapublic;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.panoramapublic.pipeline.CopyExperimentPipelineJob;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * This importer does a file move instead of copy to the temp directory and creates a symlink in place of the original
 * file.
 */
public class PanoramaPublicFileImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return PanoramaPublicManager.PANORAMA_PUBLIC_FILES;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        Logger log = ctx.getLogger();

        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        File targetRoot = fcs.getFileRoot(ctx.getContainer());

        if (null == targetRoot)
        {
            log.error("File copy target folder not found: " + ctx.getContainer().getPath());
            return;
        }

        if (null == job)
        {
            log.error("Pipeline job not found.");
            return;
        }

        if (job instanceof CopyExperimentPipelineJob expJob)
        {
            File targetFiles = new File(targetRoot.getPath(), FileContentService.FILES_LINK);

            // Get source files including resolving subfolders
            String divider = FileContentService.FILES_LINK + File.separator + PipelineService.EXPORT_DIR;
            String subProject = root.getLocation().substring(root.getLocation().lastIndexOf(divider) + divider.length());
            subProject = subProject.replace(File.separator + SubfolderWriter.DIRECTORY_NAME, "");

            Path sourcePath = Paths.get(fcs.getFileRoot(expJob.getExportSourceContainer()).getPath(), subProject);
            File sourceFiles = Paths.get(sourcePath.toString(), FileContentService.FILES_LINK).toFile();

            if (!targetFiles.exists())
            {
                log.warn("Panorama public file copy target not found. Creating directory: " + targetFiles);
                Files.createDirectories(targetFiles.toPath());
            }

            log.info("Moving files and creating sym links in folder " + ctx.getContainer().getPath());
            PanoramaPublicSymlinkManager.get().moveAndSymLinkDirectory(expJob.getUser(), expJob.getContainer(), sourceFiles, targetFiles, false, log);

            String sourceContainerPath = Paths.get(expJob.getExportSourceContainer().getPath(), subProject).toString().replace(File.separator, "/");
            Container resolvedSourceContainer = ContainerService.get().getForPath(sourceContainerPath);

            if (null == resolvedSourceContainer)
            {
                throw new ImportException("Invalid source container found for aligning data file urls: " + sourceContainerPath);
            }

            alignDataFileUrls(expJob.getUser(), ctx.getContainer(), resolvedSourceContainer, log);
        }
    }

    private void alignDataFileUrls(User user, Container targetContainer, Container sourceContainer, Logger log) throws BatchValidationException, ImportException
    {
        log.info("Aligning data files urls in folder: " + targetContainer.getPath());

        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        boolean errors = false;

        // Get all source and target container runs
        List<? extends ExpRun> sourceRuns = ExperimentService.get().getExpRuns(sourceContainer, null, null);
        for (ExpRun run : ExperimentService.get().getExpRuns(targetContainer, null, null))
        {
            // Find matching source run
            ExpRun sourceRun = sourceRuns.stream().filter(r -> r.getName().equals(run.getName())).findFirst().orElse(null);
            if (null == sourceRun)
            {
                log.error("Source run not found for run: " + run.getName());
                errors = true;
                continue;
            }

            Path targetRootPath = fcs.getFileRootPath(run.getContainer(), FileContentService.ContentType.files);
            if(targetRootPath == null || !Files.exists(targetRootPath))
            {
                throw new ImportException("Target file root path for container " + run.getContainer().getPath() + " does not exist: " + targetRootPath);
            }

            // Get source path relative to file root and apply to target file root
            String sourcePath = sourceRun.getFilePathRootPath().toString();
            String relativeSource = sourcePath.substring(sourcePath.lastIndexOf(FileContentService.FILES_LINK) + FileContentService.FILES_LINK.length());
            Path targetRunPath = Paths.get(targetRootPath.toString(), relativeSource);

            // Update run file path
            if (targetRunPath.toFile().exists())
            {
                run.setFilePathRoot(targetRunPath.toFile());
                run.save(user);
                log.debug("Updated copied run: " + run.getName() + " with file path: " + targetRunPath);
            }
            else
            {
                log.error("Run file path not found: " + targetRunPath);
                errors = true;
            }

            // Update data file urls
            for (ExpData data : run.getAllDataUsedByRun())
            {
                // Find matching source data
                ExpData sourceData = sourceRun.getAllDataUsedByRun().stream().filter(d -> d.getName().equals(data.getName())).findFirst().orElse(null);
                if (null != sourceData && null != sourceData.getDataFileUrl())
                {
                    // Get source data path relative to file root and apply to target data file root
                    String sourceDataName = sourceData.getDataFileUrl().substring(sourceData.getDataFileUrl().lastIndexOf(FileContentService.FILES_LINK) + FileContentService.FILES_LINK.length());
                    Path targetDataPath = Paths.get(targetRootPath.toString(), sourceDataName);

                    // Update data file url
                    if (targetDataPath.toFile().exists())
                    {
                        data.setDataFileURI(targetDataPath.toUri());
                        data.save(user);
                        log.debug("Updated copied data: " + data.getName() + " with file path: " + targetDataPath);
                    }
                    else
                    {
                        log.error("Data file url not found: " + sourceData.getDataFileUrl());
                        errors = true;
                    }
                }
            }
        }
        if (errors)
        {
            throw new ImportException("Data files urls could not be aligned.");
        }
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new PanoramaPublicFileImporter();
        }

        @Override
        public int getPriority()
        {
            // We want this to run last to do exp.data.datafileurl cleanup
            return PanoramaPublicManager.PRIORITY_PANORAMA_PUBLIC_FILES;
        }
    }
}
