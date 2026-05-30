/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.panoramapublic.bluesky;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class PanoramaPublicLogoResourceType implements AttachmentParentType
{
    private static final PanoramaPublicLogoResourceType INSTANCE = new PanoramaPublicLogoResourceType();

    public static PanoramaPublicLogoResourceType get()
    {
        return INSTANCE;
    }

    private PanoramaPublicLogoResourceType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return "PanoramaPublicLogoResource";
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ")
            .append(CoreSchema.getInstance().getTableInfoContainers(), "c").append(")")
            .append(" AND (")
            .append(documentNameColumn).append(" LIKE ")
            .appendStringLiteral(PanoramaPublicLogoManager.LOGO_FILE_PREFIX + "%", CoreSchema.getInstance().getSqlDialect())
            .append(") ");
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        return PARENT_CONTAINER_SQL;
    }
}
