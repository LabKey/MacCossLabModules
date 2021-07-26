package org.labkey.test.components.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.pages.InsertPage;
import org.labkey.test.tests.panoramapublic.PanoramaPublicTest;
import org.openqa.selenium.WebDriver;

public class TargetedMsExperimentInsertPage extends InsertPage
{
    private static final String DEFAULT_TITLE = "Targeted MS Experiment";

    public TargetedMsExperimentInsertPage(WebDriver driver)
    {
        super(driver, DEFAULT_TITLE);
    }

    @Override
    protected void waitForReady()
    {
        waitForElement(elements().expTitle);
    }

    public void insert(String experimentTitle)
    {
        Elements elements = elements();
        setFormElement(elements.expTitle, experimentTitle);
        setFormElement(elements.expAbstract, "This is a really short, 50 character long abstract");
        clickAndWait(elements.submit);
    }

    @Override
    protected Elements elements()
    {
        return new Elements();
    }

    private class Elements extends InsertPage.Elements
    {
        public Locator.XPathLocator expTitle = body.append(Locator.tagWithName("textarea", "title"));
        public Locator.XPathLocator expAbstract = body.append(Locator.tagWithName("textarea", "abstract"));
    }
}
