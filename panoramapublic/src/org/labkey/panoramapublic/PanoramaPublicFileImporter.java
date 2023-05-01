package org.labkey.panoramapublic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.nio.file.Files;

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
        File targetRoot = FileContentService.get().getFileRoot(ctx.getContainer());

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
        File sourceFiles = new File(root.getLocation(), PanoramaPublicFileWriter.FILE_MV);

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

        PanoramaPublicManager.get().moveAndSymLinkDirectory(sourceFiles, targetFiles, false);;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new PanoramaPublicFileImporter();
        }
    }
}
