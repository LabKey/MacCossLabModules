package org.labkey.test.components.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.ext4.Checkbox;
import org.labkey.test.components.html.Input;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class PanoramaPublicSearchWebPart extends BodyWebPart<PanoramaPublicSearchWebPart.ElementCache>
{
    private static String title = "Panorama Public Search";

    private final WebDriverWait tabWait = new WebDriverWait(getDriver(), Duration.ofSeconds(1));

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

    private void selectInput(WebElement el, String text)
    {
        el.click();
        el.sendKeys(text);
        el.sendKeys(Keys.TAB);
        el.sendKeys(Keys.DOWN);
        el.sendKeys(Keys.ENTER);
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

    public PanoramaPublicSearchWebPart setSmallMolecule(String value)
    {
        elementCache().smallMolecule.set(value);
        return this;
    }

    public String getSmallMolecule()
    {
        return elementCache().smallMolecule.get();
    }

    public PanoramaPublicSearchWebPart setSmallMoleculeExactMatch(boolean value)
    {
        elementCache().smallMoleculeExactMatch.set(value);
        return this;
    }

    public PanoramaPublicSearchWebPart gotoExperimentSearch()
    {
        elementCache().experimentSearchTab.click();
        tabWait.until(ExpectedConditions.visibilityOf(elementCache().author.getComponentElement()));
        return this;
    }

    public PanoramaPublicSearchWebPart gotoProteinSearch()
    {
        elementCache().proteinTab.click();
        tabWait.until(ExpectedConditions.visibilityOf(elementCache().protein.getComponentElement()));
        return this;
    }

    public PanoramaPublicSearchWebPart gotoPeptideSearch()
    {
        elementCache().peptideTab.click();
        tabWait.until(ExpectedConditions.visibilityOf(elementCache().peptide.getComponentElement()));
        return this;
    }

    public PanoramaPublicSearchWebPart gotoSmallMoleculeSearch()
    {
        elementCache().smallMoleculeTab.click();
        tabWait.until(ExpectedConditions.visibilityOf(elementCache().smallMolecule.getComponentElement()));
        return this;
    }

    public DataRegionTable search()
    {
        return doAndWaitForUpdate(() -> elementCache().search.click());
    }

    public DataRegionTable clearAll()
    {
        return doAndWaitForUpdate(() -> elementCache().clearAll.click());
    }

    private DataRegionTable doAndWaitForUpdate(Runnable runnable)
    {
        // Can't use `DataRegionTable.doAndWaitForUpdate`. Doesn't reuse the same data region.
        getWrapper().doAndWaitForPageSignal(runnable, DataRegionTable.UPDATE_SIGNAL);
        return new DataRegionTable.DataRegionFinder(getDriver()).refindWhenNeeded(this);
    }

    protected class ElementCache extends BodyWebPart<?>.ElementCache
    {
        //Experiment search
        final WebElement experimentSearchTab = Locator.tagWithText("label", "Experiment Search").findWhenNeeded(this);
        final Input author = Input.Input(Locator.input("Authors"), getDriver()).findWhenNeeded(this);
        final Input title = Input.Input(Locator.input("Title"), getDriver()).findWhenNeeded(this);
        final private WebElement organism = Locator.id("input-picker-div-organism")
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input")).findWhenNeeded(this);
        final private WebElement instrument = Locator.id("input-picker-div-instrument")
                .descendant(Locator.tagWithClass("span", "twitter-typeahead"))
                .child(Locator.tagWithClass("input", "tt-input")).findWhenNeeded(this);

        //Protein search
        final WebElement proteinTab = Locator.tagWithText("label", "Protein Search").findWhenNeeded(this);
        final Input protein = Input.Input(Locator.input("proteinLabel"), getDriver()).findWhenNeeded(this);
        final Checkbox proteinExactMatch = new Checkbox(Locator.inputById("exactProteinMatches").findWhenNeeded(this));

        //Peptide search
        final WebElement peptideTab = Locator.tagWithText("label", "Peptide Search").findWhenNeeded(this);
        final Input peptide = Input.Input(Locator.input("peptideSequence"), getDriver()).findWhenNeeded(this);
        final Checkbox peptideExactMatch = new Checkbox(Locator.inputById("exactPeptideMatches").findWhenNeeded(this));

        //Small Molecule Search
        final WebElement smallMoleculeTab = Locator.tagWithText("label", "Small Molecule Search").findWhenNeeded(this);
        final Input smallMolecule = Input.Input(Locator.input("smallMolecule"), getDriver()).findWhenNeeded(this);
        final Checkbox smallMoleculeExactMatch = new Checkbox(Locator.inputById("exactSmallMoleculeMatches").findWhenNeeded(this));

        final WebElement clearAll = Locator.button("Clear All").findWhenNeeded(this);
        final WebElement search = Locator.button("Search").findWhenNeeded(this);
    }
}
