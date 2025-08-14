package org.labkey.panoramapublic.message;

import org.labkey.api.data.PropertyManager;

public class PrivateDataReminderSettings
{
    public static final String PROP_PRIVATE_DATA_REMINDER = "Panorama Public private data reminder settings";
    public static final String PROP_ENABLE_REMINDER = "Enable private data reminder";
    public static final String PROP_DELAY_UNTIL_FIRST_REMINDER = "Delay until first reminder (months)";
    public static final String PROP_REMINDER_FREQUENCY = "Reminder frequency (months)";
    public static final String PROP_EXTENSION_MONTHS = "Extension duration (months)";
    


    private static final boolean DEFAULT_ENABLE_REMINDERS = false;
    private static final int DEFAULT_DELAY_UNTIL_FIRST_REMINDER = 12; // Send the first reminder after the data has been private for a year.
    private static final int DEFAULT_REMINDER_FREQUENCY = 1; // Send reminders once a month, unless extension or deletion was requested.
    private static final int DEFAULT_EXTENSION_LENGTH = 6; // Private status of a dataset can be extended by 6 months.

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
            boolean enableReminders = settingsMap.get(PROP_EXTENSION_MONTHS) == null ? DEFAULT_ENABLE_REMINDERS : Boolean.valueOf(settingsMap.get(PROP_ENABLE_REMINDER));
            settings.setEnableReminders(enableReminders);
            int delayUntilFirstReminder = settingsMap.get(PROP_DELAY_UNTIL_FIRST_REMINDER) == null ? DEFAULT_DELAY_UNTIL_FIRST_REMINDER : Integer.valueOf(settingsMap.get(PROP_DELAY_UNTIL_FIRST_REMINDER));
            settings.setDelayUntilFirstReminder(delayUntilFirstReminder);
            int reminderFrequency = settingsMap.get(PROP_REMINDER_FREQUENCY) == null ? DEFAULT_EXTENSION_LENGTH : Integer.valueOf(settingsMap.get(PROP_REMINDER_FREQUENCY));
            settings.setReminderFrequency(reminderFrequency);
            int extensionLength = settingsMap.get(PROP_EXTENSION_MONTHS) == null ? DEFAULT_EXTENSION_LENGTH : Integer.valueOf(settingsMap.get(PROP_EXTENSION_MONTHS));
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
        settingsMap.put(PROP_EXTENSION_MONTHS, String.valueOf(settings.getExtensionLength()));
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
}
