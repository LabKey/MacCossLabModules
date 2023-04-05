package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.files.FileListener;
import org.labkey.api.security.User;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;

public class PanoramaPublicFileListener implements FileListener
{

    @Override
    public String getSourceName()
    {
        return null;
    }

    @Override
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container)
    {

    }

    @Override
    public void fileReplaced(@NotNull File replaced, @Nullable User user, @Nullable Container container)
    {
        // If copying in a new file ensure to remove listener if there was previously a symlink with same name
        if (!Files.isSymbolicLink(replaced.toPath()))
            PanoramaPublicManager.get().removeSymlinkListener(replaced.toPath().toString());
    }

    @Override
    public int fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        // Update any symlinks targeting the file
        PanoramaPublicManager.get().fireSymlinkUpdate(src.toPath(), dest.toPath());

        // If the file is a symlink, update the location
        if (Files.isSymbolicLink(src.toPath()))
        {
            PanoramaPublicManager.get().fireSymlinkLocationUpdate(src.toPath(), dest.toPath());
        }

        return 0;
    }

    @Override
    public void fileDeleted(@NotNull File deleted, @Nullable User user, @Nullable Container container)
    {
        if (Files.isSymbolicLink(deleted.toPath()))
        {
            PanoramaPublicManager.get().fireSymlinkDeleted(deleted.toPath());
        }
    }

    @Override
    public Collection<File> listFiles(@Nullable Container container)
    {
        return null;
    }

    @Override
    public SQLFragment listFilesQuery()
    {
        return null;
    }
}
