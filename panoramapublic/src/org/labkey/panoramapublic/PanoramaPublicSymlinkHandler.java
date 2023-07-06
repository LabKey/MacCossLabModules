package org.labkey.panoramapublic;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.IOException;
import java.nio.file.Path;

public interface PanoramaPublicSymlinkHandler
{
    void handleSymlink(Path link, Path target, Container container, User user) throws IOException;
}
