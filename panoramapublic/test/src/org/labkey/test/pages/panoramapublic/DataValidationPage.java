package org.labkey.test.pages.panoramapublic;

import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.TextSearcher;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DataValidationPage extends LabKeyPage<DataValidationPage.ElementCache>
{
    public DataValidationPage(WebDriverWrapper webDriverWrapper)
    {
        super(webDriverWrapper);
        waitForElement(Locators.bodyTitle().withText("Data Validation Status"));
        assertTextNotPresent("Could not find job status for job");
        waitForTextToDisappear("This page will automatically refresh", WAIT_FOR_PAGE);
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
        var panel = elementCache().skyDocsPanel;
        scrollIntoView(panel);
        expandSkyDocRow(panel, skylineDocName);

        verifySkyDocStatus(skylineDocName, missing.size() == 0 ? "COMPLETE" : "INCOMPLETE");

        var sampleFilesTable = panel.findElement(getFilesTableLocator(skylineDocName, "pxv-tpl-table"));
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

    private void expandSkyDocRow(WebElement panel, String fileName)
    {
        var expander = panel.findElement(Locator.XPathLocator.tag("td").withText(fileName)
                .precedingSibling("td").descendant("div").withClass("x4-grid-row-expander"));
        expander.click();
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
        var panel = elementCache().specLibsPanel;
        scrollIntoView(panel);
        expandLibraryRow(panel, libraryFile, fileSize);
        verifySpecLibStatus(libraryFile, fileSize,
                (spectrumFiles.size() == 0 || idFiles.size() == 0
                        || spectrumFilesMissing.size() > 0 || idFilesMissing.size() > 0) ? "INCOMPLETE" : "CPMPLETE");

        var panelText = panel.getText();
        List<String> expectedTexts = new ArrayList<>(skylineDocNames);
        expectedTexts.add(statusText);
        assertTextPresent(new TextSearcher(panelText), expectedTexts.toArray(new String[]{}));

        verifyLibrarySourceFiles(libraryFile, spectrumFiles, spectrumFilesMissing, panel, "lib-spectrum-files-status");
        verifyLibrarySourceFiles(libraryFile, idFiles, idFilesMissing, panel, "lib-id-files-status");
    }

    private void expandLibraryRow(WebElement panel, String libraryName, String librarySize)
    {
        var expander = panel.findElement(Locator.XPathLocator.tag("td").withText(libraryName)
                .followingSibling("td").withText(librarySize)
                .parent("tr").descendant("div").withClass("x4-grid-row-expander"));
        expander.click();
    }

    private int getRowIndexForModification(String modification)
    {
        var rowCount = elementCache().getModificationRows().size();
        for (int i = 0; i < rowCount; i++)
        {
            if (elementCache().getModificationRowCells(i).get(1).getText().equals(modification)) return i;
        }
        return -1;
    }

    private String inferredModName(String modification)
    {
        return "**" + modification;
    }

    public void verifyModificationStatus(String modName, boolean inferred, String unimodId, String unimodName)
    {
        verifyModificationStatus(modName, inferred, unimodId, unimodName, null, null);
    }

    public void verifyModificationStatus(String modName, boolean inferred, String unimodId, String unimodName, String unimodId2, String unimodName2)
    {
        modName = inferred ? inferredModName(modName) : modName;

        int rowIdx = getRowIndexForModification(modName);
        assertNotEquals("Expected a row in the modifications validation grid for modification " + modName, -1, rowIdx);

        var cells = elementCache().getModificationRowCells(rowIdx);
        Set<String> cellValues = new HashSet<>();
        cells.forEach(cell -> cellValues.add(cell.getText()));
        assertTrue(modName + " was not found in modification row " + rowIdx, cellValues.contains(modName));

        if (unimodId == null)
        {
            assertTrue(cellValues.stream().anyMatch(v -> v.startsWith("MISSING")));
        }
        else
        {
            assertTrue(cellValues.contains(unimodId + (unimodId2 != null ? " + " +unimodId2 : "")));
            assertTrue(cellValues.contains(unimodName + (unimodName2 != null ? " + " +unimodName2 : "")));
        }
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
        private List<WebElement> modificationRows;
        private Map<Integer, List<WebElement>> modificationRowCells;

        private List<WebElement> getModificationRows()
        {
            if (modificationRows == null)
            {
                var panel = elementCache().modificationsPanel;
                scrollIntoView(panel);
                modificationRows = panel.findElements(Locator.XPathLocator.tag("tr").withClass("x4-grid-data-row"));
            }
            return modificationRows;
        }

        protected WebElement getModificationRow(int row)
        {
            return getModificationRows().get(row);
        }

        protected List<WebElement> getModificationRowCells(int row)
        {
            if (modificationRowCells == null)
            {
                modificationRowCells = new HashMap<>();
            }
            return modificationRowCells.computeIfAbsent(row, r -> Collections.unmodifiableList(Locator.xpath("td").findElements(getModificationRow(r))));
        }
    }
}
