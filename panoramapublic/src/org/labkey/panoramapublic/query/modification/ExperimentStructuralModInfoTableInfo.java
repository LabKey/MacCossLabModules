/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.query.modification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdQueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.panoramapublic.PanoramaPublicManager;
import org.labkey.panoramapublic.PanoramaPublicSchema;
import org.labkey.panoramapublic.query.ContainerJoin;
import org.labkey.panoramapublic.query.ModificationInfoManager;
import org.labkey.panoramapublic.query.PanoramaPublicTable;

public class ExperimentStructuralModInfoTableInfo extends PanoramaPublicTable
{
    public ExperimentStructuralModInfoTableInfo(PanoramaPublicSchema schema, ContainerFilter cf)
    {
        super(PanoramaPublicManager.getTableInfoExperimentStructuralModInfo(), schema, cf, ContainerJoin.ExpAnnotJoin);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new RowIdQueryUpdateService<ExperimentStructuralModInfo>(this)
        {
            @Override
            protected ExperimentStructuralModInfo createNewBean()
            {
                return new ExperimentStructuralModInfo();
            }

            @Override
            public ExperimentStructuralModInfo get(User user, Container container, int key)
            {
                return ModificationInfoManager.getStructuralModInfo(key, container);
            }

            @Override
            public void delete(User user, Container container, int key)
            {
                ModificationInfoManager.deleteStructuralModInfo(key, container, user);
            }

            @Override
            protected ExperimentStructuralModInfo insert(User user, Container container, ExperimentStructuralModInfo bean)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected ExperimentStructuralModInfo update(User user, Container container, ExperimentStructuralModInfo bean, Integer oldKey)
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
