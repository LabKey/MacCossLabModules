package org.labkey.panoramapublic;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.data.Container;
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
import java.util.Objects;

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
        return "Panorama Public Files";
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

            alignDataFileUrls(expJob.getUser(), ctx.getContainer(), log);
        }
    }

    private void alignDataFileUrls(User user, Container targetContainer, Logger log) throws BatchValidationException, ImportException
    {
        log.info("Aligning data files urls in folder: " + targetContainer.getPath());

        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        ExperimentService expService = ExperimentService.get();
        List<? extends ExpRun> runs = expService.getExpRuns(targetContainer, null, null);
        boolean errors = false;

        Path fileRootPath = fcs.getFileRootPath(targetContainer, FileContentService.ContentType.files);
        if(fileRootPath == null || !Files.exists(fileRootPath))
        {
            throw new ImportException("File root path for container " + targetContainer.getPath() + " does not exist: " + fileRootPath);
        }

        for (ExpRun run : runs)
        {
            run.setFilePathRootPath(fileRootPath);
            run.save(user);
            log.debug("Setting filePathRoot on copied run: " + run.getName() + " to: " + fileRootPath);

            for (ExpData data : run.getAllDataUsedByRun())
            {
                if (null != data.getRun() && data.getDataFileUrl().contains(FileContentService.FILES_LINK))
                {
                    String[] parts = Objects.requireNonNull(data.getFilePath()).toString().split("Run\\d+");

                    if (parts.length > 1)
                    {
                        String fileName = parts[1];
                        Path newDataPath = Paths.get(fileRootPath.toString(), fileName);

                        if (newDataPath.toFile().exists())
                        {
                            data.setDataFileURI(newDataPath.toUri());
                            data.save(user);
                            log.debug("Setting dataFileUri on copied data: " + data.getName() + " to: " + newDataPath);
                        }
                        else
                        {
                            log.error("Data file not found: " + newDataPath.toUri());
                            errors = true;
                        }
                    }
                    else
                    {
                        log.error("Unexpected data file path. Could not align dataFileUri. " + data.getFilePath().toString());
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
