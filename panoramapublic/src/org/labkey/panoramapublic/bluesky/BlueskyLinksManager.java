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
package org.labkey.panoramapublic.bluesky;

import org.labkey.api.data.PropertyManager;
import org.labkey.panoramapublic.model.ExperimentAnnotations;

public class BlueskyLinksManager
{
    // Category name for propertysets. Properties that belong to this category will have the short URL as the key
    // and the Bluesky post (AT protocol) URI as the value.
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
