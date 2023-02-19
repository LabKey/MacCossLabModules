package org.labkey.panoramapublic.catalog;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class CatalogImageAttachmentType implements AttachmentType
{
    private static final CatalogImageAttachmentType INSTANCE = new CatalogImageAttachmentType();

    public static CatalogImageAttachmentType get()
    {
        return INSTANCE;
    }

    private CatalogImageAttachmentType()
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
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CoreSchema.getInstance().getTableInfoShortURL(), "shorUrls").append(")");
    }
}
