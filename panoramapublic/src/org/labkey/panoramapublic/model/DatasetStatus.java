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
package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;
import org.labkey.panoramapublic.ncbi.NcbiConstants;

import java.util.Date;

public class DatasetStatus  extends DbEntity
{

    private int _experimentAnnotationsId;
    private Date _lastReminderDate;
    private Date _extensionRequestedDate;
    private Date _deletionRequestedDate;
    private String _potentialPublicationId;
    private String _publicationType;
    private String _publicationMatchInfo;
    private String _citation;
    private Date _userDismissedPublication;

    public int getExperimentAnnotationsId()
    {
        return _experimentAnnotationsId;
    }

    public void setExperimentAnnotationsId(int experimentAnnotationsId)
    {
        _experimentAnnotationsId = experimentAnnotationsId;
    }

    public Date getLastReminderDate()
    {
        return _lastReminderDate;
    }

    public void setLastReminderDate(Date lastReminderDate)
    {
        _lastReminderDate = lastReminderDate;
    }

    public Date getExtensionRequestedDate()
    {
        return _extensionRequestedDate;
    }

    public void setExtensionRequestedDate(Date extensionRequestedDate)
    {
        _extensionRequestedDate = extensionRequestedDate;
    }

    public Date getDeletionRequestedDate()
    {
        return _deletionRequestedDate;
    }

    public @Nullable String getDeletionRequestedDateFormatted()
    {
        return PrivateDataReminderSettings.format(_deletionRequestedDate);
    }

    public void setDeletionRequestedDate(Date deletionRequestedDate)
    {
        _deletionRequestedDate = deletionRequestedDate;
    }

    public boolean deletionRequested()
    {
        return _deletionRequestedDate != null;
    }

    public boolean extensionRequested()
    {
        return _extensionRequestedDate != null;
    }

    public boolean reminderSent()
    {
        return _lastReminderDate != null;
    }

    public String getPotentialPublicationId()
    {
        return _potentialPublicationId;
    }

    public void setPotentialPublicationId(String potentialPublicationId)
    {
        _potentialPublicationId = potentialPublicationId;
    }

    public String getPublicationType()
    {
        return _publicationType;
    }

    public void setPublicationType(String publicationType)
    {
        _publicationType = publicationType;
    }

    public String getPublicationIdLabel()
    {
        NcbiConstants.DB type = NcbiConstants.DB.fromString(_publicationType);
        String label = type == null ? (_publicationType != null ? _publicationType : "") : type.name();
        return label + " ID " + getPotentialPublicationId();
    }

    public String getPublicationMatchInfo()
    {
        return _publicationMatchInfo;
    }

    public void setPublicationMatchInfo(String publicationMatchInfo)
    {
        _publicationMatchInfo = publicationMatchInfo;
    }

    public String getCitation()
    {
        return _citation;
    }

    public void setCitation(String citation)
    {
        _citation = citation;
    }

    public Date getUserDismissedPublication()
    {
        return _userDismissedPublication;
    }

    public void setUserDismissedPublication(Date userDismissedPublication)
    {
        _userDismissedPublication = userDismissedPublication;
    }

    public boolean isPublicationDismissed(String publicationId)
    {
        return getUserDismissedPublication() != null && publicationId != null && publicationId.equals(getPotentialPublicationId());
    }
}
