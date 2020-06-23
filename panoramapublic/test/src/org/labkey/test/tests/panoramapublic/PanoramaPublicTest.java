package org.labkey.test.tests.panoramapublic;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicTest extends TargetedMSTest implements PostgresOnlyTest
{
    // private static final String SKY_FILE_1 = "MRMer.zip";
    // private static final String SKY_FILE_2 = "smallmol_plus_peptides.sky.zip";
    private static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    private static final String RAW_FILE_WIFF = "Site52_041009_Study9S_Phase-I.wiff";
    private static final String RAW_FILE_WIFF_SCAN = RAW_FILE_WIFF + ".scan";

    private static String PANORAMA_PUBLIC = "Panorama Public " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String PANORAMA_PUBLIC_GROUP = "panoramapublictest";
    private static final String DESTINATION_FOLDER = "Test Copy";

    private static final String ADMIN_USER = "admin@panoramapublic.test";
    private static final String SUBMITTER = "submitter@panoramapublic.test";

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
    }

    protected File getSampleDataPath(String file)
    {
        return TestFileUtils.getSampleData("TargetedMS/" + file);
    }

    @Test
    public void testExperimentCopy()
    {
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        assertEquals(0, expListTable.getDataRowCount()); // Table should be empty since we have not yet copied any experiments.

        // 1. Set up our source folder. We will create an experiment here and submit it to our "Panorama Public" project.
        setupFolder(FolderType.Experiment);
        impersonate(SUBMITTER);


        // 2. Import a Skyline document to the folder
        // importData(SKY_FILE_1);
        // importData(SKY_FILE_2, 2);

        // 3. Add the "Targeted MS Experiment" webpart
        portalHelper.click(Locator.folderTab("Panorama Dashboard"));
        portalHelper.enterAdminMode();
        portalHelper.addBodyWebPart("Targeted MS Experiment");

        // 3. Create a new experiment
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        TargetedMsExperimentInsertPage insertPage = expWebPart.startInsert();
        insertPage.insert();

        // Click Submit.  Should show error message since there are no Skyline documents in the folder.
        testSubmitWithNoSkyDocs(portalHelper, expWebPart);

        // 4. Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Click Submit.  Expect to see the missing information page
        testSubmitWithMissingRawFiles(portalHelper, expWebPart);

        testSubmitWithRawFiles(portalHelper, expWebPart);

        // 5. Submit the experiment
        portalHelper.click(Locator.folderTab("Panorama Dashboard"));
        expWebPart.submitWithoutPXId();
        assertTextPresent("Copy Pending!");

        // 6. Copy the experiment to the Panorama Public project
        // Customize the "Submission" grid to display the short copy URL
        stopImpersonating();
        goToProjectHome(PANORAMA_PUBLIC);
        impersonateGroup(PANORAMA_PUBLIC_GROUP, false);
        portalHelper.addWebPart("Messages");
        assertTextPresent("NEW: Experiment ID ");
        assertTextPresent("submitted to " + PANORAMA_PUBLIC);
        clickAndWait(Locator.linkWithText("view message or respond"));
        Locator.XPathLocator copyLink = Locator.linkContainingText("Copy Link");
        assertNotNull(copyLink);
        clickAndWait(copyLink);

        // In the copy form's folder tree view select the Panorama Public project as the destination.
        Locator.tagWithClass("span", "x4-tree-node-text").withText(PANORAMA_PUBLIC).waitForElement(new WebDriverWait(getDriver(), 5)).click();
        // Enter the name of the destination folder in the Panorama Public project
        setFormElement(Locator.tagWithName("input", "destContainerName"), DESTINATION_FOLDER);
        _ext4Helper.uncheckCheckbox(Ext4Helper.Locators.checkbox(this, "Send Email to Submitter:"));
        // Locator.extButton("Begin Copy"); // Locator.extButton() does not work.
        click(Ext4Helper.Locators.ext4Button("Begin Copy"));

        // Wait for the pipeline job to finish
        waitForText("Copying experiment");
        waitForPipelineJobsToComplete(1, "Copying experiment: " + TargetedMsExperimentInsertPage.EXP_TITLE, false);

        // Verify the copy
        goToProjectHome(PANORAMA_PUBLIC);
        expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        assertEquals(1, expListTable.getDataRowCount()); // The table should have a row for the copied experiment.
        expListTable.ensureColumnPresent("Title");
        Assert.assertTrue(expListTable.getDataAsText(0,"Title").contains(TargetedMsExperimentInsertPage.EXP_TITLE));
        expListTable.ensureColumnPresent("Runs");
        expListTable.ensureColumnPresent("Public"); // Column to indicate if the data is public or not
        expListTable.ensureColumnPresent("Data License");
        assertEquals("1", expListTable.getDataAsText(0, "Runs"));
        assertEquals("No", expListTable.getDataAsText(0, "Public"));
        assertEquals("CC BY 4.0", expListTable.getDataAsText(0, "Data License"));
        clickAndWait(expListTable.link(0, "Title"));
        assertTextPresentInThisOrder("Targeted MS Experiment", // Webpart title
                TargetedMsExperimentInsertPage.EXP_TITLE, // Title of the experiment
                "Data License", "CC BY 4.0" // This is the default data license
        );

        // Verify that notifications got posted on message board
        goToProjectHome(PANORAMA_PUBLIC);
        clickAndWait(Locator.linkWithText("view message or respond"));
        assertTextPresent("COPIED: Experiment ID ");
        assertTextPresent("Email was not sent to submitter");
    }

    protected void setupFolder(FolderType folderType)
    {
        super.setupFolder(folderType);
        _userHelper.createUser(SUBMITTER);
        ApiPermissionsHelper _permissionsHelper = new ApiPermissionsHelper(this);
        _permissionsHelper.addMemberToRole(SUBMITTER, "Project Administrator", PermissionsHelper.MemberType.user, getProjectName());
    }

    private void testSubmitWithNoSkyDocs(PortalHelper portal, TargetedMsExperimentWebPart expWebPart)
    {
        portal.click(Locator.folderTab("Panorama Dashboard"));
        expWebPart.clickSubmit();
        assertTextPresent("There are no Skyline documents included in this experiment");
    }

    private void testSubmitWithMissingRawFiles(PortalHelper portal, TargetedMsExperimentWebPart expWebPart)
    {
        portal.click(Locator.folderTab("Panorama Dashboard"));
        expWebPart.clickSubmit();
        assertTextPresent("Missing raw data");
        assertTextPresent(RAW_FILE_WIFF);
        assertTextPresent(RAW_FILE_WIFF_SCAN);
    }

    private void testSubmitWithRawFiles(PortalHelper portal, TargetedMsExperimentWebPart expWebPart)
    {
        portal.click(Locator.folderTab("Raw Data"));
        _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));

