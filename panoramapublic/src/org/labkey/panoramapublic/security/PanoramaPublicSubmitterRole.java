package org.labkey.panoramapublic.security;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;
import org.labkey.panoramapublic.query.JournalManager;

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
        if (super.isApplicable(policy, resource)) // Superclass verifies that the resource is a Container.
        {
            // Show the role on the permissions page of subfolders of the Panorama Public project that have an experiment.
            Container project = ((Container)resource).getProject();
            return project != null
                    && JournalManager.isJournalProject(project)
                    && ExperimentAnnotationsManager.getExperimentInContainer((Container) resource) != null;
        }
        return false;
    }
}
