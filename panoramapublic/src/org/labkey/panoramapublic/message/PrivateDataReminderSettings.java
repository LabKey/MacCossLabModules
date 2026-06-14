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
package org.labkey.panoramapublic.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.DateUtil;
import org.labkey.panoramapublic.model.DatasetStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class PrivateDataReminderSettings
{
    public static final String PROP_PRIVATE_DATA_REMINDER = "Panorama Public private data reminder settings";
    public static final String PROP_ENABLE_REMINDER = "Enable private data reminder";
    public static final String PROP_REMINDER_TIME = "Reminder time";
    public static final String PROP_DELAY_UNTIL_FIRST_REMINDER = "Delay until first reminder (months)";
    public static final String PROP_REMINDER_FREQUENCY = "Reminder frequency (months)";
    public static final String PROP_EXTENSION_LENGTH = "Extension duration (months)";
    public static final String PROP_ENABLE_PUBLICATION_SEARCH = "Enable publication search";
    public static final String PROP_PUBLICATION_SEARCH_FREQUENCY = "Publication search frequency (months)";
    public static final String PROP_NCBI_API_KEY = "NCBI API key";

    private static final boolean DEFAULT_ENABLE_REMINDERS = false;
    public static final String DEFAULT_REMINDER_TIME = "8:00 AM";
    private static final int DEFAULT_DELAY_UNTIL_FIRST_REMINDER = 12; // Send the first reminder after the data has been private for a year.
    private static final int DEFAULT_REMINDER_FREQUENCY = 1; // Send reminders once a month, unless extension or deletion was requested.
    private static final int DEFAULT_EXTENSION_LENGTH = 6; // Private status of a dataset can be extended by 6 months.
    private static final boolean DEFAULT_ENABLE_PUBLICATION_SEARCH = false;
    private static final int DEFAULT_PUBLICATION_SEARCH_FREQUENCY = 3; // Re-search every 3 months after dismissal

    public static final String DATE_FORMAT_PATTERN = "MMMM d, yyyy";
    public static final String REMINDER_TIME_FORMAT = "h:mm a";
    private static final DateTimeFormatter reminderTimeFormatter = DateTimeFormatter.ofPattern(REMINDER_TIME_FORMAT);

    private boolean _enableReminders;
    private LocalTime _reminderTime;
    private int _delayUntilFirstReminder;
    private int _reminderFrequency;
    private int _extensionLength;
    private boolean _enablePublicationSearch;
    private int _publicationSearchFrequency;
    private String _ncbiApiKey;

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

            LocalTime reminderTime = tryParseReminderTime(settingsMap.get(PROP_REMINDER_TIME), DEFAULT_REMINDER_TIME);
            settings.setReminderTime(reminderTime);

            boolean enablePublicationCheck = settingsMap.get(PROP_ENABLE_PUBLICATION_SEARCH) == null
                    ? DEFAULT_ENABLE_PUBLICATION_SEARCH
                    : Boolean.valueOf(settingsMap.get(PROP_ENABLE_PUBLICATION_SEARCH));
            settings.setEnablePublicationSearch(enablePublicationCheck);

            int publicationSearchFrequency = settingsMap.get(PROP_PUBLICATION_SEARCH_FREQUENCY) == null
                    ? DEFAULT_PUBLICATION_SEARCH_FREQUENCY
                    : Integer.valueOf(settingsMap.get(PROP_PUBLICATION_SEARCH_FREQUENCY));
            settings.setPublicationSearchFrequency(publicationSearchFrequency);

            settings.setNcbiApiKey(settingsMap.get(PROP_NCBI_API_KEY));
        }
        else
        {
            settings.setEnableReminders(DEFAULT_ENABLE_REMINDERS);
            settings.setDelayUntilFirstReminder(DEFAULT_DELAY_UNTIL_FIRST_REMINDER);
            settings.setReminderFrequency(DEFAULT_REMINDER_FREQUENCY);
            settings.setExtensionLength(DEFAULT_EXTENSION_LENGTH);
            settings.setReminderTime(parseReminderTime(DEFAULT_REMINDER_TIME));
            settings.setEnablePublicationSearch(DEFAULT_ENABLE_PUBLICATION_SEARCH);
            settings.setPublicationSearchFrequency(DEFAULT_PUBLICATION_SEARCH_FREQUENCY);
        }

        return settings;
    }

    private static LocalTime tryParseReminderTime(String timeString, String defaultTime)
    {
        LocalTime reminderTime = parseReminderTime(timeString);
        if (reminderTime == null)
        {
            reminderTime = parseReminderTime(defaultTime);
        }
        return reminderTime;
    }

    public static @Nullable LocalTime parseReminderTime(String timeString)
    {
        try
        {
            return timeString != null ? LocalTime.parse(timeString, reminderTimeFormatter) : null;
        }
        catch(DateTimeParseException ignored) {}

        return null;
    }

    public static void save(PrivateDataReminderSettings settings)
    {
        PropertyManager.WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(PROP_PRIVATE_DATA_REMINDER, true);
        settingsMap.put(PROP_ENABLE_REMINDER, String.valueOf(settings.isEnableReminders()));
        settingsMap.put(PROP_DELAY_UNTIL_FIRST_REMINDER, String.valueOf(settings.getDelayUntilFirstReminder()));
        settingsMap.put(PROP_REMINDER_FREQUENCY, String.valueOf(settings.getReminderFrequency()));
        settingsMap.put(PROP_EXTENSION_LENGTH, String.valueOf(settings.getExtensionLength()));
        settingsMap.put(PROP_REMINDER_TIME, settings.getReminderTimeFormatted());
        settingsMap.put(PROP_ENABLE_PUBLICATION_SEARCH, String.valueOf(settings.isEnablePublicationSearch()));
        settingsMap.put(PROP_PUBLICATION_SEARCH_FREQUENCY, String.valueOf(settings.getPublicationSearchFrequency()));
        settingsMap.put(PROP_NCBI_API_KEY, settings.getNcbiApiKey() != null ? settings.getNcbiApiKey() : "");
        settingsMap.save();
    }

    public void setEnableReminders(boolean enableReminders)
    {
        _enableReminders = enableReminders;
    }

    public void setReminderTime(LocalTime reminderTime)
    {
        _reminderTime = reminderTime;
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

    public LocalTime getReminderTime()
    {
        return _reminderTime;
    }

    public String getReminderTimeFormatted()
    {
        return _reminderTime != null ? _reminderTime.format(reminderTimeFormatter) : "Reminder time not set";
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

    public boolean isEnablePublicationSearch()
    {
        return _enablePublicationSearch;
    }

    public void setEnablePublicationSearch(boolean enablePublicationSearch)
    {
        _enablePublicationSearch = enablePublicationSearch;
    }

    public int getPublicationSearchFrequency()
    {
        return _publicationSearchFrequency;
    }

    public void setPublicationSearchFrequency(int publicationSearchFrequency)
    {
        _publicationSearchFrequency = publicationSearchFrequency;
    }

    public @Nullable String getNcbiApiKey()
    {
        return _ncbiApiKey;
    }

    public void setNcbiApiKey(@Nullable String ncbiApiKey)
    {
        _ncbiApiKey = ncbiApiKey;
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

    public boolean isPublicationDismissalRecent(@NotNull DatasetStatus status)
    {
        Date dismissed = status.getUserDismissedPublication();
        if (dismissed == null) return false;
        Date searchDeferralEnd = addMonths(dismissed, getPublicationSearchFrequency());
        return isDateInFuture(searchDeferralEnd);
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

    private boolean isLastReminderRecentAsOf(@NotNull DatasetStatus status, @NotNull Date currentTime)
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
