package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ShortURLRecord;

import java.time.ZoneId;
import java.util.Date;
import java.time.LocalDate;

public class DatasetStatus  extends DbEntity
{
    private ShortURLRecord _shortUrl;
    private Date _reminderDate;
    private Date _extensionRequestedDate;
    private Date _deletionRequestedDate;

    public static final int EXTENSION_VALID_MONTHS = 6; // 6 months

    public ShortURLRecord getShortUrl()
    {
        return _shortUrl;
    }

    public void setShortUrl(ShortURLRecord shortUrl)
    {
        _shortUrl = shortUrl;
    }

    public Date getReminderDate()
    {
        return _reminderDate;
    }

    public void setReminderDate(Date reminderDate)
    {
        _reminderDate = reminderDate;
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

    public void setDeletionRequestedDate(Date deletionRequestedDate)
    {
        _deletionRequestedDate = deletionRequestedDate;
    }

    public boolean deletionRequested()
    {
        return _deletionRequestedDate != null;
    }

    public boolean isExtensionValid()
    {
        if (_extensionRequestedDate == null)
        {
            return false;
        }

        LocalDate extensionDate = _extensionRequestedDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate extensionValidStartDate = LocalDate.now().minusMonths(EXTENSION_VALID_MONTHS);

        return extensionDate.isAfter(extensionValidStartDate);
    }

    public boolean isLastReminderRecent()
    {
        if (_reminderDate == null)
        {
            return false;
        }

        LocalDate reminderDate = _reminderDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate extensionValidStartDate = LocalDate.now().minusMonths(EXTENSION_VALID_MONTHS);

        return reminderDate.isAfter(extensionValidStartDate);
    }

    public @Nullable Date extensionValidUntil()
    {
        if (_extensionRequestedDate == null)
        {
            return null;
        }

        return Date.from(
                _extensionRequestedDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .plusMonths(EXTENSION_VALID_MONTHS)
                        .toInstant()
        );
    }
}
