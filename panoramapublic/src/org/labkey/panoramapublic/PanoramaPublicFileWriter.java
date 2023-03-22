package org.labkey.panoramapublic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.panoramapublic.pipeline.ExperimentExportTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PanoramaPublicFileWriter
{
    private static final Logger _log = LogManager.getLogger(ExperimentExportTask.class);

    private void moveAndSymLinkDirectory(File source, File target) throws IOException
    {
        for (File file : source.listFiles())
        {
            if (file.isDirectory())
            {
                Path targetPath = Path.of(target.getPath(), file.getName());
                Files.createDirectory(targetPath);
                _log.info("Creating directory: " + targetPath);

                moveAndSymLinkDirectory(file, targetPath.toFile());
            }
            else
            {
                _log.info("Creating file: " + Path.of(target.getPath(), file.getName()));
                Files.move(file.toPath(), Path.of(target.getPath(), file.getName()), REPLACE_EXISTING);
                Files.createSymbolicLink(file.toPath(), target.toPath());
            }
        }
    }

    public void write(Container c, Container target) throws Exception
    {
        File sourceRoot = FileContentService.get().getFileRoot(c);
        File targetRoot = FileContentService.get().getFileRoot(target);

        if (null == sourceRoot)
        {
            _log.error("File copy source folder not found: " + c.getPath());
            return;
        }

        if (null == targetRoot)
        {
            _log.error("File copy target folder not found: " + target);
            return;
        }

        File sourceFiles = new File(sourceRoot.getPath().concat("\\").concat(FileContentService.FILES_LINK));
        File targetFiles = new File(targetRoot.getPath().concat("\\").concat(FileContentService.FILES_LINK));

        if (!sourceFiles.exists())
        {
            _log.error("File copy source files directory not found: " + sourceFiles);
            return;
        }

        if (!targetFiles.exists())
        {
            _log.error("File copy target files directory not found: " + targetFiles);
            return;
        }

        moveAndSymLinkDirectory(sourceFiles, targetFiles);
    }


}
