package org.labkey.panoramapublic.message;

import org.labkey.api.data.PropertyManager;

public class PrivateDataMessageSettings
{
    public static final String PROP_PRIVATE_DATA_REMINDER = "Panorama Public private data reminder settings";
    public static final String PROP_ENABLE_REMINDER = "Enable private data reminder";
    public static final String PROP_EXTENSION_MONTHS = "Extension duration (months)";
    public static final String PROP_REMINDER_FREQUENCY = "Reminder frequency (months)";


    private static final boolean DEFAULT_ENABLE_REMINDERS = false;
    private static final int DEFAULT_EXTENSION_LENGTH = 6; // Private status of a dataset can be extended by 6 months.
    private static final int DEFAULT_REMINDER_FREQUENCY = 1; // Send reminders once a month, unless extension or deletion was requested.

    private boolean _enableReminders;
    private int _extensionLength;
    private int _reminderFrequency;

    public static PrivateDataMessageSettings get()
    {
        PropertyManager.WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(PROP_PRIVATE_DATA_REMINDER, false);

        PrivateDataMessageSettings settings = new PrivateDataMessageSettings();
        if(settingsMap != null)
        {
            boolean enableReminders = settingsMap.get(PROP_EXTENSION_MONTHS) == null ? DEFAULT_ENABLE_REMINDERS : Boolean.valueOf(settingsMap.get(PROP_ENABLE_REMINDER));
            settings.setEnableReminders(enableReminders);
            int extensionLength = settingsMap.get(PROP_EXTENSION_MONTHS) == null ? DEFAULT_EXTENSION_LENGTH : Integer.valueOf(settingsMap.get(PROP_EXTENSION_MONTHS));
            settings.setExtensionLength(extensionLength);
            int reminderFrequency = settingsMap.get(PROP_REMINDER_FREQUENCY) == null ? DEFAULT_EXTENSION_LENGTH : Integer.valueOf(settingsMap.get(PROP_REMINDER_FREQUENCY));
            settings.setReminderFrequency(reminderFrequency);
        }
        else
        {
            settings.setEnableReminders(DEFAULT_ENABLE_REMINDERS);
            settings.setExtensionLength(DEFAULT_EXTENSION_LENGTH);
            settings.setReminderFrequency(DEFAULT_REMINDER_FREQUENCY);
        }

        return settings;
    }

    public static void save(PrivateDataMessageSettings settings)
    {
        PropertyManager.WritablePropertyMap settingsMap = PropertyManager.getWritableProperties(PROP_PRIVATE_DATA_REMINDER, true);
        settingsMap.put(PROP_ENABLE_REMINDER, String.valueOf(settings.isEnableReminders()));
        settingsMap.put(PROP_EXTENSION_MONTHS, String.valueOf(settings.getExtensionLength()));
        settingsMap.put(PROP_REMINDER_FREQUENCY, String.valueOf(settings.getReminderFrequency()));
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
}
