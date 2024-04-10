package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.security.User;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

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
        PanoramaPublicSymlinkManager.get().fireSymlinkUpdate(src.toPath(), dest.toPath(), container, user);

        ExpData data = ExperimentService.get().getExpDataByURL(src, null);
        if (null != data)
            data.setDataFileURI(dest.toURI());

        return 0;
    }

    @Override
    public void fileDeleted(@NotNull Path deleted, @Nullable User user, @Nullable Container container)
    {
        ExpData data = ExperimentService.get().getExpDataByURL(deleted, container);

        if (null != data)
            data.delete(user);
    }

    @Override
    public Collection<File> listFiles(@Nullable Container container)
    {
        return Collections.emptyList();
    }

    @Override
    public SQLFragment listFilesQuery()
    {
        return null;
    }
}
