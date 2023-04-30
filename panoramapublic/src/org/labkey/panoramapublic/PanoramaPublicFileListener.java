package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.files.FileListener;
import org.labkey.api.security.User;

import java.io.File;
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
    public int fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        // Update any symlinks targeting the file
        PanoramaPublicManager.get().fireSymlinkUpdate(src.toPath(), dest.toPath());
        return 0;
    }

    @Override
    public void fileDeleted(@NotNull File deleted, @Nullable User user, @Nullable Container container)
    {
        // TODO: Handle deleting target files
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
