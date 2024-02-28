package org.labkey.test.tests.panoramapublic;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.SimpleGetCommand;
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
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PanoramaPublicBaseTest extends TargetedMSTest implements PostgresOnlyTest
{
    static String PANORAMA_PUBLIC = "Panorama Public " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    static final String PANORAMA_PUBLIC_GROUP = "panoramapublictest";
    static final String PANORAMA_PUBLIC_SUBMITTERS = "Panorama Public Submitters";
    static final String REVIEWERS = "Reviewers";

    static final String ADMIN_USER = "admin@panoramapublic.test";
    static final String SUBMITTER = "submitter@panoramapublic.test";

    static final String REVIEWER_PREFIX = "panoramapublictest_reviewer";

    static final String SKY_FILE_1 = "Study9S_Site52_v1.sky.zip";
    static final String SKY_FOLDER_NAME = "Study9S_Site52_v1";
    static final String RAW_FILE_WIFF = "Site52_041009_Study9S_Phase-I.wiff";
    static final String RAW_FILE_WIFF_RENAMED = RAW_FILE_WIFF + ".RENAMED";
    static final String RAW_FILE_WIFF_SCAN = RAW_FILE_WIFF + ".scan";
    static final String QC_1 = "QC_1";
    static final String QC_1_SKY = QC_1 + ".sky";
    static final String QC_1_SKY_ZIP = QC_1_SKY + ".zip";
    static final String QC_1_SKY_VIEW = QC_1_SKY + ".view";
    static final String QC_1_SKYD = QC_1_SKY + "d";
    static final String SMALL_MOL_FILES = "SmallMoleculeFiles";
    static final String SMALLMOL_PLUS_PEPTIDES = "smallmol_plus_peptides";
    static final String SMALLMOL_PLUS_PEPTIDES_SKY = SMALLMOL_PLUS_PEPTIDES + ".sky";
    static final String SMALLMOL_PLUS_PEPTIDES_SKYD = SMALLMOL_PLUS_PEPTIDES_SKY + "d";
    static final String SMALLMOL_PLUS_PEPTIDES_SKY_VIEW = SMALLMOL_PLUS_PEPTIDES_SKY + ".view";
    static final String SMALLMOL_PLUS_PEPTIDES_SKY_ZIP = SMALLMOL_PLUS_PEPTIDES_SKY + ".zip";

    static final String CATALOG_IMAGE_FILE = "skyline_panorama_workflow.png";
    static final String CATALOG_IMAGE_PATH = "TargetedMS/panoramapublic/" + CATALOG_IMAGE_FILE;

    static final String SAMPLEDATA_FOLDER = "panoramapublic/";

    private static final Pattern REVIEWER_PASSWORD_LINE = Pattern.compile("Password:\s(\\S+)");
    private static final String PASSWORD_SPECIAL_CHARS = "!@#$%^&*+=?";
    private static final int REVIEWER_PASSWORD_LEN = 14;

    PortalHelper portalHelper = new PortalHelper(this);

    @Override
    protected String getProjectName()
    {
        String baseName = getClass().getSimpleName();
        // The SQLite driver for the Chromatogram library code chokes on paths with certain characters on OSX. We don't have
        // any real deployments on OSX, so just avoid using those characters on dev machines and rely on TeamCity
        // to keep things happy on the platforms we actually use on production
        if (SystemUtils.IS_OS_MAC)
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

    @After
    public void afterTest() throws IOException, CommandException
    {
        if (isImpersonating())
        {
            stopImpersonating();
        }
        verifySymlinks();
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

        // Add a "Panorama Public Submitter" and a "Reviewers" permissions group
        _permissionsHelper.createProjectGroup(REVIEWERS, PANORAMA_PUBLIC);
        _permissionsHelper.createProjectGroup(PANORAMA_PUBLIC_SUBMITTERS, PANORAMA_PUBLIC);
    }

    boolean verifySymlinks() throws IOException, CommandException
    {
        Connection connection = createDefaultConnection();
        SimpleGetCommand command = new SimpleGetCommand("PanoramaPublic", "verifySymlinks");
        CommandResponse verifyResponse = command.execute(connection, "/");

        // Failure will throw exception and put results in log
        return verifyResponse.getProperty("success") != null;
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
        _userHelper.ensureUsersExist(List.of(adminUsers));
        for(String user: adminUsers)
        {
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
        assertTextPresent("Click the button to start data validation",
                "Validate Data for ProteomeXchange",
                "Submit without a ProteomeXchange ID");
        clickButton("Validate Data for ProteomeXchange");
        return new DataValidationPage(this);
    }

    String submitWithoutPXId()
    {
        clickAndWait(Locator.linkContainingText("Submit without a ProteomeXchange ID"));
        return submitFormAndGetAccessLink();
    }

    String submitWithoutPxIdButton()
    {
        clickButton("Submit without a ProteomeXchange ID");
        return submitFormAndGetAccessLink();
    }

    String submitIncompletePxButton()
    {
        clickButton("Continue with an Incomplete PX Submission");
        return submitFormAndGetAccessLink();
    }

    void resubmitWithoutPxd(boolean fromDataValidationPage, boolean keepPrivate)
    {
        if (fromDataValidationPage)
        {
            clickButton("Submit without a ProteomeXchange ID");
        }
        else
        {
            clickAndWait(Locator.linkContainingText("Submit without a ProteomeXchange ID"));
        }
        waitForText("Resubmit Request to ");
        if (!keepPrivate)
        {
            uncheck("Keep Private:");
        }
        click(Ext4Helper.Locators.ext4Button(("Resubmit")));
        waitForText("Confirm resubmission request to");
        click(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        waitAndClickAndWait(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    private String submitFormAndGetAccessLink()
    {
        submitForm();
        goToDashboard();
        assertTextPresent("Copy Pending!");
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        String accessLink = expWebPart.getAccessLink();
        assertNotNull("Expected a short access URL", accessLink);
        return accessLink;
    }

    private void submitForm()
    {
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("journalId"), PanoramaPublicTest.PANORAMA_PUBLIC);
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
        clickAndWait(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        clickAndWait(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    void copyExperimentAndVerify(String projectName, String folderName, String experimentTitle, String destinationFolder, String shortAccessUrl)
    {
        copyExperimentAndVerify(projectName, folderName, null, experimentTitle, null, false, true, destinationFolder, shortAccessUrl);
    }

    void copyExperimentAndVerify(String projectName, String folderName, @Nullable List<String> subfolders, String experimentTitle, String destinationFolder, String shortAccessUrl)
    {
        copyExperimentAndVerify(projectName, folderName, subfolders, experimentTitle, null, false, true, destinationFolder, shortAccessUrl);
    }

    void makeCopy(String shortAccessUrl, String experimentTitle, String destinationFolder, boolean recopy, boolean deleteOldCopy)
    {
        if(isImpersonating())
        {
            stopImpersonating();
        }
        makeCopy(shortAccessUrl, experimentTitle, recopy, deleteOldCopy, destinationFolder, true);
    }

    void copyExperimentAndVerify(String projectName, String folderName, @Nullable List<String> subfolders, String experimentTitle,
                                 @Nullable Integer version, boolean recopy, boolean deleteOldCopy, String destinationFolder,
                                 String shortAccessUrl)
    {
        copyExperimentAndVerify(projectName, folderName, subfolders, experimentTitle, version, recopy, deleteOldCopy,
                destinationFolder, shortAccessUrl, true);
    }

    void copyExperimentAndVerify(String projectName, String folderName, @Nullable List<String> subfolders, String experimentTitle,
                                 @Nullable Integer version, boolean recopy, boolean deleteOldCopy, String destinationFolder,
                                 String shortAccessUrl, boolean symlinks)
    {
        if(isImpersonating())
        {
            stopImpersonating();
        }
        makeCopy(shortAccessUrl, experimentTitle, recopy, deleteOldCopy, destinationFolder, symlinks);
        verifyCopy(shortAccessUrl, experimentTitle, version, projectName, folderName, subfolders, recopy);
    }

    private void makeCopy(String shortAccessUrl, String experimentTitle, boolean recopy, boolean deleteOldCopy, String destinationFolder, boolean symlinks)
    {
        goToProjectHome(PANORAMA_PUBLIC);
        impersonateGroup(PANORAMA_PUBLIC_GROUP, false);

        clickAndWait(Locator.linkContainingText(shortAccessUrl));
        Locator.XPathLocator copyLink = Locator.linkContainingText("Copy Link");
        assertNotNull(copyLink);
        clickAndWait(copyLink);

        // In the copy form's folder tree view select the Panorama Public project as the destination.
        Locator.tagWithClass("span", "x4-tree-node-text").withText(PANORAMA_PUBLIC).waitForElement(new WebDriverWait(getDriver(), Duration.ofSeconds(5))).click();
        // Enter the name of the destination folder in the Panorama Public project
        setFormElement(Locator.tagWithName("input", "destContainerName"), destinationFolder);
        uncheck("Assign ProteomeXchange ID:");
        uncheck("Assign Digital Object Identifier:");
        if (!symlinks)
        {
            uncheck("Move and Symlink Files:");
        }

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
        stopImpersonating();
    }

    protected final void uncheck(String label)
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

    private void verifyCopy(String shortAccessUrl, String experimentTitle, @Nullable Integer version, String projectName, String folderName, List<String> subfolders, boolean recopy)
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
        if(CollectionUtils.isNotEmpty(subfolders))
        {
            SubfoldersWebPart subfoldersWp = SubfoldersWebPart.getWebPart(getDriver());
            assertNotNull(subfoldersWp);
            List<String> subfolderNames = subfoldersWp.GetSubfolderNames();
            assertEquals("Unexpected subfolder count", subfolders.size(), subfolderNames.size());
            List<String> namesUpperCase = new ArrayList<>(subfolders);
            namesUpperCase.replaceAll(String::toUpperCase);
            Collections.sort(namesUpperCase);
            Collections.sort(subfolderNames);
            assertEquals("Unexpected subfolder names", namesUpperCase, subfolderNames);
        }

        // Verify that notifications got posted on message board
        goToProjectHome(PANORAMA_PUBLIC);
        clickAndWait(Locator.linkContainingText(shortAccessUrl));
        assertTextPresent((recopy ? "Recopied": "Copied") + " - " + shortAccessUrl);
        var text = "As requested, your data on " + PANORAMA_PUBLIC + " is private.";
        text += recopy ? " The reviewer account details remain unchanged." : " Here are the reviewer account details:";
        if (!recopy)
        {
            text += "\nEmail: " + REVIEWER_PREFIX;
        }
        String messageText = new BodyWebPart(getDriver(), "View Message").getComponentElement().getText();
        var srcFolderTxt = "Source folder: " + "/" + projectName + "/" + folderName;
        assertTextPresent(new TextSearcher(messageText), text, srcFolderTxt);

        // Unescaped special Markdown characters in the message may cause the password to render incorrectly.
        // Extract the reviewer password and check that it has the correct length and expected characters.
        Matcher match = REVIEWER_PASSWORD_LINE.matcher(messageText);
        assertTrue("Could not find reviewer password in the message", match.find());
        String password = match.group(1);
        assertEquals("Unexpected length of reviewer password", REVIEWER_PASSWORD_LEN, password.length());
        for (int i = 0; i < password.length(); i++)
        {
            char c = password.charAt(i);
            assertTrue("Unexpected character '"+ c + "' in reviewer password " + password,
                    Character.isUpperCase(c) || Character.isLowerCase(c)
                            || Character.isDigit(c) || PASSWORD_SPECIAL_CHARS.contains(String.valueOf(c)));
        }
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

    protected String setupFolderSubmitAndCopy(String projectName, String folderName, String targetFolder, String experimentTitle, String submitter, @Nullable String submitterLastName,
                                              @Nullable String admin, String skylineDocName)
    {
        setupSourceFolder(projectName, folderName, submitter);

        if (admin != null)
        {
            createFolderAdmin(projectName, folderName, admin);
        }

        impersonate(submitter);
        if (submitterLastName != null)
        {
            updateSubmitterAccountInfo(submitterLastName);
        }

        // Import a Skyline document to the folder
        importData(skylineDocName, 1);

        // Add the "Targeted MS Experiment" webpart and submit
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessUrl = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        makeCopy(shortAccessUrl, experimentTitle, targetFolder, false, false);

        return shortAccessUrl;
    }

    private void createFolderAdmin(String projectName, String folderName, String user)
    {
        _userHelper.ensureUsersExist(Collections.singletonList(user));
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.addMemberToRole(user, "Folder Administrator", PermissionsHelper.MemberType.user, projectName + "/" + folderName);
    }

    protected void makeDataPublic(boolean unpublishedData)
    {
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);

        if (unpublishedData)
        {
            expWebPart.clickMakePublic();
            assertTextPresent("Publication Details");
            _ext4Helper.checkCheckbox(Ext4Helper.Locators.checkbox(this, "Unpublished:"));
        }
        else
        {
            expWebPart.clickAddPublication();
            assertTextPresent("Publication Details");
            setFormElement(Locator.input("link"), "http://panorama-publication-test.org");
            setFormElement(Locator.tagWithName("textarea", "citation"), "Paper citation goes here");
        }

        clickButton("Continue");
        assertTextPresent("Confirm Publication Details");
        clickButton("OK");
        if (unpublishedData)
        {
            assertTextPresent("Data on Panorama Public", "was made public.");
        }
        else
        {
            assertTextPresent("Publication details were updated for data on Panorama Public");
        }
        clickAndWait(Locator.linkContainingText("Back to Folder"));
    }

    protected void createCatalogEntry()
    {
        assertTextPresent("Use the form below to provide a brief description and an image that will be displayed in a slideshow ");
        setFormElement(Locator.textarea("datasetDescription"), "Cool research with Skyline");
        File imageFile = TestFileUtils.getSampleData(CATALOG_IMAGE_PATH);
        setFormElement(Locator.input("imageFileInput"), imageFile);
        assertTextPresent("Drag and resize the crop-box");
        clickButton("Crop", 0);
        // waitForElementToDisappear(Locator.IdLocator.id("cropperContainer"));
        clickButton("Submit");
        assertTextPresent("Thank you for submitting your entry for the Panorama Public data catalog");
    }

    protected void enableCatalogEntries()
    {
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Panorama Public Catalog Settings"));
        checkCheckbox(Locator.input("enabled"));
        setFormElement(Locator.input("maxFileSize"), "5242880");
        setFormElement(Locator.input("imgWidth"), "600");
        setFormElement(Locator.input("imgHeight"), "400");
        setFormElement(Locator.input("maxTextChars"), "500");
        setFormElement(Locator.input("maxEntries"), "25");
        clickButton("Save");
        waitForText("Panorama Public catalog entry settings were saved");
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
