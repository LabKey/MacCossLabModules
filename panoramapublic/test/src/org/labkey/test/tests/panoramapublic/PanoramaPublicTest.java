package org.labkey.test.tests.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.SubfoldersWebPart;
import org.labkey.test.pages.InsertPage;
import org.labkey.test.tests.targetedms.TargetedMSTest;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicTest extends TargetedMSTest implements PostgresOnlyTest
{
    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String RAW_FILE_WIFF = "Site52_041009_Study9S_Phase-I.wiff";
    private static final String RAW_FILE_WIFF_SCAN = RAW_FILE_WIFF + ".scan";

    private static String PANORAMA_PUBLIC = "Panorama Public " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String PANORAMA_PUBLIC_GROUP = "panoramapublictest";

    private static final String ADMIN_USER = "admin@panoramapublic.test";
    private static final String SUBMITTER = "submitter@panoramapublic.test";
    private static final String SUBMITTER_2 = "submitter_2@panoramapublic.test";
    private static final String REVIEWER_PREFIX = "panoramapublictest_reviewer";

    private static final String INCLUDE_SUBFOLDERS_AND_SUBMIT = "Include Subfolders And Continue";
    private static final String EXCLUDE_SUBFOLDERS_AND_SUBMIT = "Skip Subfolders And Continue";

    PortalHelper portalHelper = new PortalHelper(this);

    @Override
    protected String getProjectName()
    {
        return getClass().getSimpleName() + " Project " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    @BeforeClass
    public static void initProject()
    {
        PanoramaPublicTest init = (PanoramaPublicTest)getCurrentTest();
        init.createPanoramaPublicJournalProject();

        // Create the test project
        init.setupFolder(FolderType.Experiment);
    }

    private void createPanoramaPublicJournalProject()
    {
        // Create a "Panorama Public" project where we will copy data.
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Create a new journal group"));
        setFormElement(Locator.id("journalNameTextField"), PANORAMA_PUBLIC);
        setFormElement(Locator.id("groupNameTextField"), PANORAMA_PUBLIC_GROUP);
        setFormElement(Locator.id("projectNameTextField"), PANORAMA_PUBLIC);
        clickButton("Submit", "Journal group details");

        // Add an admin user to the security group associated with the Panorama Public project
        _userHelper.createUser(ADMIN_USER);
        ApiPermissionsHelper _permissionsHelper = new ApiPermissionsHelper(this);
        _permissionsHelper.addUserToProjGroup(ADMIN_USER, PANORAMA_PUBLIC, PANORAMA_PUBLIC_GROUP);

        // Add the Messages webpart
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        assertEquals(0, expListTable.getDataRowCount()); // Table should be empty since we have not yet copied any experiments.
        portalHelper.addWebPart("Messages");
    }

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

    @NotNull
    private PanoramaPublicTest.TargetedMsExperimentWebPart createTargetedMsExperimentWebPart(String experimentTitle)
    {
        goToDashboard();
        portalHelper.enterAdminMode();
        portalHelper.addBodyWebPart("Targeted MS Experiment");

        // Create a new experiment
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        TargetedMsExperimentInsertPage insertPage = expWebPart.startInsert();
        insertPage.insert(experimentTitle);
        return expWebPart;
    }

    private void verifyCopy(String experimentTitle, String projectName, String folderName, String subfolderName, boolean recopy)
    {
        // Verify the copy
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        expListTable.ensureColumnPresent("Title");
        expListTable.setFilter("Title", "Equals", experimentTitle);
        // expListTable.setFilter("Share", "Is Not Blank");
        assertEquals(1, expListTable.getDataRowCount()); // The table should have one row for the copied experiment.
        expListTable.ensureColumnPresent("Runs");
        expListTable.ensureColumnPresent("Public"); // Column to indicate if the data is public or not
        expListTable.ensureColumnPresent("Data License");
        Assert.assertTrue(expListTable.getDataAsText(0,"Title").contains(experimentTitle));
        assertEquals("1", expListTable.getDataAsText(0, "Runs"));
        assertEquals("No", expListTable.getDataAsText(0, "Public"));
        assertEquals("CC BY 4.0", expListTable.getDataAsText(0, "Data License"));
        clickAndWait(expListTable.link(0, "Title"));
        assertTextPresentInThisOrder("Targeted MS Experiment", // Webpart title
                experimentTitle, // Title of the experiment
                "Data License", "CC BY 4.0" // This is the default data license
        );
        if(subfolderName != null)
        {
            SubfoldersWebPart subfoldersWp = SubfoldersWebPart.getWebPart(getDriver());
            assertNotNull(subfoldersWp);
            List<String> subfolderNames = subfoldersWp.GetSubfolderNames();
            assertEquals(1, subfolderNames.size());
            assertEquals(subfolderName.toUpperCase(), subfolderNames.get(0));
        }

        // Verify that notifications got posted on message board
        goToProjectHome(PANORAMA_PUBLIC);
        clickAndWait(Locator.linkContainingText("/" + projectName + "/" + folderName));
        assertTextPresent((recopy ? "RECOPIED": "COPIED") + ": Experiment ID ");
        assertTextPresent("Email was not sent to submitter");
    }

    private void copyExperimentAndVerify(String projectName, String folderName, String experimentTitle, boolean recopy, String destinationFolder)
    {
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, recopy, destinationFolder);
    }

    private void copyExperimentAndVerify(String projectName, String folderName, @Nullable String subfolderName, String experimentTitle, boolean recopy, String destinationFolder)
    {
        if(isImpersonating())
        {
            stopImpersonating();
        }
        goToProjectHome(PANORAMA_PUBLIC);
        impersonateGroup(PANORAMA_PUBLIC_GROUP, false);

        clickAndWait(Locator.linkContainingText("/" + projectName + "/" + folderName));
        Locator.XPathLocator copyLink = Locator.linkContainingText("Copy Link");
        assertNotNull(copyLink);
        clickAndWait(copyLink);

        // In the copy form's folder tree view select the Panorama Public project as the destination.
        Locator.tagWithClass("span", "x4-tree-node-text").withText(PANORAMA_PUBLIC).waitForElement(new WebDriverWait(getDriver(), 5)).click();
        // Enter the name of the destination folder in the Panorama Public project
        setFormElement(Locator.tagWithName("input", "destContainerName"), destinationFolder);
        _ext4Helper.uncheckCheckbox(Ext4Helper.Locators.checkbox(this, "Send Email to Submitter:"));
        _ext4Helper.uncheckCheckbox(Ext4Helper.Locators.checkbox(this, "Assign Digital Object Identifier:")); // Don't try to assign a DOI

        if(recopy)
        {
            assertTextPresent("This experiment was last copied on");
            Locator.XPathLocator deletePreviousCopyCb = Ext4Helper.Locators.checkbox(this, "Delete Previous Copy:");
            assertNotNull("Expected to see \"Delete Previous Copy\" checkbox", deletePreviousCopyCb);
            _ext4Helper.checkCheckbox(deletePreviousCopyCb);
        }
        else
        {
            setFormElement(Locator.tagWithName("input", "reviewerEmailPrefix"), REVIEWER_PREFIX);
        }

        // Locator.extButton("Begin Copy"); // Locator.extButton() does not work.
        click(Ext4Helper.Locators.ext4Button("Begin Copy"));

        // Wait for the pipeline job to finish
        waitForText("Copying experiment");
        waitForPipelineJobsToComplete(1, "Copying experiment: " + experimentTitle, false);

        verifyCopy(experimentTitle, projectName, folderName, subfolderName, recopy);

        stopImpersonating();
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

    private void updateSubmitterAccountInfo(String lastName)
    {
        goToMyAccount();
        clickButton("Edit");
        setFormElement(Locator.name("quf_FirstName"), "Submitter");
        setFormElement(Locator.name("quf_LastName"), lastName);
        clickButton("Submit");
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

    public void submitWithoutPXId()
    {
        clickContinueWithoutPxId();
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("journalId"), PanoramaPublicTest.PANORAMA_PUBLIC);
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
        clickAndWait(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        clickAndWait(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
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

    private void clickContinueWithoutPxId()
    {
        // Expect to be on the missing information page
        assertTextPresent("Missing Information in Submission Request");
        clickAndWait(Locator.linkContainingText("Continue without a ProteomeXchange ID"));
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // these tests use the UIContainerHelper for project creation, but we can use the APIContainerHelper for deletion
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(PANORAMA_PUBLIC, afterTest);

        _userHelper.deleteUsers(false,ADMIN_USER, SUBMITTER, SUBMITTER_2,
                // Delete the two reviewer accounts created when experiments are copied to the Panorama Public project
                REVIEWER_PREFIX + "@proteinms.net",
                REVIEWER_PREFIX + "1@proteinms.net");

        super.doCleanup(afterTest);
    }

    private class TargetedMsExperimentWebPart extends BodyWebPart
    {
        public static final String DEFAULT_TITLE = "Targeted MS Experiment";
        private DataRegionTable _dataRegionTable;
        private BaseWebDriverTest _test;

        public TargetedMsExperimentWebPart(BaseWebDriverTest test)
        {
            this(test, 0);
        }

        public TargetedMsExperimentWebPart(BaseWebDriverTest test, int index)
        {
            super(test.getDriver(), DEFAULT_TITLE, index);
            _test = test;
        }

        public DataRegionTable getDataRegion()
        {
            if (_dataRegionTable == null)
                _dataRegionTable = DataRegionTable.DataRegion(_test.getDriver()).find(getComponentElement());
            return _dataRegionTable;
        }

        public TargetedMsExperimentInsertPage startInsert()
        {
            findElement(Locator.linkContainingText("Create New Experiment")).click();
            return new TargetedMsExperimentInsertPage(_test.getDriver());
        }

        public void clickSubmit()
        {
            clickAndWait(Locator.linkContainingText("Submit"));
        }

        public void clickResubmit()
        {
            Locator.XPathLocator resubmitLink = Locator.linkContainingText("Resubmit");
            assertNotNull("Expected to see a \"Resubmit\" button", resubmitLink);
            clickAndWait(resubmitLink);
        }
    }

    private static class TargetedMsExperimentInsertPage extends InsertPage
    {
        private static final String DEFAULT_TITLE = "Targeted MS Experiment";

        public TargetedMsExperimentInsertPage(WebDriver driver)
        {
            super(driver, DEFAULT_TITLE);
        }

        @Override
        protected void waitForReady()
        {
            waitForElement(elements().expTitle);
        }

        public void insert(String experimentTitle)
        {
            Elements elements = elements();
            setFormElement(elements.expTitle, experimentTitle);
            setFormElement(elements.expAbstract, "This is a really short, 50 character long abstract");
            clickAndWait(elements.submit);
        }

        @Override
        protected Elements elements()
        {
            return new Elements();
        }

        private class Elements extends InsertPage.Elements
        {
            public Locator.XPathLocator expTitle = body.append(Locator.tagWithName("textarea", "title"));
            public Locator.XPathLocator expAbstract = body.append(Locator.tagWithName("textarea", "abstract"));
        }
    }
}
