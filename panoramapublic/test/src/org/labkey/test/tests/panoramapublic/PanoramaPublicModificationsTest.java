package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.WebPart;
import org.labkey.test.components.html.BootstrapMenu;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.EscapeUtil;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.TextSearcher;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicModificationsTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "heavy_and_combo_str_mods.sky.zip";
    private static final String SKY_FILE_2 = "hard-coded-and-wildcard-mods_v5.sky.zip";

    private static final String STRUCTURAL_MOD = "Structural Modifications";
    private static final String ISOTOPE_MOD = "Isotope Modifications";


    private final Unimod methyl = new Unimod(34, "Methyl", "H2C");
    private final Unimod propionyl = new Unimod(58, "Propionyl", "H4C3O");
    private final Unimod methylHeavy = new Unimod(298, "Methyl:2H(3)", "H'3C - H");
    private final Unimod dimethylHeavy = new Unimod(1291, "Dimethyl:2H(6)", "H'6C2 - H2");
    private final Unimod label13C3 = new Unimod(1296, "Label:13C(3)", "C'3 - C3");
    private final Unimod label13C4 = new Unimod(1266, "Label:13C(4)", "C'4 - C4");
    private final Unimod label13C5 = new Unimod(772, "Label:13C(5)", "C'5 - C5");
    private final Unimod label13C6 = new Unimod(188, "Label:13C(6)", "C'6 - C6");
    private final Unimod label13C9 = new Unimod(184, "Label:13C(9)", "C'9 - C9");

    private final Unimod label15N1 = new Unimod(994, "Label:15N(1)", "N' - N");
    private final Unimod label15N2 = new Unimod(995, "Label:15N(2)", "N'2 - N2");
    // private final Unimod label15N3 = new Unimod(996, "Label:15N(3)", "N'3 - N3");
    private final Unimod label15N4 = new Unimod(897, "Label:15N(4)", "N'4 - N4");

    private final Unimod label13C315N1 = new Unimod(1297, "Label:13C(3)15N(1)", "C'3N' - C3N");
    // private final Unimod label13C415N1 = new Unimod(1298, "Label:13C(4)15N(1)", "C'4N' - C4N");
    private final Unimod label13C515N1 = new Unimod(268, "Label:13C(5)15N(1)", "C'5N' - C5N");
    private final Unimod label13C615N1 = new Unimod(695, "Label:13C(6)15N(1)", "C'6N' - C6N");
    private final Unimod label13C615N2 = new Unimod(259, "Label:13C(6)15N(2)", "C'6N'2 - C6N2");
    // private final Unimod label13C615N4 = new Unimod(267, "Label:13C(6)15N(4)", "C'6N'4 - C6N4");
    // private final Unimod label13C915N1 = new Unimod(269, "Label:13C(9)15N(1)", "C'9N' - C9N");

    private final String methylPropionyl = "MethylPropionyl";
    private final String propionylation = "Propionylation";
    private static final String heavyMonoMethyl = "Heavy monomethyl";
    private static final String heavyDimethyl = "Heavy dimethyl";

    @Test
    public void testAddModInfo()
    {
        String projectName = getProjectName();
        String folderName = "ModificationsTest";

        setupSourceFolder(projectName, folderName, SUBMITTER);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Upload document
        importData(SKY_FILE_1, 1);
        importData(SKY_FILE_2, 2);


        goToDashboard();
        portalHelper.enterAdminMode();
        portalHelper.addBodyWebPart(STRUCTURAL_MOD);
        portalHelper.addBodyWebPart(ISOTOPE_MOD);

        testCustomizeGrid(STRUCTURAL_MOD, false);
        testCustomizeGrid(ISOTOPE_MOD, false);

        // Add the "Targeted MS Experiment" webpart
        String experimentTitle = "This is an experiment to test user-entered modification information";
        createExperimentCompleteMetadata(experimentTitle);

        goToDashboard();

        testCustomizeGrid(STRUCTURAL_MOD, true);
        testCustomizeGrid(ISOTOPE_MOD, true);

        // The web part title links to the full query grid. It must not let a caller widen the
        // scope to all folders.
        verifyModificationQueryScopeIsRestricted();
        // The standalone web part grid on this page must not be widened by a URL either.
        verifyModificationWebPartScopeIsRestricted();

        // Match structural modification
        goToExperimentDetailsPage();
        testSaveMatchForStructuralMod(propionylation, List.of(propionyl, new Unimod(206, "Delta:H(4)C(3)O(1)", propionyl.getFormula())),
                0);
        testNoMatchForStructuralMod("Acetyl");

        // Heavy structural modifications
        testSaveMatchForStructuralMod(heavyMonoMethyl, List.of(methylHeavy), 0);
        testSaveMatchForStructuralMod(heavyDimethyl, List.of(dimethylHeavy), 0);

        // Combination modifications
        String methylPropionylFormula = "H6C4O";
        testDefineCombinationMod(methylPropionyl, methylPropionylFormula, methyl, methyl, "H4C2", "H4C3O", "H2C2O", false);
        testDefineCombinationMod(methylPropionyl, methylPropionylFormula, methyl, propionyl, methylPropionylFormula, "H4C3O", "", true);

        // Hard-coded and wild-card modifications
        testWildCardModifications();

        testCopy(projectName, folderName, experimentTitle,  folderName + " Copy");
    }

    // CreateView on these queries restricts the grids to the current folder or the current folder and subfolders.
    // Check that a wider scope asked for in the URL is restricted, and that an allowed scope still works.
    private void verifyModificationQueryScopeIsRestricted()
    {
        for (String queryName : List.of("IsotopeModifications", "StructuralModifications"))
        {
            // The Folder Filter dropdown offers the narrow scopes but not All Folders.
            assertFolderFilterMenuHidesAllFolders(queryName);
            // A URL asking for All Folders is restricted to the current and subfolders.
            assertContainerFilterScope(queryName, "AllFolders", DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);
            // "Current and Subfolders" is allowed.
            assertContainerFilterScope(queryName, "CurrentAndSubfolders", DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);
        }
    }

    // The standalone modification web parts (added to a folder page, not the experiment details page)
    // read the scope straight from the URL. Check that a wider folder scope in the URL is restricted there too.
    private void verifyModificationWebPartScopeIsRestricted()
    {
        for (String webPartName : List.of(STRUCTURAL_MOD, ISOTOPE_MOD))
        {
            // Go to the dashboard tab that holds the web parts, then re-request the same page with a
            // "AllFolders" filter in the URL. The web part's data region is named after its title.
            goToDashboard();
            String url = getCurrentRelativeURL();
            url += (url.contains("?") ? "&" : "?") + EscapeUtil.encode(webPartName) + ".containerFilterName=AllFolders";
            beginAt(url);
            var grid = new DataRegionTable(webPartName, this);
            assertEquals("Standalone '" + webPartName + "' web part should restrict an All Folders URL to the current and subfolders",
                    DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS, grid.getContainerFilter());
        }
    }

    private void assertFolderFilterMenuHidesAllFolders(String queryName)
    {
        beginAt(WebTestHelper.buildURL("query", getCurrentContainerPath(), "executeQuery",
                Map.of("schemaName", "panoramapublic", "query.queryName", queryName)));
        BootstrapMenu viewsMenu = new DataRegionTable("query", this).getViewsMenu();
        viewsMenu.openMenuTo("Folder Filter", "Folder Filter");
        List<String> options = viewsMenu.findVisibleMenuItems().stream().map(WebElement::getText).map(String::trim).toList();
        // The allowed scope is present. This also guards against an empty menu making the check below pass for free.
        assertTrue("Folder Filter for query '" + queryName + "' should offer 'Current folder and subfolders'. Found: " + options,
                options.contains(DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS.getLabel()));
        // All Folders is not offered.
        assertFalse("Folder Filter for query '" + queryName + "' must not offer 'All Folders'. Found: " + options,
                options.contains(DataRegionTable.ContainerFilterType.ALL_FOLDERS.getLabel()));
    }

    private void assertContainerFilterScope(String queryName, String requestedFilterName, DataRegionTable.ContainerFilterType expected)
    {
        beginAt(WebTestHelper.buildURL("query", getCurrentContainerPath(), "executeQuery",
                Map.of("schemaName", "panoramapublic",
                        "query.queryName", queryName,
                        "query.containerFilterName", requestedFilterName)));
        var grid = new DataRegionTable("query", this);
        assertEquals("Folder Filter for query '" + queryName + "' requested as '" + requestedFilterName + "' was not the expected scope",
                expected, grid.getContainerFilter());
    }

    private void testNoMatchForStructuralMod(String modificationName)
    {
        var modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        int rowIdx = checkModificationRow(modsTable, modificationName, null);
        clickFindMatchInRow(modsTable, rowIdx);
        assertTextPresent("Unimod Match Options ");
        clickButton("Unimod Match");
        assertTextPresent("Cannot find a Unimod match for a structural modification that does not have modified amino acids or a modified terminus");
        findButton("Back").click();
    }

    private void testDefineCombinationMod(String modificationName, String modFormula, Unimod unimod1, Unimod unimod2, String combinedFormula, String difference1, String difference2, boolean balanced)
    {
        goToDashboard();
        goToExperimentDetailsPage();

        var modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        int rowIdx = checkModificationRow(modsTable, modificationName);

        clickFindMatchInRow(modsTable, rowIdx);
        assertTextPresent("Unimod Match Options ");
        clickButton("Combination Modification");

        assertTextPresent("Define Combination Modification");
        var webpart = portalHelper.getBodyWebPart("Combination Modification");
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabel("Unimod Modification 1:"), unimod1.displayName());

        // Examples: H2C+---=H2C (Difference: H4C3O) OR H2C+H2C=H4C2 (Difference: H2C2O)
        String formatString = "%s+%s=%s (Difference: %s)";
        checkFormulaDiff(webpart, String.format(formatString, unimod1.getFormula(), "---", unimod1.getFormula(), difference1), false);

        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabel("Unimod Modification 2:"), unimod2.displayName());
        if (balanced)
        {
            checkFormulaDiff(webpart, String.format("%s+%s=%s", unimod1.getFormula(), unimod2.getFormula(), modFormula), true);
        }
        else
        {
            checkFormulaDiff(webpart, String.format(formatString, unimod1.getFormula(), unimod2.getFormula(), combinedFormula, difference2), false);
        }

        clickButton("Save");
        if (!balanced)
        {
            assertTextPresent(String.format("Selected Unimod modification formulas do not add up to the formula of the modification. Combined formula is %s. Difference from the modification formula is %s.",
                    combinedFormula, difference2));
        }
        else
        {
            assertTextPresent("Unimod information for combination modification", modificationName, "was successfully saved");
            clickExperimentDetailsLink();
            modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
            checkModificationRow(modsTable, modificationName, unimod1, unimod2);
        }
    }

    private void checkFormulaDiff(WebPart webpart, String expectedText, boolean balanced)
    {
        var formulaDiff = Locator.XPathLocator.tag("div").withClass("alert").findElement(webpart);
        assertTrue("Expected formula diff: " + expectedText + " but found " + formulaDiff.getText(), formulaDiff.getText().contains(expectedText));
        assertTrue(formulaDiff.getAttribute("class").contains(balanced ? "alert-info" : "alert-warning"));
        if (balanced)
        {
            assertNotNull("Expected balanced formula indicator", Locator.XPathLocator.tag("span").withClass("fa fa-check-circle").findElementOrNull(formulaDiff));
        }
        else
        {
            assertNotNull("Expected unbalanced formula indicator", Locator.XPathLocator.tag("span").withClass("fa fa-times-circle").findElementOrNull(formulaDiff));
        }
    }

    private void testSaveMatchForStructuralMod(String modificationName, List<Unimod> matches, int correctMatchIndex)
    {
        var modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        int rowIdx = checkModificationRow(modsTable, modificationName, null);

        clickFindMatchInRow(modsTable, rowIdx);
        assertTextPresent("Unimod Match Options ");
        clickButton("Unimod Match");
        List<String> expectedTexts = new ArrayList<>();
        expectedTexts.add("Unimod Match");
        expectedTexts.add("The modification matches " + matches.size() + " Unimod modification" + (matches.size() > 1 ? "s" : ""));
        int i = 0;
        for (Unimod unimod: matches)
        {
            expectedTexts.add("-- Unimod Match" + (matches.size() > 1 ? " " + ++i : "") + " --");
            expectedTexts.add(unimod.getUnimodId());
        }
        var unimodMatchWebPart = portalHelper.getBodyWebPart("Unimod Match");
        assertTextPresentInThisOrder(new TextSearcher(unimodMatchWebPart.getComponentElement().getText()), expectedTexts.toArray(new String[0]));

        clickButtonByIndex("Save Match", correctMatchIndex);
        assertTextPresent("Unimod information for structural modification", modificationName, "was successfully saved");
        clickExperimentDetailsLink();


        modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        checkModificationRow(modsTable, modificationName, matches.get(correctMatchIndex));
    }

    private void clickExperimentDetailsLink()
    {
        var exptDetailsLink = Locator.XPathLocator.tag("a").withText("[View Experiment Details]").findElement(getDriver());
        exptDetailsLink.click();
    }


    private void testCustomizeGrid(String drName, boolean folderHasExperiment)
    {
        var modsTable = new DataRegionTable(drName, this);
        var customizeView = modsTable.openCustomizeGrid();
        customizeView.addColumn("UnimodMatch");
        customizeView.applyCustomView();
        List<String> unimodMatchCellText = modsTable.getColumnDataAsText("UnimodMatch");
        if (folderHasExperiment)
        {
            // Folder has an experiment and the test document has modifications without Unimod Ids.  We expect to see "Find Match" links
            assertTrue("Expected 'Find Match' text in the UnimodMatch column of the " + drName + " table. Folder has an experiment",
                    unimodMatchCellText.stream().anyMatch(t -> t.contains("FIND MATCH")));
        }
        else
        {
            // If the folder does not have an experiment then we should not see the "Find Match" link
            assertTrue("Unexpected 'Find Match' text in the UnimodMatch column of the " + drName + " table. Folder does not have an experiment",
                    unimodMatchCellText.stream().noneMatch(t -> t.contains("FIND MATCH")));
        }
    }

    private void testWildCardModifications()
    {
        goToExperimentDetailsPage();
        // There should be no matches yet for these modifications
        var modsTable = new DataRegionTable(ISOTOPE_MOD, this);
        checkModificationRow(modsTable, null, "Label:13C");
        checkModificationRow(modsTable, null, "Label:15N");
        checkModificationRow(modsTable, null, "Label:13C15N");

        int rowIdx = checkModificationRow(modsTable, "Label:15N", null);
        clickFindMatchInRow(modsTable, rowIdx);
        assertTextPresent("Cannot find a Unimod match for an isotope modification that does not have modified amino acids");
        clickButton("Back");

        // heavyK_R is a dummy modification in the test document that is defined on K and R with heavy C and N.
        // K and R have different number of Nitrogen atoms so we will not be able to calculate a formula for this modification.
        rowIdx = checkModificationRow(modsTable, "heavyK_R", null);
        clickFindMatchInRow(modsTable, rowIdx);
        assertTextPresent("Error finding Unimod match. Cannot calculate formula for isotope modification 'heavyK_R'. " +
                "The modification is defined on multiple amino acids, but the number of labeled atoms in the amino acids are not the same. " +
                "To calculate the formula for an isotope modification all amino acids in the modification definition must have the same number of labeled atoms");
        clickButton("Back");

        var validationPage = submitValidationJob();
        validationPage.verifyValidationCurrent();
        // After running the data validation job the wildcard modifications should have a match
        validationPage.verifyWildCardModStatus("Label:13C", true, List.of(
                new Pair(label13C3.getUnimodId(), label13C3.getName()),
                new Pair(label13C4.getUnimodId(), label13C4.getName()),
                new Pair(label13C5.getUnimodId(), label13C5.getName()),
                new Pair(label13C6.getUnimodId(), label13C6.getName()),
                new Pair(label13C9.getUnimodId(), label13C9.getName())));

        goToExperimentDetailsPage();
        modsTable = new DataRegionTable(ISOTOPE_MOD, this);
        verifyWildcardMods(modsTable);
    }

    private void verifyWildcardMods(DataRegionTable modsTable)
    {
        checkModificationRow(modsTable, List.of(label13C3, label13C4, label13C5, label13C6, label13C9), "Label:13C");
        // label15N3 should not be included since 'H' is not included in the modification sites
        checkModificationRow(modsTable, List.of(label15N1, label15N2, label15N4), "Label:15N");
        // label13C414N1, label13C615N4, label13C915N1 should not be included since we don't have the modification sites for those modifications
        checkModificationRow(modsTable, List.of(label13C315N1, label13C515N1, label13C615N1, label13C615N2), "Label:13C15N");
    }

    private void testCopy(String projectName, String folderName, String experimentTitle, String targetFolder)
    {
        goToExperimentDetailsPage();
        var expWebPart = new TargetedMsExperimentWebPart(this);
        expWebPart.clickSubmit();
        var validationPage = new DataValidationPage(this);

        validationPage.verifyModificationStatus(propionylation, true, propionyl.getUnimodId(), propionyl.getName());
        validationPage.verifyModificationStatus(methylPropionyl, true, methyl.getUnimodId(), methyl.getName(), propionyl.getUnimodId(), propionyl.getName());

        String shortAccessUrl = submitIncompletePxButton();

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, folderName, experimentTitle, targetFolder, shortAccessUrl);
        goToProjectFolder(PANORAMA_PUBLIC, targetFolder);
        goToExperimentDetailsPage();
        var modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        checkModificationRow(modsTable, propionylation, propionyl);
        checkModificationRow(modsTable, methylPropionyl, methyl, propionyl);
        checkModificationRow(modsTable, heavyMonoMethyl, methylHeavy);
        checkModificationRow(modsTable, heavyDimethyl, dimethylHeavy);

        modsTable = new DataRegionTable(ISOTOPE_MOD, this);
        verifyWildcardMods(modsTable);
    }

    private int checkModificationRow(DataRegionTable modsTable, String modificationName)
    {
        return checkModificationRow(modsTable, modificationName, null, null);
    }

    private int checkModificationRow(DataRegionTable modsTable, String modificationName, Unimod assignedMatch1)
    {
        return checkModificationRow(modsTable, modificationName, assignedMatch1, null);
    }

    private int checkModificationRow(DataRegionTable modsTable, String modificationName, Unimod assignedMatch1, Unimod assignedMatch2)
    {
        String expectedText;
        if (assignedMatch1 != null)
        {
            expectedText = String.format("**%s(%s)", assignedMatch1.getUnimodId(), assignedMatch1.getName());
            if (assignedMatch2 != null)
            {
                //Example: **UNIMOD:34 (Methyl)+UNIMOD:58 (Propionyl)
                expectedText = String.format("%s+%s(%s)", expectedText, assignedMatch2.getUnimodId(), assignedMatch2.getName());
            }
        }
        else
        {
            expectedText = "FIND MATCH";
        }

        return checkModRow(modsTable, modificationName, expectedText);
    }

    private int checkModRow(DataRegionTable modsTable, String modificationName, String expectedText)
    {
        int rowIdx = modsTable.getRowIndex("ModId/Name", modificationName);
        assertNotEquals("Expected a row in the " + modsTable.getDataRegionName() + " table for modification name " + modificationName, -1, rowIdx);
        var cellText = modsTable.getDataAsText(rowIdx, "UnimodMatch");
        assertTrue("UnimodMatch cell text (" + cellText + ") does not contain expected text: " + expectedText, cellText.contains(expectedText));
        return rowIdx;
    }

    private int checkModificationRow(DataRegionTable modsTable,  List<Unimod> matches, String modificationName)
    {
        String expectedText = "";
        if (matches != null && !matches.isEmpty())
        {
            for (var unimod: matches)
            {
                expectedText += String.format("**%s(%s)\n", unimod.getUnimodId(), unimod.getName()) ;
            }
        }
        else
        {
            expectedText = "FIND MATCH";
        }
        return checkModRow(modsTable, modificationName, expectedText.trim());
    }

    private void clickFindMatchInRow(DataRegionTable modsTable, int rowIdx)
    {
        var row = modsTable.findRow(rowIdx);
        var findMatchLink = Locator.XPathLocator.tag("a").withText("Find Match").findElement(row);
        clickAndWait(findMatchLink);
    }

    @Override
    public String getSampleDataFolder()
    {
        return PanoramaPublicTest.SAMPLEDATA_FOLDER;
    }

    private static class Unimod
    {
        private final int _unimodId;
        private final String _name;
        private final String _formula;

        public Unimod(int unimodId, String name, String formula)
        {
            _unimodId = unimodId;
            _name = name;
            _formula = formula;
        }

        public String getUnimodId()
        {
            return "UNIMOD:" + _unimodId;
        }

        public String getName()
        {
            return _name;
        }

        public String getFormula()
        {
            return _formula;
        }

        public String displayName()
        {
            return _name + ", " + _formula + ", " + "Unimod:" + _unimodId;
        }
    }
}
