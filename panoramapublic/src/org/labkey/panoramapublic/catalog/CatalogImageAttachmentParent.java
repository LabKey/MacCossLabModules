package org.labkey.panoramapublic.catalog;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.view.ShortURLRecord;

public class CatalogImageAttachmentParent implements AttachmentParent
{
    private final String _entityId;
    private final String _containerId;

    public CatalogImageAttachmentParent(@NotNull ShortURLRecord shortUrl, @NotNull Container container)
    {
        _entityId = shortUrl.getEntityId().toString();
        _containerId = container.getId();
    }
    @Override
    public String getEntityId()
    {
        return _entityId;
    }

    @Override
    public String getContainerId()
    {
        return _containerId;
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return CatalogImageAttachmentType.get();
    }
}
