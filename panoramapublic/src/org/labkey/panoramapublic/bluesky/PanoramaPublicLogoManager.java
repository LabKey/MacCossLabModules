package org.labkey.panoramapublic.bluesky;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;

import java.io.IOException;
import java.util.Collections;

public class PanoramaPublicLogoManager
{
    public static String LOGO_FILE_PREFIX = "PanoramaPublicLogo-NewData";
    @Nullable
    public static Attachment getNewDataLogo()
    {
        AttachmentParent ap = PanoramaPublicLogoAttachmentParent.get();
        if (ap == null) return null;

        return AttachmentService.get().getAttachments(ap).stream()
                .filter(a -> a.getName() != null && LOGO_FILE_PREFIX.equals(FileUtil.getBaseName(a.getName())))
                .findFirst()
                .orElse(null);
    }

    public static void saveNewDataLogo(AttachmentFile file, User user) throws IOException
    {
        String logoFileName = getLogoFileName(file.getFilename(), LOGO_FILE_PREFIX);
        AttachmentFile attachmentFile = new InputStreamAttachmentFile(file.openInputStream(), logoFileName);
        PanoramaPublicLogoAttachmentParent ap = PanoramaPublicLogoAttachmentParent.get();
        AttachmentService svc = AttachmentService.get();
        deleteExistingNewDataLogo(svc, user);
        AttachmentService.get().addAttachments(ap, Collections.singletonList(attachmentFile), user);
    }

    private static String getLogoFileName(String name, String fileNamePrefix)
    {
        String extension = FileUtil.getExtension(name);
        if (extension == null)
        {
            throw new IllegalArgumentException("Unable to get file extension from logo file - " + name);
        }

        return fileNamePrefix + "." + extension;
    }

    public static void deleteExistingNewDataLogo(User user)
    {
        deleteExistingNewDataLogo(AttachmentService.get(), user);
    }

    private static void deleteExistingNewDataLogo(AttachmentService svc, User user)
    {
        PanoramaPublicLogoAttachmentParent ap = PanoramaPublicLogoAttachmentParent.get();
        if (ap == null) return;

        for (Attachment attachment : svc.getAttachments(ap))
        {
            if (attachment.getName().startsWith(PanoramaPublicLogoManager.LOGO_FILE_PREFIX))
            {
                svc.deleteAttachment(ap, attachment.getName(), user);
            }
        }
    }
}
