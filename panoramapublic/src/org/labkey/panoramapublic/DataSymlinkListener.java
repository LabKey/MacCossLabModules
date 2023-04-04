package org.labkey.panoramapublic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class contains the location and target of a symlink for a file that was moved from a private project to
 * Panorama Public. There are a number of helpers to update the target of the symlink. These objects are registered
 * in PanoramaPublicManager.registerSymlinkListener to be called upon various updates.
 */
public class DataSymlinkListener
{
    private Path _target;
    private Path _symlink;

    public DataSymlinkListener(Path symlink, Path target)
    {
        _symlink = symlink;
        _target = target;
    }

    public void update(Path oldTarget, Path newTarget)
    {
        if (!_target.equals(oldTarget))
            return;

        try
        {
            Files.delete(_symlink);
            _symlink = Files.createSymbolicLink(_symlink, newTarget);
            _target = newTarget;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isSymlinkInContainer(String container)
    {
        String containerPath = normalizeContainerPath(container);
        return (String.valueOf(_symlink).contains(containerPath));
    }

    private String normalizeContainerPath(String path)
    {
        File file = new File(path);
        if (file.isAbsolute() || path.startsWith(File.separator))
            return path + File.separator;

        return File.separator + path + File.separator;
    }

    public void updateTargetContainer(String oldContainer, String newContainer)
    {
        String containerPath = normalizeContainerPath(oldContainer);
        if (String.valueOf(_target).contains(containerPath))
        {
            Path newTarget = Path.of(_target.toString().replace(containerPath, normalizeContainerPath(newContainer)));
            try
            {
                Files.delete(_symlink);
                _symlink = Files.createSymbolicLink(_symlink, newTarget);
                _target = newTarget;
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean deleteTargetContainer(String container)
    {
        String containerPath = normalizeContainerPath(container);
        if (String.valueOf(_target).contains(containerPath))
        {
            try
            {
                Files.delete(_symlink);
                return true;
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
}
