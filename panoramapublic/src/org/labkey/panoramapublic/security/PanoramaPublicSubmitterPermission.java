package org.labkey.panoramapublic.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class PanoramaPublicSubmitterPermission extends AbstractPermission
{
    public PanoramaPublicSubmitterPermission()
    {
        super("Make submitted data public", "Allows a user to make their submitted data public, and add publication information.");
    }
}
