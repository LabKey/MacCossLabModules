package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;

import java.util.Date;

public class DatasetStatus  extends DbEntity
{
    private int _experimentAnnotationsId;
    private Date _lastReminderDate;
    private Date _extensionRequestedDate;
    private Date _deletionRequestedDate;
    private String _potentialPubMedId;
    private String _pubMedSearchStrategy;
    private Boolean _userDismissedPubMed;

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

    public String getPotentialPubMedId()
    {
        return _potentialPubMedId;
    }

    public void setPotentialPubMedId(String potentialPubMedId)
    {
        _potentialPubMedId = potentialPubMedId;
    }

    public String getPubMedSearchStrategy()
    {
        return _pubMedSearchStrategy;
    }

    public void setPubMedSearchStrategy(String pubMedSearchStrategy)
    {
        _pubMedSearchStrategy = pubMedSearchStrategy;
    }

    public Boolean getUserDismissedPubMed()
    {
        return _userDismissedPubMed;
    }

    public void setUserDismissedPubMed(Boolean userDismissedPubMed)
    {
        _userDismissedPubMed = userDismissedPubMed;
    }
}
