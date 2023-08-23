package org.labkey.test.components.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.pages.InsertPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class TargetedMsExperimentInsertPage extends InsertPage
{
    public TargetedMsExperimentInsertPage(WebDriver driver)
    {
        this(driver, false);
    }

    public TargetedMsExperimentInsertPage(WebDriver driver, boolean update)
    {
        super(driver, update ? "Update Targeted MS Experiment Details" : "Create Targeted MS Experiment");
    }

    public void insert(String experimentTitle)
    {
        Elements elements = elements();
        setFormElement(elements.expTitle, experimentTitle);
        setDefaultAbstract();
        clickAndWait(elements.submit);
    }

    private void setDefaultAbstract()
    {
        setFormElement(elements().expAbstract, "This is a really short, 50 character long abstract");
    }

    public void insertAllRequired(String experimentTitle)
    {
        Elements elements = elements();
        setFormElement(elements.expTitle, experimentTitle);
        setDefaultAbstract();
        selectOrganism();
        selectInstrument();
        setFormElement(elements.keywords, "Skyline, Panorama");
        setFormElement(elements.submitterAffiliation, "University of Washington");
        clickAndWait(elements.submit);
    }

    private void selectOrganism()
    {
        selectInput(elements().organismInputDiv, null);
    }

    private void selectInstrument()
    {
        selectInput(elements().instrumentInputdiv, "thermo");
    }

    private void selectInput(Locator.XPathLocator inputsDiv, String text)
    {
        click(inputsDiv);
        if (text != null)
        {
            inputsDiv.findElement(getDriver()).sendKeys(text);
        }
        pressTab(inputsDiv);
        pressDownArrow(inputsDiv);
        pressEnter(inputsDiv);
    }

    public void setRequired(String experimentTitle)
    {
        Elements elements = elements();
        setFormElement(elements.expTitle, experimentTitle);
        selectOrganism();
        selectInstrument();
        setFormElement(elements.keywords, "Skyline, Panorama");
        setFormElement(elements.submitterAffiliation, "University of Washington");

        clickAndWait(elements.updateButton);
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
        public Locator.XPathLocator keywords = body.append(Locator.tagWithName("textarea","keywords"));
        public Locator.XPathLocator submitterAffiliation = body.append(Locator.input("submitterAffiliation"));
        public Locator.XPathLocator organismInputDiv = body.append(Locator.id("input-picker-div-organism")
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input")));
        public Locator.XPathLocator instrumentInputdiv = body.append(Locator.id("input-picker-div-instrument"))
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input"));
        WebElement updateButton = Locator.lkButton("Update").findWhenNeeded(getDriver());
    }
}
