package org.labkey.test.components.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.ext4.Checkbox;
import org.labkey.test.components.html.Input;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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

    public String getTitle()
    {
        return elementCache().title.get();
    }

    public PanoramaPublicSearchWebPart setTitle(String title)
    {
        elementCache().title.set(title);
        return this;
    }

    public String getAuthor()
    {
        return elementCache().author.get();
    }

    public PanoramaPublicSearchWebPart setAuthor(String value)
    {
        elementCache().author.set(value);
        return this;
    }

    public PanoramaPublicSearchWebPart setOrganism(String value)
    {
        selectInput(elementCache().organism, value);
        return this;
    }

    public PanoramaPublicSearchWebPart setInstrument(String value)
    {
        selectInput(elementCache().instrument, value);
        return this;
    }

    private void selectInput(Locator.XPathLocator inputsDiv, String text)
    {
        getWrapper().click(inputsDiv);
        if (text != null)
        {
            inputsDiv.findElement(getDriver()).sendKeys(text);
        }
        inputsDiv.findElement(this).sendKeys(Keys.TAB);
        inputsDiv.findElement(this).sendKeys(Keys.DOWN);
        inputsDiv.findElement(this).sendKeys(Keys.ENTER);
    }

    public String getProtein()
    {
        return elementCache().protein.get();
    }

    public PanoramaPublicSearchWebPart setProtein(String value)
    {
        elementCache().protein.set(value);
        return this;
    }

    public PanoramaPublicSearchWebPart setProteinExactMatch(boolean value)
    {
        elementCache().proteinExactMatch.set(value);
        return this;
    }

    public String getPeptide()
    {
        return elementCache().peptide.get();
    }

    public PanoramaPublicSearchWebPart setPeptide(String value)
    {
        elementCache().peptide.set(value);
        return this;
    }

    public PanoramaPublicSearchWebPart setPeptideExactMatch(boolean value)
    {
        elementCache().peptideExactMatch.set(value);
        return this;
    }

    public PanoramaPublicSearchWebPart gotoExperimentSearch()
    {
        elementCache().experimentSearchTab.click();
        return this;
    }

    public PanoramaPublicSearchWebPart gotoProteinSearch()
    {
        elementCache().proteinTab.click();
        return this;
    }

    public PanoramaPublicSearchWebPart gotoPeptideSearch()
    {
        elementCache().peptideTab.click();
        return this;
    }

    public DataRegionTable search()
    {
        elementCache().search.click();
        return new DataRegionTable.DataRegionFinder(getDriver()).waitFor(this);
    }

    protected class ElementCache extends BodyWebPart.ElementCache
    {
        //Experiment search
        final WebElement experimentSearchTab = Locator.tagWithText("label", "Experiment Search").findWhenNeeded(this);
        final Input author = Input.Input(Locator.input("Authors"), getDriver()).findWhenNeeded(this);
        final Input title = Input.Input(Locator.input("Title"), getDriver()).findWhenNeeded(this);
        //Protein search
        final WebElement proteinTab = Locator.tagWithText("label", "Protein Search").findWhenNeeded(this);
        final Input protein = Input.Input(Locator.input("proteinLabel"), getDriver()).findWhenNeeded(this);
        final Checkbox proteinExactMatch = new Checkbox(Locator.inputById("exactProteinMatches").findWhenNeeded(this));
        //Peptide search
        final WebElement peptideTab = Locator.tagWithText("label", "Peptide Search").findWhenNeeded(this);
        final Input peptide = Input.Input(Locator.input("peptideSequence"), getDriver()).findWhenNeeded(this);
        final Checkbox peptideExactMatch = new Checkbox(Locator.inputById("exactPeptideMatches").findWhenNeeded(this));
        final WebElement search = Locator.button("Search").findWhenNeeded(this);

        public Locator.XPathLocator organism = Locator.id("input-picker-div-organism")
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input"));
        public Locator.XPathLocator instrument = Locator.id("input-picker-div-instrument")
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input"));
    }
}
