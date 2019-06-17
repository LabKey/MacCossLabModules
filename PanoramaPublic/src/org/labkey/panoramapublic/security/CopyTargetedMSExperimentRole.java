/*
 * Copyright (c) 2014-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.targetedms.security;

import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
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
        excludeGuests();
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return false; // Do not show in the permission UI
    }
}
