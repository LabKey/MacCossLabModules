package org.labkey.panoramapublic.bluesky;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class PanoramaPublicLogoResourceType implements AttachmentType
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
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ")
                .append(CoreSchema.getInstance().getTableInfoContainers(), "c").append(")")
                .append(" AND (")
                .append(documentNameColumn)
                .append(" LIKE '" + PanoramaPublicLogoManager.LOGO_FILE_PREFIX + "%' )");
    }
}
