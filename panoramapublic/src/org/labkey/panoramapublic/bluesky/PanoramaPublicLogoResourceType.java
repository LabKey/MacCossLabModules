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
