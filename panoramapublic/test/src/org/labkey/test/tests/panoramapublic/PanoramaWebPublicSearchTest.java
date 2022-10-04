package org.labkey.test.tests.panoramapublic;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.panoramapublic.PanoramaPublicSearchWebPart;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PermissionsHelper;

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
    public void testExperimentalSearch()
    {
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        DataRegionTable table = panoramaPublicSearch.setAuthors(AUTHOR_FIRST_NAME).search();
        CustomizeView customizeView = table.openCustomizeGrid();
        customizeView.addColumn("Authors");
        customizeView.clickViewGrid();
        table = panoramaPublicSearch.refreshedTable();
        checker().verifyEquals("Incorrect search result for author", 1, table.getDataRowCount());
//        checker().verifyEquals("Incorrect row information", Arrays.asList(""), table.getRowDataAsText(0));

        panoramaPublicSearch.setAuthors("").setOrganism("Homo").search();
        checker().verifyEquals("Incorrect search result for Organism", 2, table.getDataRowCount());
        checker().verifyEquals("Incorrect tittle filtered", Arrays.asList(" Test experiment for search improvements", " Submitter Experiment"), table.getColumnDataAsText("Title"));
    }

    @Test
    public void testProteinSearch()
    {
        log("Protein : Partial match and results across folder");
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        DataRegionTable table = panoramaPublicSearch.gotoProteinSearch().setProtein("R").search();
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
        table = panoramaPublicSearch.gotoProteinSearch().setProtein("00706094|Alpha").setProteinExactMatch(true).search();
        checker().verifyEquals("Incorrect protein searched with exact match", 1, table.getDataRowCount());
        checker().screenShotIfNewError("ExactProteinMatch");

        clickAndWait(Locator.linkWithText("1"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect information", Arrays.asList("00706094|Alpha"), detailsTable.getColumnDataAsText("Label"));

        log("Protein : Exact match and no result");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        table = panoramaPublicSearch.gotoProteinSearch().setProtein("00706094Alpha").setProteinExactMatch(true).search();
        checker().verifyEquals("Incorrect protein searched with exact match", 0, table.getDataRowCount());
    }

    @Test
    public void testPeptideSearch()
    {
        log("Peptide : Partial match and results across folder");
        goToProjectHome();
        PanoramaPublicSearchWebPart panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        DataRegionTable table = panoramaPublicSearch.gotoPeptideSearch().setPeptide("VL").search();
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
        table = panoramaPublicSearch.gotoPeptideSearch().setPeptide("GFCGLSQPK").setPeptideExactMatch(true).search();
        checker().verifyEquals("Incorrect peptide searched with exact match", 1, table.getDataRowCount());
        checker().screenShotIfNewError("ExactPeptideMatch");

        clickAndWait(Locator.linkWithText("1"));
        detailsTable = new DataRegionTable.DataRegionFinder(getDriver()).find();
        checker().verifyEquals("Incorrect information", Arrays.asList("GFCGLSQPK"), detailsTable.getColumnDataAsText("ModifiedPeptideDisplayColumn"));

        log("Peptide : Exact match and no result");
        goToProjectHome();
        panoramaPublicSearch = new PanoramaPublicSearchWebPart(getDriver(), "Panorama Public Search");
        table = panoramaPublicSearch.gotoPeptideSearch().setPeptide("XYZ").setPeptideExactMatch(true).search();
        checker().verifyEquals("Incorrect peptide searched with exact match", 0, table.getDataRowCount());
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false, SUBMITTER);
        super.doCleanup(afterTest);
    }
}
