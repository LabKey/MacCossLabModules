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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.panoramapublic.model.Journal;
import org.labkey.panoramapublic.query.JournalManager;

public class PanoramaPublicLogoAttachmentParent extends ContainerManager.ContainerParent
{
    private PanoramaPublicLogoAttachmentParent(Container c)
    {
        super(c);
    }

    @Nullable
    public static PanoramaPublicLogoAttachmentParent get()
    {
        // Associate with the Panorama Public project, if it exists
        Journal panoramaPublic = JournalManager.getJournal(JournalManager.PANORAMA_PUBLIC);
        if (panoramaPublic != null)
        {
            Container project = panoramaPublic.getProject();
            return new PanoramaPublicLogoAttachmentParent(project);
        }
        return null;
    }

    @Override
    public @NotNull AttachmentParentType getAttachmentParentType()
    {
        return PanoramaPublicLogoResourceType.get();
    }
}
