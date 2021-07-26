package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;

import java.io.File;

import static org.junit.Assert.assertEquals;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String RAW_FILE_WIFF = "Site52_041009_Study9S_Phase-I.wiff";
    private static final String RAW_FILE_WIFF_SCAN = RAW_FILE_WIFF + ".scan";

    private static final String SUBMITTER_2 = "submitter_2@panoramapublic.test";

    private static final String INCLUDE_SUBFOLDERS_AND_SUBMIT = "Include Subfolders And Continue";
    private static final String EXCLUDE_SUBFOLDERS_AND_SUBMIT = "Skip Subfolders And Continue";

    protected File getSampleDataPath(String file)
    {
        return TestFileUtils.getSampleData("TargetedMS/" + file);
    }

    @Test
    public void testExperimentCopy()
    {
        // Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Folder 1";
        String targetFolder = "Test Copy 1";
        String experimentTitle = "This is a test experiment";
        setupSourceFolder(projectName, folderName, SUBMITTER);

        impersonate(SUBMITTER);

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createTargetedMsExperimentWebPart(experimentTitle);

        // Should show error message since there are no Skyline documents in the folder.
        testSubmitWithNoSkyDocs(expWebPart);

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Should show an error message since the submitter's account info does not have a first and last name
        testSubmitWithIncompleteAccountInfo(expWebPart);
        updateSubmitterAccountInfo("One");

        // Click Submit.  Expect to see the missing information page. Submit the experiment by clicking the
        // "Continue without a ProteomeXchange ID" link
        testSubmitWithMissingRawFiles(portalHelper, expWebPart);

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, folderName, experimentTitle, false, targetFolder);

        // Test re-submit experiment
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        goToDashboard();
        expWebPart.clickResubmit();
        resubmitWithoutPxd();
        goToDashboard();
        assertTextPresent("Copy Pending!");

        copyExperimentAndVerify(projectName, folderName, experimentTitle, true, targetFolder);
    }

    @Test
    public void testCopyExperimentWithSubfolder()
    {
        String projectName = getProjectName();
        String sourceFolder = "Folder 2";
        String subfolder = "Subfolder_for_test";
        String targetFolder = "Test Copy 2";
        String experimentTitle = "Test experiment with subfolders";

        setupSourceFolder(projectName, sourceFolder, SUBMITTER, SUBMITTER_2);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createTargetedMsExperimentWebPart(experimentTitle);

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Create a subfolder
        _containerHelper.createSubfolder(projectName, sourceFolder, subfolder, "Collaboration", null, false);

        goToProjectFolder(projectName, sourceFolder);
        testSubmitWithSubfolders(expWebPart);

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, sourceFolder, subfolder, experimentTitle, false, targetFolder);

        // Remove permissions for SUBMITTER_2 from the subfolder, and try to resubmit the experiment as SUBMITTER_2. This user
        // should not be able to resubmit because the experiment was configured by SUBMITTER to include subfolders, and read permissions
        // in all subfolders are required for submitting an experiment.
        goToProjectFolder(projectName, sourceFolder + "/" + subfolder);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.removeUserRoleAssignment(SUBMITTER_2, "Folder Administrator", projectName + "/" + sourceFolder + "/" + subfolder);
        permissionsHelper.assertNoPermission(SUBMITTER_2, "Reader");

        goToProjectFolder(projectName, sourceFolder);
        impersonate(SUBMITTER_2);
        updateSubmitterAccountInfo("Two"); // Add first and last name
        goToDashboard();
        expWebPart.clickResubmit();
        assertTextPresent("This experiment is configured to include subfolders but you do not have read permissions in the following folders");
        assertTextPresent(subfolder);
        assertTextPresent("Read permissions are required in all subfolders to include data from subfolders");
        assertElementPresent(Locator.linkWithText("Back To Folder"));
        assertElementPresent(Locator.linkWithText("Exclude Subfolders"));
    }

    protected void setupSourceFolder(String projectName, String folderName, String ... adminUsers)
    {
        setupSubfolder(projectName, folderName, FolderType.Experiment); // Create the subfolder

        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        for(String user: adminUsers)
        {
            _userHelper.deleteUser(user);
            _userHelper.createUser(user);
            permissionsHelper.addMemberToRole(user, "Folder Administrator", PermissionsHelper.MemberType.user, projectName + "/" + folderName);
        }
    }

    private void testSubmitWithIncompleteAccountInfo(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("First and last names missing for data submitter: " + SUBMITTER);
    }

    private void testSubmitWithNoSkyDocs(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("There are no Skyline documents included in this experiment");
    }

    private void testSubmitWithMissingRawFiles(PortalHelper portal, TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("Missing raw data");
        assertTextPresent(RAW_FILE_WIFF);
        assertTextPresent(RAW_FILE_WIFF_SCAN);

        portal.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("Missing raw data");
        assertTextPresent(RAW_FILE_WIFF_SCAN);
        assertEquals(1, countText(RAW_FILE_WIFF));

        submitWithoutPXId();

        goToDashboard();
        assertTextPresent("Copy Pending!");
    }

    private void testSubmitWithSubfolders(TargetedMsExperimentWebPart expWebPart)
    {
        goToDashboard();
        expWebPart.clickSubmit();
        assertTextPresent("Confirm Include Subfolders");
        assertTextPresent("Would you like to include data from the following subfolders in the experiment?");
        assertElementPresent(Locator.linkWithText(INCLUDE_SUBFOLDERS_AND_SUBMIT));
        assertElementPresent(Locator.linkWithText(EXCLUDE_SUBFOLDERS_AND_SUBMIT));

        clickAndWait(Locator.linkWithText(INCLUDE_SUBFOLDERS_AND_SUBMIT));

        submitWithoutPXId();

        goToDashboard();
        assertTextPresent("Copy Pending!");
    }

    public void resubmitWithoutPxd()
    {
        clickContinueWithoutPxId();
        waitForText("Resubmit Request to ");
        click(Ext4Helper.Locators.ext4Button(("Resubmit")));
        waitForText("Confirm resubmission request to");
        click(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        waitForText("Request resubmitted to");
        click(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false,ADMIN_USER, SUBMITTER, SUBMITTER_2,
                // Delete the two reviewer accounts created when experiments are copied to the Panorama Public project
                REVIEWER_PREFIX + "@proteinms.net",
                REVIEWER_PREFIX + "1@proteinms.net");

        super.doCleanup(afterTest);
    }
}
