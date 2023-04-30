package org.labkey.panoramapublic;

import java.nio.file.Path;

public interface SymlinkHandler
{
    void handleSymlink(Path link, Path target);
}
