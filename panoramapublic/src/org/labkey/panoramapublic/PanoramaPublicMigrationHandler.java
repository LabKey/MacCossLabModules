/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.migration.DatabaseMigrationConfiguration;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.util.GUID;
import org.labkey.panoramapublic.bluesky.PanoramaPublicLogoManager;
import org.labkey.panoramapublic.bluesky.PanoramaPublicLogoResourceType;
import org.labkey.panoramapublic.catalog.CatalogImageAttachmentType;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class PanoramaPublicMigrationHandler extends DefaultMigrationSchemaHandler
{
    PanoramaPublicMigrationHandler()
    {
        super(PanoramaPublicSchema.getSchema());
    }

    @Override
    public @NotNull Collection<AttachmentParentType> getAttachmentTypes()
    {
        return List.of(CatalogImageAttachmentType.get());
    }

    @Override
    public Collection<AttachmentParentType> copyAttachments(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, @Nullable Set<GUID> copyContainers)
    {
        Collection<AttachmentParentType> ret = new LinkedList<>(super.copyAttachments(configuration, sourceSchema, targetSchema, copyContainers));

        // Logos are attached to their container, so select them by name instead
        SQLFragment sql = SQLFragment.unsafe("Parent");

        if (copyContainers != null)
            sql.appendInClause(copyContainers, sourceSchema.getSqlDialect());
        else
            sql.unsafeAppend(" IN (SELECT EntityId FROM core.Containers)");

        sql.unsafeAppend(" AND Name LIKE ?", PanoramaPublicLogoManager.LOGO_FILE_PREFIX + "%");
        ret.addAll(copyAttachments(configuration, new SQLClause(sql), PanoramaPublicLogoResourceType.get()));

        return ret;
    }
}
