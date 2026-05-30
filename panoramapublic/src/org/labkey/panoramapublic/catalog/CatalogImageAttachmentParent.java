/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.panoramapublic.catalog;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentType;
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
    public @NotNull AttachmentParentType getAttachmentParentType()
    {
        return CatalogImageAttachmentType.get();
    }
}
