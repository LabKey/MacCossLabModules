package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;

import java.util.Date;

public class DatasetStatus  extends DbEntity
{
    private ShortURLRecord _shortUrl;
    private Date _lastReminderDate;
    private Date _extensionRequestedDate;
    private Date _deletionRequestedDate;

    public ShortURLRecord getShortUrl()
    {
        return _shortUrl;
    }

    public void setShortUrl(ShortURLRecord shortUrl)
    {
        _shortUrl = shortUrl;
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
}
