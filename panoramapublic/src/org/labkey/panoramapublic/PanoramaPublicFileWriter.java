package org.labkey.panoramapublic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.writer.VirtualFile;

import java.io.File;

/**
 * This file writer moves (instead of copy) the file from the temp directory into the public folder and updates the
 * symlink in the original folder.
 */
public class PanoramaPublicFileWriter extends BaseFolderWriter
{
    // Use a different directory than "files" so file importer doesn't pick it up
    public static final String FILE_MV = "filesMv";
    private static final Logger _log = LogManager.getLogger(PanoramaPublicFileWriter.class);

    @Override
    public String getDataType()
    {
        return PanoramaPublicManager.PANORAMA_PUBLIC_FILES;
    }

    @Override
    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        File sourceRoot = FileContentService.get().getFileRoot(c);

        if (null == sourceRoot)
        {
            _log.error("File copy source folder not found: " + c.getPath());
            return;
        }

        File sourceFiles = new File(sourceRoot.getPath(), FileContentService.FILES_LINK);
        File targetFiles = new File(vf.getLocation() ,FILE_MV);

        if (!sourceFiles.exists())
        {
            _log.warn("File copy source files directory not found: " + sourceFiles);
            return;
        }

//        if (!targetFiles.exists())
//        {
//            Files.createDirectory(targetFiles.toPath());
//        }
//
//        PanoramaPublicManager.get().moveAndSymLinkDirectory(sourceFiles, targetFiles, true);
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new PanoramaPublicFileWriter();
        }
    }


}
