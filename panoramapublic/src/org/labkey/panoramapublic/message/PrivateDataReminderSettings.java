package org.labkey.panoramapublic.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.DateUtil;
import org.labkey.panoramapublic.model.DatasetStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class PrivateDataReminderSettings
{
    public static final String PROP_PRIVATE_DATA_REMINDER = "Panorama Public private data reminder settings";
    public static final String PROP_ENABLE_REMINDER = "Enable private data reminder";
    public static final String PROP_DELAY_UNTIL_FIRST_REMINDER = "Delay until first reminder (months)";
    public static final String PROP_REMINDER_FREQUENCY = "Reminder frequency (months)";
    public static final String PROP_EXTENSION_LENGTH = "Extension duration (months)";

    private static final boolean DEFAULT_ENABLE_REMINDERS = false;
    private static final int DEFAULT_DELAY_UNTIL_FIRST_REMINDER = 12; // Send the first reminder after the data has been private for a year.
    private static final int DEFAULT_REMINDER_FREQUENCY = 1; // Send reminders once a month, unless extension or deletion was requested.
    private static final int DEFAULT_EXTENSION_LENGTH = 6; // Private status of a dataset can be extended by 6 months.

    private static final String DATE_FORMAT_PATTERN = "MMMM d, yyyy";

    private boolean _enableReminders;
    private int _delayUntilFirstReminder;
    private int _reminderFrequency;
    private int _extensionLength;

    public static PrivateDataReminderSettings get()
    {
        PropertyManager.WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(PROP_PRIVATE_DATA_REMINDER, false);

        PrivateDataReminderSettings settings = new PrivateDataReminderSettings();
        if(settingsMap != null)
        {
            boolean enableReminders = settingsMap.get(PROP_ENABLE_REMINDER) == null
                    ? DEFAULT_ENABLE_REMINDERS
                    : Boolean.valueOf(settingsMap.get(PROP_ENABLE_REMINDER));
            settings.setEnableReminders(enableReminders);

            int delayUntilFirstReminder = settingsMap.get(PROP_DELAY_UNTIL_FIRST_REMINDER) == null
                    ? DEFAULT_DELAY_UNTIL_FIRST_REMINDER
                    : Integer.valueOf(settingsMap.get(PROP_DELAY_UNTIL_FIRST_REMINDER));
            settings.setDelayUntilFirstReminder(delayUntilFirstReminder);

            int reminderFrequency = settingsMap.get(PROP_REMINDER_FREQUENCY) == null
                    ? DEFAULT_REMINDER_FREQUENCY
                    : Integer.valueOf(settingsMap.get(PROP_REMINDER_FREQUENCY));
            settings.setReminderFrequency(reminderFrequency);

            int extensionLength = settingsMap.get(PROP_EXTENSION_LENGTH) == null
                    ? DEFAULT_EXTENSION_LENGTH
                    : Integer.valueOf(settingsMap.get(PROP_EXTENSION_LENGTH));
            settings.setExtensionLength(extensionLength);
        }
        else
        {
            settings.setEnableReminders(DEFAULT_ENABLE_REMINDERS);
            settings.setDelayUntilFirstReminder(DEFAULT_DELAY_UNTIL_FIRST_REMINDER);
            settings.setReminderFrequency(DEFAULT_REMINDER_FREQUENCY);
            settings.setExtensionLength(DEFAULT_EXTENSION_LENGTH);
        }

        return settings;
    }

    public static void save(PrivateDataReminderSettings settings)
    {
        PropertyManager.WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(PROP_PRIVATE_DATA_REMINDER, true);
        settingsMap.put(PROP_ENABLE_REMINDER, String.valueOf(settings.isEnableReminders()));
        settingsMap.put(PROP_DELAY_UNTIL_FIRST_REMINDER, String.valueOf(settings.getDelayUntilFirstReminder()));
        settingsMap.put(PROP_REMINDER_FREQUENCY, String.valueOf(settings.getReminderFrequency()));
        settingsMap.put(PROP_EXTENSION_LENGTH, String.valueOf(settings.getExtensionLength()));
        settingsMap.save();
    }

    public void setEnableReminders(boolean enableReminders)
    {
        _enableReminders = enableReminders;
    }

    public void setExtensionLength(int extensionLength)
    {
        _extensionLength = extensionLength;
    }

    public void setReminderFrequency(int reminderFrequency)
    {
        _reminderFrequency = reminderFrequency;
    }

    public boolean isEnableReminders()
    {
        return _enableReminders;
    }

    public int getExtensionLength()
    {
        return _extensionLength;
    }

    public int getReminderFrequency()
    {
        return _reminderFrequency;
    }

    public int getDelayUntilFirstReminder()
    {
        return _delayUntilFirstReminder;
    }

    public void setDelayUntilFirstReminder(int delayUntilFirstReminder)
    {
        _delayUntilFirstReminder = delayUntilFirstReminder;
    }

    public @Nullable Date getReminderValidUntilDate(@NotNull DatasetStatus status)
    {
        return status.getLastReminderDate() == null ? null : addMonths(status.getLastReminderDate(), getReminderFrequency());
    }

    public boolean isLastReminderRecent(@NotNull DatasetStatus status)
    {
        return isDateInFuture(getReminderValidUntilDate(status));
    }

    public @Nullable Date getExtensionValidUntilDate(@NotNull DatasetStatus status)
    {
        return status.getExtensionRequestedDate() == null ? null : addMonths(status.getExtensionRequestedDate(), getExtensionLength());
    }

    public boolean isExtensionValid(@NotNull DatasetStatus status)
    {
        return isDateInFuture(getExtensionValidUntilDate(status));
    }

    public @Nullable String extensionValidUntilFormatted(@NotNull DatasetStatus status)
    {
        Date date = getExtensionValidUntilDate(status);
        return date != null ? format(date) : null;
    }

    public static String format(@NotNull Date date)
    {
        return DateUtil.formatDateTime(date, DATE_FORMAT_PATTERN);
    }

    private static boolean isDateInFuture(@Nullable Date date)
    {
        return isDateInFuture(date, new Date());
    }

    private static boolean isDateInFuture(@Nullable Date date, @NotNull Date currentTime)
    {
        return date != null && date.after(currentTime);
    }

    private static Date addMonths(@NotNull Date date, int months)
    {
        return Date.from(dateToZonedDateTime(date).plusMonths(months).toInstant());
    }

    private static ZonedDateTime dateToZonedDateTime(@NotNull Date date)
    {
        return date.toInstant().atZone(ZoneId.systemDefault());
    }

    private boolean isExtensionValidAsOf(@NotNull DatasetStatus status, @NotNull Date currentTime)
    {
        Date extensionValidUntil = getExtensionValidUntilDate(status);
        return isDateInFuture(extensionValidUntil, currentTime);
    }

    public boolean isLastReminderRecentAsOf(@NotNull DatasetStatus status, @NotNull Date currentTime)
    {
        Date reminderValidUntil = getReminderValidUntilDate(status);
        return isDateInFuture(reminderValidUntil, currentTime);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIsExtensionCurrentScenarios()
        {
            DatasetStatus datasetStatus = new DatasetStatus();
            PrivateDataReminderSettings settings = createTestSettingsExtensionLength(6);

            // No extension requested (extensionRequestDate is null)
            assertFalse("Should return false; extensionRequestDate is null", settings.isExtensionValid(datasetStatus));

            // Extension request is within the configured extension period
            testExtensionIsValid(settings, -3);

            // Extension request has expired
            testExtensionIsExpired(settings, -7);

            // Extension request expires exactly now, not in the future.
            testExtensionIsExpiredAsOf(settings, (settings.getExtensionLength() * -1), 0);

            // Extension expires in 1 minute - still current
            testExtensionIsValidAsOf(settings, (settings.getExtensionLength() * -1), 1);

            // Extension expired 1 minute ago
            testExtensionIsExpiredAsOf(settings, (settings.getExtensionLength() * -1), -1);
        }

        @Test
        public void testIsLastReminderRecentScenarios()
        {
            DatasetStatus datasetStatus = new DatasetStatus();
            PrivateDataReminderSettings settings = createTestSettingsReminderFrequency(2);

            // No reminder sent yet (lastReminderDate is null)
            assertFalse("Should return false; lastReminderDate is null", settings.isLastReminderRecent(datasetStatus));

            // Last reminder sent within the reminder frequency period
            testReminderIsRecent(settings, -15);

            // Reminder is old
            testReminderIsOld(settings, -70);

            // Reminder is old now
            testReminderIsOldAsOf(settings, (settings.getReminderFrequency() * -1), 0);

            // Reminder gets old in 1 minute - still current
            testReminderIsRecentAsOf(settings, (settings.getReminderFrequency() * -1), 1);

            // Reminder became old 1 minute ago
            testReminderIsOldAsOf(settings, (settings.getReminderFrequency() * -1), -1);

            // Setting the reminder frequency to 0 will return false for isReminderRecent, unless lastReminderDate is set in the future.
            settings = createTestSettingsReminderFrequency(0);
            datasetStatus = new DatasetStatus();
            assertFalse("Should return false; lastReminderDate is null", settings.isLastReminderRecent(datasetStatus));
            testReminderIsOld(settings, -15);
            testReminderIsOldAsOf(settings, (settings.getReminderFrequency() * -1), 0);
            testReminderIsRecent(settings, 30); // Reminder date is set in the future
        }

        private void testExtensionIsValid(PrivateDataReminderSettings settings, int monthsOffset)
        {
            testExtensionIsValid(settings, monthsOffset, 0, null, true);
        }

        private void testExtensionIsExpired(PrivateDataReminderSettings settings, int monthsOffset)
        {
            testExtensionIsValid(settings, monthsOffset, 0, null, false);
        }

        private void testExtensionIsValidAsOf(PrivateDataReminderSettings settings, int monthsOffset, int minutesOffset)
        {
            testExtensionIsValid(settings, monthsOffset, minutesOffset, dateFromNow(), true);
        }

        private void testExtensionIsExpiredAsOf(PrivateDataReminderSettings settings, int monthsOffset, int minutesOffset)
        {
            testExtensionIsValid(settings, monthsOffset, minutesOffset, dateFromNow(), false);
        }

        private void testExtensionIsValid(PrivateDataReminderSettings settings, int monthsOffset, int minutesOffset,
                                              Date currentDate, boolean expectedValid)
        {
            Date extensionDate = dateFromNow(monthsOffset, 0, minutesOffset);

            DatasetStatus datasetStatus = new DatasetStatus();
            datasetStatus.setExtensionRequestedDate(extensionDate);

            String failureMessage = String.format("Extension is %s; Extension Length: %d; Extension Requested On: %s; Valid Until: %s",
                    expectedValid ? "valid" : "expired",
                    settings.getExtensionLength(),
                    datasetStatus.getExtensionRequestedDate(),
                    settings.getExtensionValidUntilDate(datasetStatus));
            if (currentDate != null)
            {
                failureMessage += String.format("; Current Date: %s", currentDate);
            }

            boolean isValid = currentDate == null
                        ? settings.isExtensionValid(datasetStatus)
                        : settings.isExtensionValidAsOf(datasetStatus, currentDate);
            if (expectedValid) assertTrue(failureMessage, isValid);
            else assertFalse(failureMessage, isValid);
        }

        private void testReminderIsRecent(PrivateDataReminderSettings settings, int daysOffset)
        {
            testReminderIsRecent(settings, 0, daysOffset, 0, null, true);
        }

        private void testReminderIsOld(PrivateDataReminderSettings settings, int daysOffset)
        {
            testReminderIsRecent(settings, 0, daysOffset, 0, null, false);
        }

        private void testReminderIsRecentAsOf(PrivateDataReminderSettings settings, int monthsOffset, int minutesOffset)
        {
            testReminderIsRecent(settings, monthsOffset, 0, minutesOffset, dateFromNow(), true);
        }

        private void testReminderIsOldAsOf(PrivateDataReminderSettings settings, int monthsOffset, int minutesOffset)
        {
            testReminderIsRecent(settings, monthsOffset, 0, minutesOffset, dateFromNow(), false);
        }

        private void testReminderIsRecent(PrivateDataReminderSettings settings, int monthsOffset, int daysOffset, int minutesOffset,
                                              Date currentDate, boolean expectedRecent)
        {
            Date reminderDate = dateFromNow(monthsOffset, daysOffset, minutesOffset);

            DatasetStatus datasetStatus = new DatasetStatus();
            datasetStatus.setLastReminderDate(reminderDate);

            String failureMessage = String.format("Reminder is %s; Reminder Frequency: %d; Reminder Sent On: %s; Valid Until: %s",
                    expectedRecent ? "recent" : "old",
                    settings.getReminderFrequency(),
                    datasetStatus.getLastReminderDate(),
                    settings.getReminderValidUntilDate(datasetStatus));
            if (currentDate != null)
            {
                failureMessage += String.format("; Current Date: %s", currentDate);
            }

            boolean isValid = currentDate == null
                    ? settings.isLastReminderRecent(datasetStatus)
                    : settings.isLastReminderRecentAsOf(datasetStatus, currentDate);
            if (expectedRecent) assertTrue(failureMessage, isValid);
            else assertFalse(failureMessage, isValid);
        }

        private PrivateDataReminderSettings createTestSettingsExtensionLength(int extensionLength)
        {
            return createTestSettings(extensionLength, 0);
        }

        private PrivateDataReminderSettings createTestSettingsReminderFrequency(int reminderFrequency)
        {
            return createTestSettings(0, reminderFrequency);
        }

        private PrivateDataReminderSettings createTestSettings(int extensionLength, int reminderFrequency)
        {
            PrivateDataReminderSettings testSettings = new PrivateDataReminderSettings();
            testSettings.setExtensionLength(extensionLength);
            testSettings.setReminderFrequency(reminderFrequency);
            return testSettings;
        }

        private Date dateFromNow()
        {
            return dateFromNow(0, 0, 0);
        }

        private Date dateFromNow(int monthsOffset, int daysOffset, int minutesOffset)
        {
            return Date.from(
                    LocalDate.now()
                            .plusMonths(monthsOffset)
                            .plusDays(daysOffset)
                            .atStartOfDay(ZoneId.systemDefault())
                            .plusMinutes(minutesOffset)
                            .toInstant()
            );
        }
    }
}
