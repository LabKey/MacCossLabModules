package org.labkey.panoramapublic.bluesky;

import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class BlueskySettingsManager
{
    private static final String CREDENTIALS = "Bluesky credentials";
    private static final String ACCOUNT = "Bluesky account";
    private static final String PASSWORD = "Bluesky password";

    private static final String TEST_ACCOUNT = "Bluesky test account";
    private static final String TEST_ACCOUNT_PASSWORD = "Bluesky test password";

    private static final String SETTINGS = "Bluesky settings";
    private static final String AUTH_URL = "Bluesky auth endpoint";
    private static final String POST_URL = "Bluesky post endpoint";
    private static final String BLOB_UPLOAD_URL = "Image blob upload endpoint";

    private static final String HASHTAGS = "Hashtags";
    private static final String TEST_HASHTAGS = "Hashtags for test account";
    private static final String PANORAMA_LOGO_FILENAME = "Panorama logo file name";
    private static final String AUTOPOST = "Auto‑post to Bluesky on publish";


    public static final String BLUESKY_LINKS = "Bluesky post links";

    public static BlueskySettings getSettings()
    {
        BlueskySettings settings = new BlueskySettings();
        WritablePropertyMap credentialsMap = PropertyManager.getEncryptedStore().getWritableProperties(CREDENTIALS, false);
        if(credentialsMap != null)
        {
            settings.setAccount(credentialsMap.get(ACCOUNT));
            settings.setPassword(credentialsMap.get(PASSWORD));
            settings.setTestAccount(credentialsMap.get(TEST_ACCOUNT));
            settings.setTestAccountPassword(credentialsMap.get(TEST_ACCOUNT_PASSWORD));
        }
        WritablePropertyMap settingsMap = PropertyManager.getNormalStore().getWritableProperties(SETTINGS, false);
        if (settingsMap != null)
        {
            settings.setAuthEndpoint(settingsMap.get(AUTH_URL));
            settings.setPostEndpoint(settingsMap.get(POST_URL));
            settings.setBlobUploadEndpoint(settingsMap.get(BLOB_UPLOAD_URL));
            settings.setHashtags(settingsMap.get(HASHTAGS));
            settings.setTestHashtags(settingsMap.get(TEST_HASHTAGS));
            settings.setAutopost(Boolean.valueOf(settingsMap.get(AUTOPOST)));
            settings.setImageFileName(settingsMap.get(PANORAMA_LOGO_FILENAME));
        }
        return settings;
    }

    public static void saveSettings(BlueskySettings settings)
    {
        WritablePropertyMap credentialsMap = PropertyManager.getEncryptedStore().getWritableProperties(CREDENTIALS, true);
        credentialsMap.put(ACCOUNT, settings.getAccount());
        credentialsMap.put(PASSWORD, settings.getPassword());
        credentialsMap.put(TEST_ACCOUNT, settings.getTestAccount());
        credentialsMap.put(TEST_ACCOUNT_PASSWORD, settings.getTestAccountPassword());
        credentialsMap.save();

        WritablePropertyMap settingsMap = PropertyManager.getNormalStore().getWritableProperties(SETTINGS, true);
        settingsMap.put(PANORAMA_LOGO_FILENAME, settings.getImageFileName());
        settingsMap.put(AUTOPOST, Boolean.toString(settings.isAutopost()));
        settingsMap.put(AUTH_URL, settings.getAuthEndpoint());
        settingsMap.put(POST_URL, settings.getPostEndpoint());
        settingsMap.put(BLOB_UPLOAD_URL, settings.getBlobUploadEndpoint());
        settingsMap.put(HASHTAGS, settings.getHashtags());
        settingsMap.put(TEST_HASHTAGS, settings.getTestHashtags());
        settingsMap.save();
    }

    public static void removeLogoFileName()
    {
        BlueskySettings settings = getSettings();
        settings.setImageFileName(null);
        saveSettings(settings);
    }

    public static String getPostUrlForExperiment(ExperimentAnnotations experimentAnnotations)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return null;
        }

        WritablePropertyMap map = PropertyManager.getNormalStore()
                .getWritableProperties(BlueskySettingsManager.BLUESKY_LINKS, false);
        return map != null ? map.get(experimentAnnotations.getShortUrl().renderShortURL()) : null;
    }

    public static void savePostUrlForExperiment(ExperimentAnnotations experimentAnnotations, String blueskyPostUrl)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return;
        }
        WritablePropertyMap propertyMap = PropertyManager.getNormalStore()
                .getWritableProperties(BlueskySettingsManager.BLUESKY_LINKS, true);
        propertyMap.put(experimentAnnotations.getShortUrl().renderShortURL(), blueskyPostUrl);
        propertyMap.save();
    }

    public static void clearPostUrlForExperiment(ExperimentAnnotations experimentAnnotations)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return;
        }
        if (getPostUrlForExperiment(experimentAnnotations) != null)
        {
            WritablePropertyMap propertyMap = PropertyManager.getNormalStore()
                    .getWritableProperties(BlueskySettingsManager.BLUESKY_LINKS, true);
            propertyMap.remove(experimentAnnotations.getShortUrl().renderShortURL());
            propertyMap.save();
        }
    }
}
