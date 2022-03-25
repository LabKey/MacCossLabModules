package org.labkey.test.tests.panoramapublic;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentInsertPage;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PanoramaPublicTest extends PanoramaPublicBaseTest
{
    public static final String SAMPLEDATA_FOLDER = "panoramapublic/";

    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String RAW_FILE_WIFF = "Site52_041009_Study9S_Phase-I.wiff";
    private static final String RAW_FILE_WIFF_SCAN = RAW_FILE_WIFF + ".scan";

    private static final String SUBMITTER_2 = "submitter_2@panoramapublic.test";

    private static final String INCLUDE_SUBFOLDERS_AND_SUBMIT = "Include Subfolders And Continue";
    private static final String EXCLUDE_SUBFOLDERS_AND_SUBMIT = "Skip Subfolders And Continue";

    @Override
    public String getSampleDataFolder()
    {
        return SAMPLEDATA_FOLDER;
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
        TargetedMsExperimentWebPart expWebPart = createExperiment(experimentTitle);

        // Should show error message since there are no Skyline documents in the folder.
        testSubmitWithNoSkyDocs(expWebPart);

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Should show an error message since the submitter's account info does not have a first and last name
        testSubmitWithIncompleteAccountInfo(expWebPart);
        updateSubmitterAccountInfo("One");

        experimentTitle = "This is a test experiment without subfolders";
        testSubmitWithMissingMetadata(expWebPart, experimentTitle);

        // Run validation job and submit without ProteomeXchange ID
        String shortAccessLink = testSubmitWithMissingRawFiles(portalHelper, expWebPart);

        // Verify rows in Submission table
        verifySubmissionsAndPublishedVersions(projectName, folderName, 1, 0, List.of(Boolean.FALSE), List.of("") , List.of(""), List.of(""));

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, folderName, experimentTitle, targetFolder);

        // Verify that a version is not displayed in the Panorama Public copy since there is only one version.
        verifyExperimentVersion(PANORAMA_PUBLIC, targetFolder, null);

        // Verify rows in Submission table
        verifySubmissionsAndPublishedVersions(projectName, folderName, 1, 1, List.of(Boolean.TRUE), List.of(experimentTitle), List.of("1"), List.of(shortAccessLink));

        // Test re-submit experiment
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        goToDashboard();
        expWebPart.clickResubmit();
        resubmitWithoutPxd();
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Verify rows in Submission table
        verifySubmissionsAndPublishedVersions(projectName, folderName, 2, 1, List.of(Boolean.TRUE, Boolean.FALSE), List.of(experimentTitle, ""), List.of("1", ""), List.of(shortAccessLink, ""));

        copyExperimentAndVerify(projectName, folderName, null, experimentTitle,
                2, // We are not deleting the first copy so this is version 2
                true,
                false, // Do not delete old copy
                targetFolder);

        // Verify rows in Submission table. The short URL of the previous copy should have a '_v1' suffix.
        String v1Link = shortAccessLink.replace(".url", "_v1.url");
        verifySubmissionsAndPublishedVersions(projectName, folderName, 2, 2, List.of(Boolean.TRUE, Boolean.TRUE), List.of(experimentTitle, experimentTitle), List.of("1", "2"), List.of(v1Link, shortAccessLink));

        // The folder containing the older copy should have a "V.1" suffix added to the name.
        String v1Folder = targetFolder + " V.1";
        assertTrue("Expected the container for the previous copy to have been renamed with a suffix 'V.1'",
                _containerHelper.doesContainerExist(PANORAMA_PUBLIC + "/" + v1Folder));

        // Verify versions
        verifyVersionCount(experimentTitle, 2);
        verifyExperimentVersion(PANORAMA_PUBLIC, targetFolder, "Current");
        verifyExperimentVersion(PANORAMA_PUBLIC, v1Folder, "1");

        // Panorama Public admin should be able to delete the old folder
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteFolder(PANORAMA_PUBLIC, v1Folder);
        assertFalse("Expected the container for the previous copy to have been deleted",
                _containerHelper.doesContainerExist(PANORAMA_PUBLIC + "/" + v1Folder));

        // There should still be two rows in the submission table
        verifySubmissionsAndPublishedVersions(projectName, folderName, 2, 2, List.of(Boolean.TRUE, Boolean.TRUE), List.of("", experimentTitle), List.of("", "2"), List.of("", shortAccessLink));

        // Submitter should be able to delete their folder after it has been copied to Panorama Public
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        apiContainerHelper.deleteFolder(projectName, folderName);
        assertFalse("Expected the submitter's container to have been deleted",
                _containerHelper.doesContainerExist(projectName + "/" + folderName));
    }

    private void verifyExperimentVersion(String projectName, String folderName, String version)
    {
        goToProjectFolder(projectName, folderName);
        goToDashboard();
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        assertEquals("Unexpected experiment data version", version, expWebPart.getDataVersion());
    }

    private void verifySubmissionsAndPublishedVersions(String projectName, String folderName, int submissionCount, int maxVersion,
                                                       List<Boolean> copied,
                                                       List<String> experimentTitles,
                                                       List<String> versions,
                                                       List<String> accessLinks)
    {
        if (isImpersonating())
        {
            stopImpersonating();
        }
        goToProjectFolder(projectName, folderName);
        goToDashboard();
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);

        expWebPart.clickMoreDetails();

        verifySubmissions(submissionCount, copied, experimentTitles, versions, accessLinks);
        verifyPublishedVersions(maxVersion, versions, accessLinks);
    }

    private void verifyPublishedVersions(int maxVersion, List<String> versions, List<String> accessLinks)
    {
        int copiedCount = (int) accessLinks.stream().filter(l -> !"".equals(l)).count();
        if (copiedCount > 0)
        {
            DataRegionTable publishedVersionsTable = new DataRegionTable("PublishedVersions", getDriver());
            assertEquals("Expected " + copiedCount + " rows in the Published Versions table", copiedCount, publishedVersionsTable.getDataRowCount());
            int row = 0;
            String[] columns = new String[]{"Version", "Link"};
            for (int i = 0; i < accessLinks.size(); i++)
            {
                /// 2, 2, List.of(Boolean.TRUE, Boolean.TRUE), List.of("", experimentTitle), List.of("", "2"), List.of("", shortAccessLink));
                if (!"".equals(accessLinks.get(i)))
                {
                    List<String> rowVals = publishedVersionsTable.getRowDataAsText(row, columns).stream().map(String::trim).collect(Collectors.toList());
                    String version = versions.get(i);
                    if (!StringUtils.isBlank(version) && Integer.parseInt(version) == maxVersion)
                    {
                        version = "Current";
                    }
                    assertEquals("Unexpected values in Published Versions table row " + row,
                            List.of(version, accessLinks.get(i)),
                            rowVals);
                    row++;
                }
            }
        }
    }

    private void verifySubmissions(int submissionCount, List<Boolean> copied, List<String> experimentTitles, List<String> versions, List<String> accessLinks)
    {
        DataRegionTable submissionTable = new DataRegionTable("Submission", getDriver());
        assertEquals("Expected " + submissionCount + " rows in the Submission table", submissionCount, submissionTable.getDataRowCount());

        String[] columns = new String[]{"ShortURL", "CopiedExperimentId/DataVersion", "CopiedExperimentId", "Edit", "Delete"};
        for (int row = 0; row < submissionCount; row++)
        {
            Boolean wasCopied = copied.get(row);
            String rowCopiedVal = submissionTable.getRowDataAsText(row, "Copied").get(0).trim();
            if (wasCopied)
            {
                assertNotEquals("Expected a value in the 'Copied' column", "", rowCopiedVal);
            }
            else
            {
                assertEquals("Expected 'Copied' column to be blank", "", rowCopiedVal);
            }
            List<String> rowVals = submissionTable.getRowDataAsText(row, columns).stream().map(String::trim).collect(Collectors.toList());

            assertEquals("Unexpected values in Submission table row " + row,
                    List.of(accessLinks.get(row),
                            versions.get(row),
                            experimentTitles.get(row),
                            wasCopied ? ((row == submissionCount - 1) ? "RESUBMIT" : "") : "EDIT",
                            wasCopied ? "" : "DELETE"),
                    rowVals);
        }
    }

    @Test
    public void testCopyExperimentWithSubfolder()
    {
        String projectName = getProjectName();
        String sourceFolder = "Folder 2";
        String subfolder = "Subfolder_for_test";
        String targetFolder = "Test Copy 2";
        String experimentTitle = "This is a test experiment with subfolders";

        setupSourceFolder(projectName, sourceFolder, SUBMITTER, SUBMITTER_2);
        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Add the "Targeted MS Experiment" webpart
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Create a subfolder
        _containerHelper.createSubfolder(projectName, sourceFolder, subfolder, "Collaboration", null, false);

        goToProjectFolder(projectName, sourceFolder);
        testSubmitWithSubfolders(expWebPart);

        // Copy the experiment to the Panorama Public project
        copyExperimentAndVerify(projectName, sourceFolder, subfolder, experimentTitle, targetFolder);

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

    @Override
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

    private void testSubmitWithMissingMetadata(TargetedMsExperimentWebPart expWebPart, String newExperimentTitle)
    {
        goToDashboard();
        expWebPart.clickSubmit();

        var expectedTexts = new String[] {
                "The following information is required", "for submitting data to Panorama Public.",
                "Title should be at least 30 characters.",
                "Organism is required.",
                "Instrument is required.",
                "Keywords are required",
                "Submitter affiliation is required."
        };
        assertTextPresent(expectedTexts);
        clickAndWait(Locator.lkButton("Update Experiment Metadata"));
        var experimentUpdatePage = new TargetedMsExperimentInsertPage(getDriver(), true);
        experimentUpdatePage.setRequired(newExperimentTitle);

        goToDashboard();
        expWebPart.clickSubmit();
        // We should not see any missing metadata warnings anymore
        assertTextPresent("Click the button to start a new data validation job");
    }

    private String testSubmitWithMissingRawFiles(PortalHelper portal, TargetedMsExperimentWebPart expWebPart)
    {
        var validationPage = submitValidationJob();
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(), List.of(RAW_FILE_WIFF, RAW_FILE_WIFF_SCAN));

        portal.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));
        _fileBrowserHelper.fileIsPresent(RAW_FILE_WIFF);

        validationPage = submitValidationJob();
        validationPage.verifySampleFileStatus(SKY_FILE_1, List.of(RAW_FILE_WIFF), List.of(RAW_FILE_WIFF_SCAN));

        submitWithoutPxIdButton();

        goToDashboard();
        assertTextPresent("Copy Pending!");

        String accessLink = expWebPart.getAccessLink();
        assertNotNull("Expected a short access URL", accessLink);
        return accessLink;
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

    private void resubmitWithoutPxd()
    {
        clickButton("Submit without a ProteomeXchange ID");
        waitForText("Resubmit Request to ");
        click(Ext4Helper.Locators.ext4Button(("Resubmit")));
        waitForText("Confirm resubmission request to");
        click(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        waitForText("Request resubmitted to");
        click(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    private void verifyVersionCount(String experimentTitle, int count)
    {
        goToProjectHome(PANORAMA_PUBLIC);
        var expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        expListTable.ensureColumnPresent("VersionCount");
        expListTable.setFilter("Title", "Equals", experimentTitle);
        assertEquals(count, expListTable.getDataRowCount()); // One row per version of the data
        for (var row = 0; row < count; row++)
        {
            assertEquals("Unexpected VersionCount", String.valueOf(count), expListTable.getRowDataAsText(row, "VersionCount").get(0).trim());
        }
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
