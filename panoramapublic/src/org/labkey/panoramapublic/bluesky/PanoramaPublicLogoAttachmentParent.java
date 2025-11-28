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
