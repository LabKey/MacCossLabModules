package org.labkey.panoramapublic.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.panoramapublic.message.PrivateDataReminderSettings;

import java.time.ZoneId;
import java.util.Date;
import java.time.LocalDate;

public class DatasetStatus  extends DbEntity
{
    private ShortURLRecord _shortUrl;
    private Date _lastReminderDate;
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
        return format(_deletionRequestedDate);
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

    public boolean isExtensionCurrent(PrivateDataReminderSettings settings)
    {
        if (_extensionRequestedDate == null)
        {
            return false;
        }

        LocalDate extensionDate = _extensionRequestedDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate extensionValidStartDate = LocalDate.now().minusMonths(settings.getExtensionLength());

        return extensionDate.isAfter(extensionValidStartDate);
    }

    public boolean isLastReminderRecent(PrivateDataReminderSettings settings)
    {
        if (_lastReminderDate == null)
        {
            return false;
        }

        LocalDate reminderDate = _lastReminderDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate reminderValidStartDate = LocalDate.now().minusMonths(settings.getReminderFrequency());

        return reminderDate.isAfter(reminderValidStartDate);
    }

    public @Nullable Date extensionValidUntil(PrivateDataReminderSettings settings)
    {
        if (_extensionRequestedDate == null)
        {
            return null;
        }

        return Date.from(
                _extensionRequestedDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .plusMonths(settings.getExtensionLength())
                        .toInstant()
        );
    }

    public @Nullable String extensionValidUntilFormatted(PrivateDataReminderSettings settings)
    {
        return format(extensionValidUntil(settings));
    }

    private @Nullable String format(Date date)
    {
        return date != null ? DateUtil.formatDateTime(date, "MMMM d, yyyy") : null;
    }
}
