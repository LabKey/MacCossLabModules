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
package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.catalog.CatalogImageAttachmentParent;
import org.labkey.panoramapublic.query.ExperimentAnnotationsManager;

public class CatalogEntry extends DbEntity
{
    private ShortURLRecord _shortUrl;
    private String _imageFileName;
    private String _description;
    private Boolean _approved;

    public ShortURLRecord getShortUrl()
    {
        return _shortUrl;
    }

    public void setShortUrl(ShortURLRecord shortUrl)
    {
        _shortUrl = shortUrl;
    }

    public String getImageFileName()
    {
        return _imageFileName;
    }

    public void setImageFileName(String imageFileName)
    {
        _imageFileName = imageFileName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Boolean getApproved()
    {
        return _approved;
    }

    public void setApproved(Boolean approved)
    {
        _approved = approved;
    }

    public @Nullable Attachment getAttachment()
    {
        if (_shortUrl != null && _imageFileName != null)
        {
            ExperimentAnnotations expAnnotations = ExperimentAnnotationsManager.getExperimentForShortUrl(_shortUrl);
            return expAnnotations != null
                    ? AttachmentService.get().getAttachment(new CatalogImageAttachmentParent(_shortUrl, expAnnotations.getContainer()), _imageFileName)
                    : null;
        }
        return null;
    }

    public static String getStatusText(Boolean status)
    {
        return status == null ? "Pending approval" : status ? "Approved" : "Rejected";
    }
}
