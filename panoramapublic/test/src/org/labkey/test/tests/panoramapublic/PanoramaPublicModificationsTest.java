package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.WebPart;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.TextSearcher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicModificationsTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "heavy_and_combo_str_mods.sky.zip";
    private static final String SKY_FILE_2 = "hard-coded-and-wildcard-mods_v2.sky.zip";

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

        testSaveMatchForStructuralMod(propionylation, List.of(propionyl, new Unimod(206, "Delta:H(4)C(3)O(1)", propionyl.getFormula())),
                0);

        // Heavy structural modifications
        testSaveMatchForStructuralMod(heavyMonoMethyl, List.of(methylHeavy), 0);
        testSaveMatchForStructuralMod(heavyDimethyl, List.of(dimethylHeavy), 0);


        String methylPropionylFormula = "H6C4O";
        testDefineCombinationMod(methylPropionyl, methylPropionylFormula, methyl, methyl, "H4C2", "H4C3O", "H2C2O", false);
        testDefineCombinationMod(methylPropionyl, methylPropionylFormula, methyl, propionyl, methylPropionylFormula, "H4C3O", "", true);

        // Hard-coded and wild-card modifications
        testWildCardModifications();

         testCopy(projectName, folderName, experimentTitle,  folderName + " Copy");
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

        modsTable = new DataRegionTable(STRUCTURAL_MOD, this);
        checkModificationRow(modsTable, modificationName, matches.get(correctMatchIndex));
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

        var validationPage = submitValidationJob();
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

        submitIncompletePxButton();
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, folderName, experimentTitle, targetFolder);
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
            expectedText = String.format("**%s (%s)", assignedMatch1.getUnimodId(), assignedMatch1.getName());
            if (assignedMatch2 != null)
            {
                //Example: **UNIMOD:34 (Methyl)+UNIMOD:58 (Propionyl)
                expectedText = String.format("%s+%s (%s)", expectedText, assignedMatch2.getUnimodId(), assignedMatch2.getName());
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
        if (matches != null && matches.size() > 0)
        {
            for (var unimod: matches)
            {
                expectedText += String.format("**%s (%s)\n", unimod.getUnimodId(), unimod.getName()) ;
            }
        }
        else
        {
            expectedText = "FIND MATCH";
        }
        return checkModRow(modsTable, modificationName, expectedText);
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
