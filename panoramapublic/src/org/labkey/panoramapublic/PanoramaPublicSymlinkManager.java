package org.labkey.panoramapublic;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PanoramaPublicSymlinkManager
{
    private static final Logger _log = LogHelper.getLogger(PanoramaPublicSymlinkManager.class, "Handling symlinks between public and private folders");

    private static final boolean DEBUG_SYMLINKS_ON_WINDOWS = false; // Note: To debug on Windows, you must run as administrator

    private static final PanoramaPublicSymlinkManager _instance = new PanoramaPublicSymlinkManager();

    // Register symlinks created when copying files to Panorama Public
    private PanoramaPublicSymlinkManager()
    {
        // prevent external construction with a private default constructor
    }

    public static PanoramaPublicSymlinkManager get()
    {
        return _instance;
    }


    private void handleContainerSymlinks(File source, PanoramaPublicSymlinkHandler handler)
    {
        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                handleContainerSymlinks(file, handler);
            }
            else
            {
                Path filePath = file.toPath();
                if (Files.isSymbolicLink(filePath))
                {
                    try {
                        Path target = Files.readSymbolicLink(filePath);
                        handler.handleSymlink(filePath, target);
                    } catch (IOException x) {
                        _log.error("Unable to resolve symlink target for symlink at " + filePath);
                    }
                }
            }
        }
    }

    public void handleContainerSymlinks(Container container, PanoramaPublicSymlinkHandler handler)
    {
        FileContentService fcs = FileContentService.get();
        if (null != fcs)
        {
            File root = fcs.getFileRoot(container);
            if (null != root)
            {
                handleContainerSymlinks(root, handler);
            }
        }
    }

    private void handleAllSymlinks(PanoramaPublicSymlinkHandler handler)
    {
        Set<Container> containers = ContainerManager.getAllChildrenWithModule(ContainerManager.getRoot(), ModuleLoader.getInstance().getModule(PanoramaPublicModule.class));
        for (Container container : containers)
        {
            Set<Container> tree = ContainerManager.getAllChildren(container);
            for (Container node : tree)
            {
                handleContainerSymlinks(node, handler);
            }
        }
    }

    private String normalizeContainerPath(String path)
    {
        File file = new File(path);
        if (file.isAbsolute() || path.startsWith(File.separator))
            return path + File.separator;

        return File.separator + path + File.separator;
    }

    public void fireSymlinkCopiedExperimentDelete(ExperimentAnnotations expAnnot)
    {
        if (null != expAnnot.getSourceExperimentPath())
        {
            Container sourceContainer = ContainerService.get().getForPath(expAnnot.getSourceExperimentPath());
            if (null != sourceContainer)
            {
                handleContainerSymlinks(sourceContainer, (link, target) -> {
                    Files.move(target, link, REPLACE_EXISTING);
                    _log.info("File moved from " + target + " to " + link);

                    fireSymlinkUpdate(target, link);
                });
            }
        }
    }

    public void fireSymlinkContainerDelete(String container)
    {
        String containerPath = normalizeContainerPath(container);
        handleAllSymlinks((link, target) -> {
            if (String.valueOf(target).contains(containerPath))
            {
                try
                {
                    Files.delete(link);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void fireSymlinkUpdateContainer(String oldContainer, String newContainer)
    {
        String oldContainerPath = normalizeContainerPath(oldContainer);
        String newContainerPath = normalizeContainerPath(newContainer);
        handleAllSymlinks((link, target) -> {
            if (String.valueOf(target).contains(oldContainerPath))
            {
                Path newTarget = Path.of(target.toString().replace(oldContainerPath, newContainerPath));
                try
                {
                    Files.delete(link);
                    Files.createSymbolicLink(link, newTarget);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void fireSymlinkUpdate(Path oldTarget, Path newTarget)
    {
        handleAllSymlinks((link, target) -> {
            if (!target.equals(oldTarget))
                return;

            try
            {
                Files.delete(link);
                Files.createSymbolicLink(link, newTarget);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    public void moveAndSymLinkDirectory(User user, Container c, File source, File target, boolean createSourceSymLinks) throws IOException
    {
        FileContentService fcs = FileContentService.get();
        if (null == fcs)
            throw new RuntimeException("Unable to access FileContentService");

        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                Path targetPath = Path.of(target.getPath(), file.getName());
                if (!Files.exists(targetPath))
                {
                    Files.createDirectory(targetPath);
                    _log.info("Directory created: " + targetPath);
                }

                moveAndSymLinkDirectory(user, c, file, targetPath.toFile(), createSourceSymLinks);
            }
            else
            {
                Path targetPath = Path.of(target.getPath(), file.getName());
                Path filePath = file.toPath();

                // If this has already been copied, don't copy the symlink
                if (Files.isSymbolicLink(filePath) && filePath.compareTo(targetPath) == 0)
                    continue;

                // Don't move over logs
                if (FilenameUtils.getExtension(file.getPath()).equals("log"))
                    continue;

                // If on Windows (not the production server use-case), Windows cannot do symlink without admin permissions so
                // just copy over the files. Also, if the file is a clib file, special handling is required so just copy it over.
                if ((!DEBUG_SYMLINKS_ON_WINDOWS && SystemUtils.IS_OS_WINDOWS) || FilenameUtils.getExtension(file.getPath()).equals("clib"))
                {
                    Files.copy(filePath, targetPath, REPLACE_EXISTING);
                    fcs.fireFileCreateEvent(targetPath, user, c);

                    continue;
                }

                // Symbolic link should move the target file over. This would be for a re-copy to public.
                if (Files.isSymbolicLink(filePath))
                {
                    Path oldPath = Files.readSymbolicLink(filePath);
                    Files.move(oldPath, targetPath, REPLACE_EXISTING);
                    fcs.fireFileCreateEvent(targetPath, user, c);

                    fireSymlinkUpdate(oldPath, targetPath);
                    _log.info("File moved from " + oldPath + " to " + targetPath);

                    Path symlink = Files.createSymbolicLink(oldPath, targetPath);
                    _log.info("Symlink created: " + symlink);
                }
                else
                {
                    Files.move(filePath, targetPath, REPLACE_EXISTING);
                    fcs.fireFileCreateEvent(targetPath, user, c);

                    Files.createSymbolicLink(filePath, targetPath);
                    fireSymlinkUpdate(filePath, targetPath);
                }

                if (createSourceSymLinks)
                {
                    Path symlink = Files.createSymbolicLink(filePath, targetPath);
                    _log.info("Symlink created: " + symlink);
                }
            }
        }
    }

    private void verifyFileTreeSymlinks(File source, Map<String, String> linkInvalidTarget, Map<String, String> linkWithSymlinkTarget) throws IOException
    {
        for (File file : Objects.requireNonNull(source.listFiles()))
        {
            if (file.isDirectory())
            {
                verifyFileTreeSymlinks(file, linkInvalidTarget, linkWithSymlinkTarget);
            }
            else
            {
                Path filePath = file.toPath();
                if (Files.isSymbolicLink(filePath))
                {
                    // Verify target file exists and is not a symbolic link
                    Path targetPath = Files.readSymbolicLink(filePath);
                    if (!FileUtil.isFileAndExists(targetPath))
                    {
                        linkInvalidTarget.put(filePath.toString(), targetPath.toString());
                    }
                    else if (Files.isSymbolicLink(targetPath))
                    {
                        linkWithSymlinkTarget.put(filePath.toString(), targetPath.toString());
                    }
                }
            }
        }
    }

    public boolean verifySymlinks() throws IOException
    {
        Map<String, String> linkInvalidTarget = new HashMap<>();
        Map<String, String> linkWithSymlinkTarget = new HashMap<>();
        Set<Container> containers = ContainerManager.getAllChildrenWithModule(ContainerManager.getRoot(), ModuleLoader.getInstance().getModule(PanoramaPublicModule.class));
        for (Container container : containers)
        {
            Set<Container> tree = ContainerManager.getAllChildren(container);
            for (Container node : tree)
            {
                FileContentService fcs = FileContentService.get();
                if (null != fcs && null != fcs.getFileRoot(node))
                {
                    File root = fcs.getFileRoot(node);
                    if (null != root)
                    {
                        verifyFileTreeSymlinks(root, linkInvalidTarget, linkWithSymlinkTarget);
                    }
                }
            }
        }

        if(linkInvalidTarget.size() > 0)
            _log.error("Symlinks with invalid targets: " + linkInvalidTarget);

        if(linkWithSymlinkTarget.size() > 0)
            _log.error("Symlinks targeting symlinks: " + linkWithSymlinkTarget);

        return linkInvalidTarget.size() == 0 && linkWithSymlinkTarget.size() == 0;
    }

}
