package org.labkey.test.tests.panoramapublic;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.panoramapublic.PanoramaPublicSearchWebPart;
import org.labkey.test.selenium.RefindingWebElement;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaWebPublicSearchTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String SKY_FILE_2 = "Olga_srm_course_heavy_light_w_maxquant_lib.sky.zip";

    private static final String SUBFOLDER_1 = "First Subfolder";
    private static final String SUBFOLDER_2 = "Second Subfolder";

    private static final String AUTHOR_FIRST_NAME = "Jane";
    private static final String AUTHOR_LAST_NAME = "Doe";

    ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);

    @BeforeClass
    public static void initialSetUp()
    {
        PanoramaWebPublicSearchTest init = (PanoramaWebPublicSearchTest) getCurrentTest();
        init.initialSetupHelper();
    }

    @Override
    public String getSampleDataFolder()
    {
        return "panoramapublic/";
    }

    private void initialSetupHelper()
    {
        goToProjectHome();
        portalHelper.addBodyWebPart("Panorama Public Search");
        portalHelper.addBodyWebPart("Targeted MS Experiment List");
        portalHelper.removeWebPart("Mass Spec Search");
        portalHelper.removeWebPart("Targeted MS Runs");

        setupSubfolder(getProjectName(), SUBFOLDER_1, FolderType.Experiment);
        importData(SKY_FILE_1, 1);
        createExperimentCompleteMetadata("Test experiment for search improvements");

        setupSubfolder(getProjectName(), SUBFOLDER_2, FolderType.Experiment);
        importData(SKY_FILE_2, 1);

        _userHelper.createUser(SUBMITTER);
        permissionsHelper.addMemberToRole(SUBMITTER, "Folder Administrator", PermissionsHelper.MemberType.user, getProjectName() + "/" + SUBFOLDER_2);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo(AUTHOR_FIRST_NAME, AUTHOR_LAST_NAME, SUBFOLDER_2);
        createExperimentCompleteMetadata("Submitter Experiment");
        stopImpersonating();
        portalHelper.exitAdminMode();
    }

    /*
        First Name and last name for the user needs to be set for Author in experiment to get populated.
     */
    private void updateSubmitterAccountInfo(String firstName, String lastName, String subfolder)
    {
        goToMyAccount();
        clickButton("Edit");
        setFormElement(Locator.name("quf_FirstName"), firstName);
        setFormElement(Locator.name("quf_LastName"), lastName);
        clickButton("Submit");
        navigateToFolder(getProjectName(), subfolder);
    }

    @Test
    public void testExperimentSearch()
    {
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        DataRegionTable table = panoramaPublicSearch.setAuthor(AUTHOR_LAST_NAME).searchExperiments();

        CustomizeView customizeView = table.openCustomizeGrid();
        customizeView.addColumn("Authors");
        customizeView.applyCustomView(0);
        checker().verifyEquals("Incorrect search result for author", 1, table.getDataRowCount());
        checker().verifyEquals("Incorrect result", AUTHOR_FIRST_NAME + " " + AUTHOR_LAST_NAME + ",", table.getDataAsText(0, "Authors"));

        table = panoramaPublicSearch
                .setOrganism("Homo")
                .setAuthor("")
                .setInstrument("Thermo")
                .searchExperiments();

        checker().verifyEquals("Incorrect search results", 2, table.getDataRowCount());
        checker().verifyEquals("Incorrect values for experiment title", Arrays.asList(" Test experiment for search improvements", " Submitter Experiment"),
                table.getColumnDataAsText("Title"));

        table = panoramaPublicSearch.setOrganism("")
                .setInstrument("")
                .setTitle("Experiment")
                .setAuthor(AUTHOR_FIRST_NAME + " " + AUTHOR_LAST_NAME)
                .searchExperiments();

        checker().verifyEquals("Incorrect search results", 1, table.getDataRowCount());
        checker().verifyEquals("Incorrect values for experiment title", Arrays.asList(" Submitter Experiment"),
                table.getColumnDataAsText("Title"));
    }

    @Test
    public void testProteinSearch()
    {
        log("Empty protein search");
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoProteinSearch().setProtein("").clickSearch();
        DataRegionTable table = DataRegionTable.findDataRegionWithinWebpart(this, "The searched protein '' appeared in the following experiments");
        checker().verifyEquals("Incorrect protein searched with partial match", 0, table.getDataRowCount());

        log("Protein : Partial match and results across folder");
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoProteinSearch().setProtein("R").clickSearch();

        table = DataRegionTable.findDataRegionWithinWebpart(this, "The searched protein 'R' appeared in the following experiments");
        checker().verifyEquals("Incorrect protein searched with partial match", 2, table.getDataRowCount());

        clickAndWait(Locator.linkWithText("3"));
        DataRegionTable detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect detailed information for protein", Arrays.asList("00716246|Carbonic", "129823|Lactoperoxidase", "1351907|Serum"),
                detailsTable.getColumnDataAsText("Label"));
        goBack();

        waitAndClickAndWait(Locator.linkWithText("10"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect detailed information for protein ", Arrays.asList("Rv0079|Rv0079", "Rv1738|Rv1738", "Rv1812c|Rv1812c", "Rv1996|Rv1996",
                        "Rv2027c|dosT", "Rv2031c|hspX", "Rv2623|TB31.7", "Rv2626c|hrp1", "Rv3132c|devS", "Rv3133c|devR"),
                detailsTable.getColumnDataAsText("Label"));
        checker().screenShotIfNewError("PartialProteinMatch");

        log("Protein : Exact match and result should be one row");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoProteinSearch().setProtein("00706094|Alpha").setProteinExactMatch(true).clickSearch();
        table = new DataRegionTable.DataRegionFinder(getDriver()).find(new RefindingWebElement(PortalHelper.Locators.webPartWithTitleContaining("The searched protein"), getDriver()));
        checker().verifyEquals("Incorrect protein searched with exact match", 1, table.getDataRowCount());
        checker().screenShotIfNewError("ExactProteinMatch");

        clickAndWait(Locator.linkWithText("1"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect information", Arrays.asList("00706094|Alpha"), detailsTable.getColumnDataAsText("Label"));

        log("Protein : Exact match and no result");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoProteinSearch().setProtein("00706094Alpha").setProteinExactMatch(true).clickSearch();
        table = new DataRegionTable.DataRegionFinder(getDriver()).find(new RefindingWebElement(PortalHelper.Locators.webPartWithTitleContaining("The searched protein"), getDriver()));
        checker().verifyEquals("Incorrect protein searched with exact match", 0, table.getDataRowCount());
    }

    @Test
    public void testPeptideSearch()
    {
        log("Empty peptide Search");
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoPeptideSearch().setPeptide("").clickSearch();
        DataRegionTable table = DataRegionTable.findDataRegionWithinWebpart(this, "The searched peptide '' appeared in the following experiments");
        checker().verifyEquals("Incorrect peptide searched with partial match", 0, table.getDataRowCount());

        log("Peptide : Partial match and results across folder");
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoPeptideSearch().setPeptide("VL").clickSearch();
        table = DataRegionTable.findDataRegionWithinWebpart(this, "The searched peptide 'VL' appeared in the following experiments");
        checker().verifyEquals("Incorrect peptide searched with partial match", 2, table.getDataRowCount());

        clickAndWait(Locator.linkWithText("3"));
        DataRegionTable detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect detailed information for peptide", Arrays.asList("IVGYLDEEGVLDQNR", "VLDALDSIK", "VLVLDTDYK"),
                detailsTable.getColumnDataAsText("ModifiedPeptideDisplayColumn"));
        goBack();

        waitAndClickAndWait(Locator.linkWithText("4"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect detailed information for peptide", Arrays.asList("GVLGALIEEPKPIR", "GVLGALIEEPKPIR", "VPAARPDVAVLDVR", "VPAARPDVAVLDVR"),
                detailsTable.getColumnDataAsText("ModifiedPeptideDisplayColumn"));
        checker().screenShotIfNewError("PartialPeptideMatch");

        log("Peptide : Exact match and result should be one row");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoPeptideSearch().setPeptide("GFCGLSQPK").setPeptideExactMatch(true).clickSearch();
        table = new DataRegionTable.DataRegionFinder(getDriver()).find(new RefindingWebElement(PortalHelper.Locators.webPartWithTitleContaining("The searched peptide"), getDriver()));

        checker().verifyEquals("Incorrect peptide searched with exact match", 1, table.getDataRowCount());
        checker().screenShotIfNewError("ExactPeptideMatch");

        clickAndWait(Locator.linkWithText("1"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect information", Arrays.asList("GFCGLSQPK"), detailsTable.getColumnDataAsText("ModifiedPeptideDisplayColumn"));

        log("Peptide : Exact match and no result");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        panoramaPublicSearch.gotoPeptideSearch().setPeptide("XYZ").setPeptideExactMatch(true).clickSearch();
        table = new DataRegionTable.DataRegionFinder(getDriver()).find(new RefindingWebElement(PortalHelper.Locators.webPartWithTitleContaining("The searched peptide"), getDriver()));
        checker().verifyEquals("Incorrect peptide searched with exact match", 0, table.getDataRowCount());
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false, SUBMITTER);
        super.doCleanup(afterTest);
    }
}