//        if (!_fileBrowserHelper.fileIsPresent(fileName))
//            _fileBrowserHelper.uploadFile(getSampleDataPath(RAW_FILE_WIFF));

        portal.click(Locator.folderTab("Panorama Dashboard"));
        expWebPart.clickSubmit();
        assertTextPresent("Missing raw data");
        // assertTextNotPresent(RAW_FILE_WIFF);
        assertTextPresent(RAW_FILE_WIFF_SCAN);
        assertEquals(1, countText(RAW_FILE_WIFF));
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // these tests use the UIContainerHelper for project creation, but we can use the APIContainerHelper for deletion
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(PANORAMA_PUBLIC, afterTest);

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
            super(test.getDriver(), DEFAULT_TITLE, 0);
            _test = test;
        }

        public DataRegionTable getDataRegion()
        {
            if (_dataRegionTable == null)
                _dataRegionTable = DataRegionTable.DataRegion(_test.getDriver()).find(getComponentElement());
            return _dataRegionTable;
        }

        public PanoramaPublicTest.TargetedMsExperimentInsertPage startInsert()
        {
            findElement(Locator.linkContainingText("Create New Experiment")).click();
            return new PanoramaPublicTest.TargetedMsExperimentInsertPage(_test.getDriver());
        }

        public void submitWithoutPXId()
        {
            findElement(Locator.linkContainingText("Submit")).click();
            waitAndClick(Locator.linkContainingText("Continue Without ProteomeXchange ID"));
            getWrapper()._ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("journalId"), PANORAMA_PUBLIC);
            waitAndClick(Locator.linkContainingText("Submit"));
            waitAndClick(Locator.lkButton("OK")); // Confirm to proceed with the submission.
            waitAndClick(Locator.linkWithSpan("Back to Experiment Details")); // Navigate to the experiment details page.
        }

        public void clickSubmit()
        {
            clickAndWait(Locator.linkContainingText("Submit"));
        }

        public void resubmit()
        {
            clickAndWait(Locator.linkContainingText("Resubmit"));
            waitAndClick(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        }
    }

    private class TargetedMsExperimentInsertPage extends InsertPage
    {
        private static final String DEFAULT_TITLE = "Targeted MS Experiment";
        public static final String EXP_TITLE = "This is a test experiment";

        public TargetedMsExperimentInsertPage(WebDriver driver)
        {
            super(driver, DEFAULT_TITLE);
        }

        @Override
        protected void waitForReady()
        {
            waitForElement(elements().expTitle);
        }

        public void insert()
        {
            Elements elements = elements();
            setFormElement(elements.expTitle, EXP_TITLE);
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
