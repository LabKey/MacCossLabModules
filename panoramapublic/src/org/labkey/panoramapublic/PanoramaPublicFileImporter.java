package org.labkey.panoramapublic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This importer does a file move instead of copy to the temp directory and creates a symlink in place of the original
 * file.
 */
public class PanoramaPublicFileImporter implements FolderImporter
{
    private static final Logger _log = LogManager.getLogger(PanoramaPublicFileImporter.class);

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
        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        File targetRoot = fcs.getFileRoot(ctx.getContainer());

        if (null == targetRoot)
        {
            _log.error("File copy target folder not found: " + ctx.getContainer().getPath());
            return;
        }

        if (null == job)
        {
            _log.error("Pipeline job not found.");
            return;
        }

        File targetFiles = new File(targetRoot.getPath(), FileContentService.FILES_LINK);

        // Get source files including resolving subfolders
        String divider = FileContentService.FILES_LINK + File.separator + PipelineService.EXPORT_DIR;
        String subProject = root.getLocation().substring(root.getLocation().lastIndexOf(divider) + divider.length());
        subProject = subProject.replace(File.separator + SubfolderWriter.DIRECTORY_NAME, "");

        File sourceFiles = Paths.get(fcs.getFileRoot(((CopyExperimentPipelineJob) job).getExportSourceContainer()).getPath(), subProject, FileContentService.FILES_LINK).toFile();

        if (!sourceFiles.exists())
        {
            // This is expected for full file copy instead of file move
            return;
        }

        if (!targetFiles.exists())
        {
            _log.warn("Panorama public file copy target not found. Creating directory: " + targetFiles);
            Files.createDirectories(targetFiles.toPath());
        }

        PanoramaPublicSymlinkManager.get().moveAndSymLinkDirectory(job.getUser(), job.getContainer(), sourceFiles, targetFiles, false);

        alignDataFileUrls(job.getUser(), ctx.getContainer(), root);
    }

    private List<ExpRun> getAllExpRuns(Container container)
    {
        Set<Container> children = ContainerManager.getAllChildren(container);
        ExperimentService expService = ExperimentService.get();
        List<ExpRun> allRuns = new ArrayList<>();

        for(Container child: children)
        {
            List<? extends ExpRun> runs = expService.getExpRuns(child, null, null);
            allRuns.addAll(runs);
        }
        return allRuns;
    }

    private void alignDataFileUrls(User user, Container targetContainer, VirtualFile root) throws BatchValidationException
    {
        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            return;

        for (ExpRun run : getAllExpRuns(targetContainer))
        {
            Path fileRootPath = fcs.getFileRootPath(run.getContainer(), FileContentService.ContentType.files);
            if(fileRootPath == null || !Files.exists(fileRootPath))
            {
                _log.error("File root path for container " + run.getContainer().getPath() + " does not exist: " + fileRootPath);
                return;
            }

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

                            String[] relativePath = newDataPath.toString().split(FileContentService.FILES_LINK);
                            run.setFilePathRoot(new File(relativePath[0] + FileContentService.FILES_LINK));
                            run.save(user);
                        }
                        else
                        {
                            _log.error("Data file not found: " + newDataPath.toUri());
                        }
                    }
                }
            }
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
            return 1000;
        }
    }
}
