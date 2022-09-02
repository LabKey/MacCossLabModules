package org.labkey.test.components.panoramapublic;

import org.labkey.test.components.BodyWebPart;
import org.openqa.selenium.WebDriver;

public class PanoramaPublicSearchWebPart extends BodyWebPart<PanoramaPublicSearchWebPart.ElementCache>
{
    private static String title = "Panorama Public Search";

    public PanoramaPublicSearchWebPart(WebDriver driver, String title)
    {
        super(driver, title);
    }

    @Override
    protected PanoramaPublicSearchWebPart.ElementCache newElementCache()
    {
        return new PanoramaPublicSearchWebPart.ElementCache();
    }

    protected class ElementCache extends BodyWebPart.ElementCache
    {

    }
}
