package org.labkey.test.pages.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.TextSearcher;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DataValidationPage extends LabKeyPage<DataValidationPage.ElementCache>
{
    public DataValidationPage(WebDriverWrapper webDriverWrapper)
    {
        super(webDriverWrapper);
        waitForElement(Locators.bodyTitle().withText("Data Validation Status"));
        assertTextNotPresent("Could not find job status for job");
        waitForTextToDisappear("This page will automatically refresh", WAIT_FOR_PAGE);
        expandValidationRows();
    }

    private void expandValidationRows()
    {
        var gridRowExapnder = Locator.XPathLocator.tagWithClass("div", "x4-grid-row-expander");
        var els = gridRowExapnder.findElements(getDriver());
        assertTrue("Expected to find grid expander elements", els.size() > 0);
        for (var el: els)
        {
            el.click(); // Expand all the rows
        }
    }

    public void verifyInvalidStatus()
    {
        assertStatus("Submit Without A ProteomeXchange ID",
                List.of("Status: Data is not valid for a ProteomeXchange submission",
                        "The data cannot be assigned a ProteomeXchange ID",
                        "Missing raw data files",
                        "Submit Without A ProteomeXchange ID"),
                null);
    }

    public void verifyIncompleteStatus()
    {
        assertStatus("Continue With An Incomplete PX Submission",
                List.of("Status: Incomplete data and/or metadata",
                        "The data can be assigned a ProteomeXchange ID but it is not valid for a \"complete\" ProteomeXchange submission",
                        "Continue With An Incomplete PX Submission"),
                List.of("Missing raw data files"));
    }

    public void verifyCompleteStatus()
    {
        assertStatus("Continue Submission",
                List.of("Status: Complete",
                        "The data is valid for a \"complete\" ProteomeXchange submission",
                        "Continue Submission"),
                List.of("Missing raw data files"));
    }

    private void assertStatus(String submitButtonText, List<String> textsPresent, List<String> textsNotPresent)
    {
        scrollIntoView(elementCache().summaryPanel);
        var button = elementCache().summaryPanel.findElement(By.cssSelector("a.pxv-btn-submit"));
        assertEquals(submitButtonText, button.getText());
        var summaryText = elementCache().summaryPanel.getText();
        assertTextPresent(new TextSearcher(summaryText), textsPresent.toArray(new String[]{}));
        if (textsNotPresent != null)
        {
            assertTextNotPresent(new TextSearcher(summaryText), textsNotPresent.toArray(new String[]{}));
        }
    }

    public void verifySampleFileStatus(String skylineDocName, List<String> found, List<String> missing)
    {
        scrollIntoView(elementCache().skyDocsPanel);
        verifySkyDocStatus(skylineDocName, missing.size() == 0 ? "COMPLETE" : "INCOMPLETE");

        var sampleFilesTable = elementCache().skyDocsPanel.findElement(getFilesTableLocator(skylineDocName, "pxv-tpl-table"));
        for (var file: missing)
        {
            verifyFileStatus(sampleFilesTable, file, true);
        }
        for (var file: found)
        {
            verifyFileStatus(sampleFilesTable, file, false);
        }
    }

    private void verifySkyDocStatus(String skylineDocName, String status)
    {
        elementCache().skyDocsPanel.findElement(Locator.XPathLocator.tag("td").withText(skylineDocName).followingSibling("td").withText(status));
    }

    private Locator getFilesTableLocator(String skylineDocName, String clsName)
    {
        return Locator.XPathLocator.tag("td").withText(skylineDocName)
                .parent("tr")
                .followingSibling("tr")
                .descendant("table").withClass(clsName);
    }

    private void verifyFileStatus(WebElement sampleFilesTable, String file, boolean missing)
    {
        sampleFilesTable.findElement(
                Locator.XPathLocator.tag("tbody").child("tr")
                .child(Locator.tag("td").withText(file))
                .followingSibling("td")
                .child(Locator.tag("span").withClass(missing ? "pxv-invalid" : "pxv-valid")));
    }

    public void verifySpectralLibraryStatus(String libraryFile, String fileSize, String statusText,
                                            List<String> skylineDocNames,
                                            List<String> spectrumFiles, List<String> spectrumFilesMissing,
                                            List<String> idFiles, List<String> idFilesMissing)
    {
        scrollIntoView(elementCache().specLibsPanel);
        verifySpecLibStatus(libraryFile, fileSize,
                (spectrumFiles.size() == 0 || idFiles.size() == 0
                        || spectrumFilesMissing.size() > 0 || idFilesMissing.size() > 0) ? "INCOMPLETE" : "CPMPLETE");

        var panelText = elementCache().specLibsPanel.getText();
        List<String> expectedTexts = new ArrayList<>(skylineDocNames);
        expectedTexts.add(statusText);
        assertTextPresent(new TextSearcher(panelText), expectedTexts.toArray(new String[]{}));

        var panel = elementCache().specLibsPanel;
        verifyLibrarySourceFiles(libraryFile, spectrumFiles, spectrumFilesMissing, panel, "lib-spectrum-files-status");
        verifyLibrarySourceFiles(libraryFile, idFiles, idFilesMissing, panel, "lib-id-files-status");
    }

    private void verifyLibrarySourceFiles(String libraryName, List<String> files, List<String> filesMissing, WebElement specLibsPanel, String tblCls)
    {
        if (files.size() > 0 || filesMissing.size() > 0)
        {
            var filesTable = specLibsPanel.findElement(getFilesTableLocator(libraryName, tblCls));
            for (var file : filesMissing)
            {
                verifyFileStatus(filesTable, file, true);
            }
            for (var file : files)
            {
                verifyFileStatus(filesTable, file, false);
            }
        }
    }

    private void verifySpecLibStatus(String libraryName, String fileSize, String status)
    {
        elementCache().specLibsPanel.findElement(Locator.XPathLocator.tag("td").withText(libraryName)
                .followingSibling("td").withText(fileSize)
                .followingSibling("td").withText(status));
    }

    @Override
    protected DataValidationPage.ElementCache newElementCache()
    {
        return new DataValidationPage.ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        protected final WebElement summaryPanel = Locator.tagWithClass("div", "pxv-summary-panel").findWhenNeeded(this);
        protected final WebElement skyDocsPanel = Locator.tagWithClass("div", "pxv-skydocs-panel").findWhenNeeded(this);
        protected final WebElement modificationsPanel = Locator.tagWithClass("div", "pxv-modifications-panel").findWhenNeeded(this);
        protected final WebElement specLibsPanel = Locator.tagWithClass("div", "pxv-speclibs-panel").findWhenNeeded(this);
    }
}
