package org.labkey.targetedms.security;

import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AbstractRole;

/**
 * User: vsharma
 * Date: 9/2/2014
 * Time: 2:05 PM
 */
public class CopyTargetedMSExperimentRole extends AbstractRole
{
    public CopyTargetedMSExperimentRole()
    {
        super("Copy Experiment", "Can copy a TargetedMS experiment along with the associated runs, as well as, several folder properties and artifacts.",
                ReadPermission.class,
                FolderExportPermission.class
        );
    }

    // This role should not be displayed in a permissions management interface.
    @Override
    public boolean isAssignable()
    {
        return false;
    }
}
