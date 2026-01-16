package org.labkey.panoramapublic.catalog;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class CatalogImageAttachmentType implements AttachmentParentType
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
        return "CatalogImage";
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        return new SQLFragment("SELECT EntityId, ShortUrl AS Description FROM ").append(CoreSchema.getInstance().getTableInfoShortURL());
    }
}
