package org.labkey.test.tests.panoramapublic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.SubfoldersWebPart;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentInsertPage;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.panoramapublic.DataValidationPage;
import org.labkey.test.tests.targetedms.TargetedMSTest;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.TextSearcher;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PanoramaPublicBaseTest extends TargetedMSTest implements PostgresOnlyTest
{
    static String PANORAMA_PUBLIC = "Panorama Public " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    static final String PANORAMA_PUBLIC_GROUP = "panoramapublictest";

    static final String ADMIN_USER = "admin@panoramapublic.test";
    static final String SUBMITTER = "submitter@panoramapublic.test";

    static final String REVIEWER_PREFIX = "panoramapublictest_reviewer";

    PortalHelper portalHelper = new PortalHelper(this);

    @Override
    protected String getProjectName()
    {
        String baseName = getClass().getSimpleName();
        // The SQLite driver for the Chromatogram library code chokes on paths with certain characters on OSX. We don't have
        // any real deployments on OSX, so just avoid using those characters on dev machines and rely on TeamCity
        // to keep things happy on the platforms we actually use on production
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMacOs = osName.startsWith("mac os x");
        if (isMacOs)
        {
            return baseName;
        }
        return baseName + " Project " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    @BeforeClass
    public static void initProject()
    {
        PanoramaPublicBaseTest init = (PanoramaPublicBaseTest)getCurrentTest();
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

    @NotNull
    TargetedMsExperimentWebPart createExperiment(String experimentTitle)
    {
        return createTargetedMsExperiment(experimentTitle, false);
    }

    @NotNull
    TargetedMsExperimentWebPart createExperimentCompleteMetadata(String experimentTitle)
    {
        return createTargetedMsExperiment(experimentTitle, true);
    }

    private TargetedMsExperimentWebPart createTargetedMsExperiment(String experimentTitle, boolean completeMetadata)
    {
        goToDashboard();
        portalHelper.enterAdminMode();
        portalHelper.addBodyWebPart("Targeted MS Experiment");

        // Create a new experiment
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        TargetedMsExperimentInsertPage insertPage = expWebPart.startInsert();
        if (completeMetadata)
        {
            insertPage.insertAllRequired(experimentTitle);
        }
        else
        {
            insertPage.insert(experimentTitle);
        }
        return expWebPart;
    }

    void setupSourceFolder(String projectName, String folderName, String ... adminUsers)
    {
        setupSourceFolder(projectName, folderName, FolderType.Experiment, adminUsers);
    }

    void setupLibraryFolder(String projectName, String folderName, FolderType folderType, String ... adminUsers)
    {
        setupSourceFolder(projectName, folderName, folderType, adminUsers);
    }

    private void setupSourceFolder(String projectName, String folderName, FolderType folderType, String ... adminUsers)
    {
        setupSubfolder(projectName, folderName, folderType); // Create the subfolder

        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        for(String user: adminUsers)
        {
            _userHelper.deleteUser(user);
            _userHelper.createUser(user);
            permissionsHelper.addMemberToRole(user, "Folder Administrator", PermissionsHelper.MemberType.user, projectName + "/" + folderName);
        }
    }

    void updateSubmitterAccountInfo(String lastName)
    {
        goToMyAccount();
        clickButton("Edit");
        setFormElement(Locator.name("quf_FirstName"), "Submitter");
        setFormElement(Locator.name("quf_LastName"), lastName);
        clickButton("Submit");
    }

    void goToExperimentDetailsPage()
    {
        goToDashboard();
        new TargetedMsExperimentWebPart(this).clickMoreDetails();
    }

    DataValidationPage submitValidationJob()
    {
        goToDashboard();
        var expWebPart = new TargetedMsExperimentWebPart(this);
        expWebPart.clickSubmit();
        assertTextPresent("Click the button to start a new data validation job",
                "Validate Data for ProteomeXchange",
                "Submit without a ProteomeXchange ID");
        clickButton("Validate Data for ProteomeXchange");
        return new DataValidationPage(this);
    }

    void submitWithoutPXId()
    {
        clickAndWait(Locator.linkContainingText("Submit without a ProteomeXchange ID"));
        submitForm();
    }

    void submitWithoutPxIdButton()
    {
        clickButton("Submit without a ProteomeXchange ID");
        submitForm();
    }

    void submitIncompletePxButton()
    {
        clickButton("Continue with an Incomplete PX Submission");
        submitForm();
    }

    private void submitForm()
    {
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("journalId"), PanoramaPublicTest.PANORAMA_PUBLIC);
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
        clickAndWait(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        clickAndWait(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    void copyExperimentAndVerify(String projectName, String folderName, String experimentTitle, String destinationFolder)
    {
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, null, false, true, destinationFolder);
    }

    void copyExperimentAndVerify(String projectName, String folderName, @Nullable String subfolderName, String experimentTitle, String destinationFolder)
    {
        copyExperimentAndVerify(projectName, folderName, subfolderName, experimentTitle, null, false, true, destinationFolder);
    }

    void copyExperimentAndVerify(String projectName, String folderName, @Nullable String subfolderName, String experimentTitle,
                                 @Nullable Integer version, boolean recopy, boolean deleteOldCopy, String destinationFolder)
    {
        if(isImpersonating())
        {
            stopImpersonating();
        }
        makeCopy(projectName, folderName, experimentTitle, recopy, deleteOldCopy, destinationFolder);
        verifyCopy(experimentTitle, version, projectName, folderName, subfolderName, recopy);

        stopImpersonating();
    }

    private void makeCopy(String projectName, String folderName, String experimentTitle, boolean recopy, boolean deleteOldCopy, String destinationFolder)
    {
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
        uncheck("Assign ProteomeXchange ID:");
        uncheck("Send Email to Submitter:");
        uncheck("Assign Digital Object Identifier:");

        if(recopy)
        {
            assertTextPresent("This experiment was last copied on");
            Locator.XPathLocator deletePreviousCopyCb = Ext4Helper.Locators.checkbox(this, "Delete Previous Copy:");
            assertNotNull("Expected to see \"Delete Previous Copy\" checkbox", deletePreviousCopyCb);
            if(deleteOldCopy)
            {
                _ext4Helper.checkCheckbox(deletePreviousCopyCb);
            }
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
    }

    private void uncheck(String label)
    {
        scrollIntoView(Ext4Helper.Locators.checkbox(this, label));
        int tries = 1;
        while(_ext4Helper.isChecked(Ext4Helper.Locators.checkbox(this, label)) && tries <= 5)
        {
            _ext4Helper.uncheckCheckbox(Ext4Helper.Locators.checkbox(this, label));
            tries++;
            sleep(250);
        }
        if(_ext4Helper.isChecked(Ext4Helper.Locators.checkbox(this, label)))
        {
            Assert.fail("Did not uncheck checkbox '" + label + "'");
        }
    }

    private void verifyCopy(String experimentTitle, @Nullable Integer version, String projectName, String folderName, String subfolderName, boolean recopy)
    {
        // Verify the copy
        goToProjectHome(PANORAMA_PUBLIC);
        DataRegionTable expListTable = DataRegionTable.findDataRegionWithinWebpart(this, "Targeted MS Experiment List");
        expListTable.ensureColumnPresent("Title");
        expListTable.setFilter("Title", "Equals", experimentTitle);
        if (version != null)
        {
            expListTable.ensureColumnPresent("DataVersion");
            expListTable.setFilter("DataVersion", "Equals", String.valueOf(version));
        }
        assertEquals(1, expListTable.getDataRowCount()); // The table should have one row for the copied experiment.
        expListTable.ensureColumnPresent("Runs");
        expListTable.ensureColumnPresent("Public"); // Column to indicate if the data is public or not
        expListTable.ensureColumnPresent("Data License");
        Assert.assertTrue(expListTable.getDataAsText(0,"Title").contains(experimentTitle));
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
        var text = "As requested, your data on " + PANORAMA_PUBLIC + " is private.";
        text += recopy ? " The reviewer account details remain unchanged." : " Here are the reviewer account details:";
        if (!recopy)
        {
            text += "\nEmail: " + REVIEWER_PREFIX;
        }
        String messageText = new BodyWebPart(getDriver(), "View Message").getComponentElement().getText();
        assertTextPresent(new TextSearcher(messageText), text);
    }

    @Override
    @LogMethod
    protected void importData(@LoggedParam String file, int jobCount)
    {
        importData(getSampleDataFolder() + file, jobCount, false);
    }

    public String getSampleDataFolder()
    {
        return "";
    }

    public File getSampleDataPath(String file)
    {
        return TestFileUtils.getSampleData("TargetedMS/" + getSampleDataFolder() + file);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // these tests use the UIContainerHelper for project creation, but we can use the APIContainerHelper for deletion
        APIContainerHelper apiContainerHelper = new APIContainerHelper(this);
        apiContainerHelper.deleteProject(PANORAMA_PUBLIC, afterTest);

        _userHelper.deleteUsers(false,ADMIN_USER, SUBMITTER);

        super.doCleanup(afterTest);
    }
}
