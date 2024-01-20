package org.labkey.test.tests.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMyDataViewTest extends PanoramaPublicBaseTest
{
    private static final String SUBMITTER_1 = "submitter1@panoramapublic.test";
    private static final String SUBMITTER_2 = "submitter2@panoramapublic.test";

    private static final String COL_TITLE = "Title";
    private static final String COL_PUBLIC = "Public";
    private static final String COL_CATALOG_ENTRY = "Catalog Entry";

    @BeforeClass
    public static void initialSetUp()
    {
        PanoramaPublicMyDataViewTest test = (PanoramaPublicMyDataViewTest) getCurrentTest();
        test.init();
    }

    private void init()
    {
        goToProjectHome(PANORAMA_PUBLIC);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.setSiteGroupPermissions("Guests", "Reader");
        portalHelper.removeAllWebParts();
        portalHelper.addBodyWebPart("Panorama Public Search");
        portalHelper.addBodyWebPart("Targeted MS Experiment List");
        portalHelper.addBodyWebPart("Messages");

        // Enable Panorama Public catalog entries in the admin console.
        enableCatalogEntries();
    }

    @Override
    public String getSampleDataFolder()
    {
        return "panoramapublic/";
    }

    @Test
    public void testMyDataView()
    {
        // Set up our source folders, and submit to "Panorama Public"
        String projectName = getProjectName();

        // Dataset 1 by submitter1
        String experimentTitle_1_1 = "This is dataset One submitted by Submitter One";
        setupFolderSubmitAndCopy(projectName, "Folder Submitter1 Data1", "Test Copy 1_1", experimentTitle_1_1, SUBMITTER_1, "One", null, SKY_FILE_1);

        // Dataset 2 by submitter1
        String experimentTitle_1_2 = "This is dataset Two submitted by Submitter One";
        setupFolderSubmitAndCopy(projectName, "Folder Submitter1 Data2", "Test Copy 1_2", experimentTitle_1_2, SUBMITTER_1, null, null, SKY_FILE_1);

        // Dataset 1 by submitter2
        String experimentTitle_2_1 = "This is dataset One submitted by Submitter Two";
        setupFolderSubmitAndCopy(projectName, "Folder Submitter2 Data1", "Test Copy 2_1", experimentTitle_2_1, SUBMITTER_2, "Two", null, SKY_FILE_1);

        // Verify the "My Data" views for each submitter
        viewSubmitterData(SUBMITTER_1, List.of(experimentTitle_1_1, experimentTitle_1_2), true);
        viewSubmitterData(SUBMITTER_2, List.of(experimentTitle_2_1), false);

        // Sign out. Guest should not be able to see the "My Data" button
        simpleSignOut();
        goToProjectHome(PANORAMA_PUBLIC);
        var table = new DataRegionTable.DataRegionFinder(getDriver()).refindWhenNeeded();
        assertFalse(table.hasHeaderMenu("My Data"));

        simpleSignIn();
    }

    private void viewSubmitterData(String submitter, List<String> experimentTitles, boolean addCatalogEntry)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectHome(PANORAMA_PUBLIC);
        impersonate(submitter);

        DataRegionTable table = myDataView();
        assertEquals("Unexpected row count", experimentTitles.size(), table.getDataRowCount());
        List<String> expectedColumns = List.of(COL_TITLE, COL_PUBLIC, COL_CATALOG_ENTRY);
        List<String> actualColumns = table.getColumnLabels();
        assertTrue("Expected table columns " + expectedColumns + ". Found: " + actualColumns,
                actualColumns.containsAll(expectedColumns));

        List<String> actualExptTitles = table.getColumnDataAsText(COL_TITLE);
        assertEquals("Unexpected experiment titles", experimentTitles.stream().sorted().collect(Collectors.toList()),
                actualExptTitles.stream().map(String::trim).sorted().collect(Collectors.toList()));


        int catalogEntryCol = table.getColumnIndex(COL_CATALOG_ENTRY);
        int dataPublicCol = table.getColumnIndex(COL_PUBLIC);
        verifyColumnValues(table, catalogEntryCol, dataPublicCol, experimentTitles.size());

        String firstExptTitle = actualExptTitles.get(0);

        // Make one dataset public
        int titleCol = table.getColumnIndex(COL_TITLE);
        int rowIdx = getExperimentRowIndex(table, firstExptTitle, titleCol);
        clickAndWait(table.link(rowIdx, titleCol)); // Go to the folder where the data lives
        makeDataPublic(true);

        // The "Catalog Entry" column for the data that was made public should have an icon linking to the AddCatalogEntry action.
        verifyColumnValues(catalogEntryCol, dataPublicCol, titleCol, experimentTitles.size(), firstExptTitle, false);

        if (addCatalogEntry)
        {
            // Add a catalog entry
            WebElement catalogEntryLink = table.link(rowIdx, catalogEntryCol);
            clickAndWait(catalogEntryLink);
            createCatalogEntry();

            // The "Catalog Entry" column for the data to which the catalog entry was added should have an icon linking to the ViewCatalogEntry action.
            verifyColumnValues(catalogEntryCol, dataPublicCol, titleCol, experimentTitles.size(), firstExptTitle, true);
        }
    }

    private int getExperimentRowIndex(DataRegionTable table, String firstExptTitle, int titleCol)
    {
        int rowIdx = table.getRowIndex(titleCol, firstExptTitle);
        assertNotEquals("Row index not found for experiment title: " + firstExptTitle, -1, rowIdx);
        return rowIdx;
    }

    private void verifyColumnValues(DataRegionTable table, int catalogEntryCol, int dataPublicCol, int rowCount)
    {
        verifyColumnValues(table, catalogEntryCol, dataPublicCol, rowCount, -1, false);
    }

    private void verifyColumnValues(int catalogEntryCol, int dataPublicCol, int titleCol, int rowCount, String publicExptTitle, boolean hasCatalogEntry)
    {
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable table = myDataView();
        int rowIdx = getExperimentRowIndex(table, publicExptTitle, titleCol);
        verifyColumnValues(table, catalogEntryCol, dataPublicCol, rowCount, rowIdx, hasCatalogEntry);
    }

    private void verifyColumnValues(DataRegionTable table, int catalogEntryCol, int dataPublicCol, int rowCount, int publicRowIdx, boolean hasCatalogEntry)
    {
        for (int row = 0; row < rowCount; row++)
        {
            String expectedPublicValue = row == publicRowIdx ? "Yes" : "No";
            assertEquals("Unexpected value in \"Public\" column for row " + row,  expectedPublicValue, table.getDataAsText(row, dataPublicCol));
            if (row == publicRowIdx)
            {
                // If this is a row for a public dataset, we expect to see an icon linking to either
                // AddCatalogEntryAction if the data does not have a catalog entry, or
                // ViewCatalogEntryAction if the data has a catalog entry.
                WebElement catalogEntryLink = table.link(row, catalogEntryCol);
                String href = catalogEntryLink.getAttribute("href");
                String expectedInHref = hasCatalogEntry ? "panoramapublic-viewCatalogEntry.view" : "panoramapublic-addCatalogEntry.view";
                String linkTitle = hasCatalogEntry ? "View catalog entry" : "Add catalog entry";
                assertTrue("Expected \"" + expectedInHref + "\" in link. Found: " + href,  href.contains(expectedInHref));
                assertEquals("Unexpected link title", linkTitle, catalogEntryLink.getAttribute("title"));
                WebElement imgEl = catalogEntryLink.findElement(By.tagName("img"));
                String expectedIconImg = hasCatalogEntry ? "slideshow-icon-green.png" : "slideshow-icon.png";
                String catalogEntryIcon = imgEl.getAttribute("src");
                assertTrue("Expected " + expectedIconImg + " in catalog entry icon. Found: " + catalogEntryIcon, imgEl.getAttribute("src").contains(expectedIconImg));
            }
            else
            {
                // This data is not public. The Catalog Entry column should be blank.
                assertEquals("Unexpected value in \"Catalog Entry\" column for row " + row, "", table.getDataAsText(row, catalogEntryCol).trim());
            }
        }
    }

    @NotNull
    private DataRegionTable myDataView()
    {
        var table = new DataRegionTable.DataRegionFinder(getDriver()).refindWhenNeeded();
        assertTrue(table.hasHeaderMenu("My Data"));
        table.clickHeaderButtonAndWait("My Data");
        return new DataRegionTable.DataRegionFinder(getDriver()).refindWhenNeeded();
    }
}
