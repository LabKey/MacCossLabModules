package org.labkey.panoramapublic.security;

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AbstractRole;

public class PanoramaPublicSubmitterRole extends AbstractRole
{
    public PanoramaPublicSubmitterRole()
    {
        super("Panorama Public Submitter", "Can make their data public, and add publication information.",
                ReadPermission.class,
                PanoramaPublicSubmitterPermission.class
        );
        excludeGuests();
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return false; // Do not show in the permission UI
    }
}
