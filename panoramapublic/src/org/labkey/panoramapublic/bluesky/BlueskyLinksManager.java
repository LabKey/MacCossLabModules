package org.labkey.panoramapublic.bluesky;

import org.labkey.api.data.PropertyManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class BlueskyLinksManager
{
    // Category name for propertysets. Properties belonging to this category will have the short URL as the key
    // and the Bluesky post URI (AT protocol) as the value.
    public static final String BLUESKY_URIS = "Bluesky AT protocol URIs";

    public static String getBlueskyUriForExperiment(ExperimentAnnotations experimentAnnotations)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return null;
        }

        PropertyManager.WritablePropertyMap map = PropertyManager.getNormalStore()
                .getWritableProperties(BLUESKY_URIS, false);
        return map != null ? map.get(experimentAnnotations.getShortUrl().renderShortURL()) : null;
    }

    public static void saveBlueskyUriForExperiment(ExperimentAnnotations experimentAnnotations, String atProtocolUri)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return;
        }
        PropertyManager.WritablePropertyMap propertyMap = PropertyManager.getNormalStore()
                .getWritableProperties(BLUESKY_URIS, true);
        propertyMap.put(experimentAnnotations.getShortUrl().renderShortURL(), atProtocolUri);
        propertyMap.save();
    }

    public static void clearBlueskyUriForExperiment(ExperimentAnnotations experimentAnnotations)
    {
        if (experimentAnnotations == null || experimentAnnotations.getShortUrl() == null)
        {
            return;
        }
        if (getBlueskyUriForExperiment(experimentAnnotations) != null)
        {
            PropertyManager.WritablePropertyMap propertyMap = PropertyManager.getNormalStore()
                    .getWritableProperties(BLUESKY_URIS, true);
            propertyMap.remove(experimentAnnotations.getShortUrl().renderShortURL());
            propertyMap.save();
        }
    }
}
