package org.labkey.panoramapublic;

import java.io.IOException;
import java.nio.file.Path;

public interface PanoramaPublicSymlinkHandler
{
    void handleSymlink(Path link, Path target) throws IOException;
}
