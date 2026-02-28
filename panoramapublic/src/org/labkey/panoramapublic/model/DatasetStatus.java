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
    private Boolean _userDismissedPublication;

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

    public String getPublicationLabel()
    {
        NcbiConstants.DB type = NcbiConstants.DB.fromString(_publicationType);
        if (type == null)
        {
            return _publicationType != null ? _publicationType : "";
        }
        return type.getLabel();
    }

    public String getPublicationMatchInfo()
    {
        return _publicationMatchInfo;
    }

    public void setPublicationMatchInfo(String publicationMatchInfo)
    {
        _publicationMatchInfo = publicationMatchInfo;
    }

    public Boolean getUserDismissedPublication()
    {
        return _userDismissedPublication;
    }

    public void setUserDismissedPublication(Boolean userDismissedPublication)
    {
        _userDismissedPublication = userDismissedPublication;
    }
}
