package org.labkey.test.tests.panoramapublic;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.PermissionsHelper;

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
        createExperimentCompleteMetadata("Test experiment for search improvements " + TRICKY_CHARACTERS_NO_QUOTES);

        setupSubfolder(getProjectName(), SUBFOLDER_2, FolderType.Experiment);
        importData(SKY_FILE_2, 1);

        _userHelper.createUser(SUBMITTER);
        permissionsHelper.addMemberToRole(SUBMITTER, "Folder Administrator", PermissionsHelper.MemberType.user, getProjectName() + "/" + SUBFOLDER_2);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo(AUTHOR_FIRST_NAME, AUTHOR_LAST_NAME, SUBFOLDER_2);
        createExperimentCompleteMetadata("Submitter Experiment");
        stopImpersonating();
    }

    /*
        First Name and last name for the user needs to be set for Author to get populated.
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
    }

    @Test
    public void testProteinSearch()
    {
        goToProjectHome();
    }

    @Test
    public void testPeptideSearch()
    {
        goToProjectHome();
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false, SUBMITTER);
        super.doCleanup(afterTest);
    }
}
