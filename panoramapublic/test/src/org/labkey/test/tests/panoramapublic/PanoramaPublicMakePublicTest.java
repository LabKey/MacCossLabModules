package org.labkey.test.tests.panoramapublic;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.pages.admin.PermissionsPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMakePublicTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String ADMIN_2 = "admin_2@panoramapublic.test";

    private static final String IMAGE_FILE = "skyline_panorama_workflow.png";
    private static final String IMAGE_PATH = "TargetedMS/panoramapublic/" + IMAGE_FILE;

    @Test
    public void testExperimentCopy()
    {
        // Set up our source folder. We will create an experiment and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Folder 1";
        String targetFolder = "Test Copy 1";
        String experimentTitle = "This is an experiment to test making data public";
        String shortAccessUrl = setupFolderSubmitAndCopy(projectName, folderName, targetFolder, experimentTitle);

        verifyPermissions(projectName, folderName, PANORAMA_PUBLIC, targetFolder);

        // Verify that the submitter can make the data public
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, SUBMITTER, true);
        // Verify that a folder admin in the source folder, who is not the submitter or lab head will not see the
        // "Make Public" button in the Panorama Public copy.
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, ADMIN_2, false);

        // Resubmit the folder.  This is still possible since the Panorama Public copy is not yet associated with a publication.
        resubmitFolder(projectName, folderName, SUBMITTER, true);

        // Re-copy the experiment to the Panorama Public project. Do not delete the previous copy
        makeCopy(shortAccessUrl, experimentTitle, targetFolder, true, false);

        // Verify that the "Make Public button is not visible in the older copy of the data.
        String v1Folder = targetFolder + " V.1";
        verifyMakePublic(PANORAMA_PUBLIC, v1Folder, SUBMITTER, true);
        // Verify that the submitter can make data public, and add publication details
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, SUBMITTER, true, true);
        verifyMakePublic(PANORAMA_PUBLIC, v1Folder, ADMIN_2, false);

        // Data has been made public, and publication link and citation have been added.  User should not able to resubmit
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        goToDashboard();
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        assertFalse("Data has been made public, and a publication link has been added. Resubmit button should not be displayed.", expWebPart.hasResubmitLink());
    }

    @Test
    public void testAddCatalogEntry()
    {
        // Set up our source folder. We will create an experiment and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Folder Catalog Entry";
        String targetFolder = "Test Copy Catalog Entry";
        String experimentTitle = "This is an experiment to test adding a catalog entry";
        String shortAccessUrl = setupFolderSubmitAndCopy(projectName, folderName, targetFolder, experimentTitle);

        // Enable Panorama Public catalog entries in the admin console.
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Panorama Public Catalog Settings"));
        checkCheckbox(Locator.input("enabled"));
        setFormElement(Locator.input("maxFileSize"), "5242880");
        setFormElement(Locator.input("imgWidth"), "600");
        setFormElement(Locator.input("imgHeight"), "400");
        setFormElement(Locator.input("maxTextChars"), "500");
        setFormElement(Locator.input("maxEntries"), "25");
        clickButton("Save", "Panorama Public catalog entry settings were saved");

        // 1. Make the data public and add a catalog entry
        addCatalogEntry(PANORAMA_PUBLIC, targetFolder);
        // - Verify that the catalog entry cannot be viewed in the user's source folder
        verifyCatalogEntryWebpart(projectName, folderName, false);
        // Verify permissions - only PanoramaPublicSubmitterRole can view / edit / delete the catalog entry in the
        // Panorama Public folder
        verifyPermissionsForCatalogEntry(PANORAMA_PUBLIC, targetFolder);

        // 2. Data was made public but publication details were not entered. This data can be resubmitted.
        // Resubmit data and make a new copy.
        resubmitFolder(projectName, folderName, SUBMITTER, false);
        // Re-copy the experiment. Do not delete the previous copy
        makeCopy(shortAccessUrl, experimentTitle, targetFolder, true, false);
        // - Verify catalog entry was moved to the new copy
        verifyCatalogEntryWebpart(PANORAMA_PUBLIC, targetFolder, true);
        // - Verify no catalog entry in the older copy
        verifyCatalogEntryWebpart(PANORAMA_PUBLIC, targetFolder + " V.1", false);
    }

    private void verifyPermissionsForCatalogEntry(String projectName, String folderName)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        verifyCatalogEntryWebpart(projectName ,folderName, true);
        stopImpersonating();
        impersonateRole("Reader");
        verifyCatalogEntryWebpart(projectName ,folderName, false);
        stopImpersonating();
    }

    private void addCatalogEntry(String projectName, String folderName)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        makeDataPublicWithCatalogEntry();
        stopImpersonating();
    }

    private void makeDataPublicWithCatalogEntry()
    {
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);
        expWebPart.clickMakePublic();
        assertTextPresent("Publication Details");
        _ext4Helper.checkCheckbox(Ext4Helper.Locators.checkbox(this, "Unpublished:"));

        clickButton("Continue");
        assertTextPresent("Confirm Publication Details");
        clickButton("OK");

        assertTextPresent("Data on Panorama Public", "was made public.");

        clickButton("Add Catalog Entry");
        assertTextPresent("Use the form below to provide a brief description and an image that will be displayed in a slideshow ");
        setFormElement(Locator.textarea("datasetDescription"), "Cool research with Skyline");
        File imageFile = TestFileUtils.getSampleData(IMAGE_PATH);
        setFormElement(Locator.input("imageFileInput"), imageFile);
        assertTextPresent("Drag and resize the crop-box");
        clickButton("Crop", 0);
        // waitForElementToDisappear(Locator.IdLocator.id("cropperContainer"));
        clickButton("Submit");
        assertTextPresent("Thank you for submitting your entry for the Panorama Public data catalog");
        clickButton("View Entry");
        assertTextPresent("Pending approval");
        assertElementNotPresent("Approve button should be displayed only for site admins.", Locator.lkButton("Approve"));
        clickButton("Back to Folder");
    }

    private void verifyCatalogEntryWebpart(String projectName, String folderName, boolean expectWebpart)
    {
        goToProjectFolder(projectName, folderName);
        TargetedMsExperimentWebPart expWebpart = new TargetedMsExperimentWebPart(this);
        expWebpart.checkCatalogEntryLink(expectWebpart);
        expWebpart.clickMoreDetails();
        if (expectWebpart)
        {
            assertTextPresent("Panorama Public Catalog Entry");
            BodyWebPart catalogEntryWp = new BodyWebPart(getDriver(), "Panorama Public Catalog Entry");
            WebElement imgTag = Locator.XPathLocator.tag("img").withAttributeContaining("src", IMAGE_FILE).findElement(catalogEntryWp);
            int responseCode = WebTestHelper.getHttpResponse(imgTag.getAttribute("src")).getResponseCode();
            assertEquals("Catalog entry image is missing. Unexpected response code. " + responseCode, HttpStatus.SC_OK, responseCode);

        }
        else
        {
            assertTextNotPresent("Panorama Public Catalog Entry");
        }
    }

    private String setupFolderSubmitAndCopy(String projectName, String folderName, String targetFolder, String experimentTitle)
    {
        setupSourceFolder(projectName, folderName, SUBMITTER);

        createFolderAdmin(projectName, folderName, ADMIN_2);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Add the "Targeted MS Experiment" webpart and submit
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        String shortAccessUrl = submitWithoutPXId();

        // Copy the experiment to the Panorama Public project
        makeCopy(shortAccessUrl, experimentTitle, targetFolder, false, false);

        return shortAccessUrl;
    }

    private void resubmitFolder(String projectName, String folderName, String submitter, boolean keepPrivate)
    {
        TargetedMsExperimentWebPart expWebPart;
        goToProjectFolder(projectName, folderName);
        impersonate(submitter);
        goToDashboard();
        expWebPart = new TargetedMsExperimentWebPart(this);
        expWebPart.clickResubmit();
        resubmitWithoutPxd(keepPrivate);
        goToDashboard();
        assertTextPresent("Copy Pending!");
    }

    private void verifyPermissions(String userProject, String userFolder, String panoramaPublicProject, String panoramaPublicFolder)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        String role = "Panorama Public Submitter";
        goToProjectFolder(panoramaPublicProject, panoramaPublicFolder);
        PermissionsPage permsPage = navBar().goToPermissionsPage();
        assertTrue("Expected submitter " + SUBMITTER + " to be assigned the " + role + " role in the copied folder.", permsPage.isUserInRole(SUBMITTER, role));

        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.assertGroupExists(PANORAMA_PUBLIC_SUBMITTERS, PANORAMA_PUBLIC);
        permissionsHelper.assertGroupExists(REVIEWERS, PANORAMA_PUBLIC);
        permissionsHelper.assertUserInGroup(SUBMITTER, PANORAMA_PUBLIC_SUBMITTERS, PANORAMA_PUBLIC, PermissionsHelper.PrincipalType.USER);

        String reviewerEmail = getReviewerEmail(panoramaPublicProject, panoramaPublicFolder);
        permissionsHelper.assertUserInGroup(reviewerEmail, REVIEWERS, PANORAMA_PUBLIC, PermissionsHelper.PrincipalType.USER);

        goToProjectFolder(userProject, userFolder);
        permsPage = navBar().goToPermissionsPage();
        try
        {
            permsPage.isUserInRole(SUBMITTER, role);
            Assert.fail(role + " role should not be visible in a user project.");
        }
        catch (NoSuchElementException e)
        {
            // Exception thrown should be about not finding the "Panorama Public Submitter" role on the permissions page.
            assertTrue(e.getMessage().contains(role));
        }
    }

    private String getReviewerEmail(String panoramaPublicProject, String panoramaPublicFolder)
    {
        // Get the reviewer's email from the notification messages
        goToProjectFolder(panoramaPublicProject, panoramaPublicFolder);
        TargetedMsExperimentWebPart expWp = new TargetedMsExperimentWebPart(this);
        expWp.getWebParMenu().clickSubMenu(true, "Support Messages");
        String bodyText = getBodyText();
        Pattern pattern = Pattern.compile(REVIEWER_PREFIX + "\\d*@proteinms\\.net");
        Matcher matcher = pattern.matcher(bodyText);
        if (matcher.find())
        {
            return matcher.group();
        }
        Assert.fail("Could not get reviewer email from support messages.");
        return null;
    }

    private void resubmitWithoutPxd(boolean keepPrivate)
    {
        clickAndWait(Locator.linkContainingText("Submit without a ProteomeXchange ID"));
        waitForText("Resubmit Request to ");
        if (!keepPrivate)
        {
            uncheck("Keep Private:");
        }
        click(Ext4Helper.Locators.ext4Button(("Resubmit")));
        waitForText("Confirm resubmission request to");
        click(Locator.lkButton("OK")); // Confirm to proceed with the submission.
        waitForText("Request resubmitted to");
        click(Locator.linkWithText("Back to Experiment Details")); // Navigate to the experiment details page.
    }

    private void verifyMakePublic(String projectName, String folderName, String user, boolean isSubmitter)
    {
        verifyMakePublic(projectName, folderName, user, isSubmitter, false);
    }

    private void verifyMakePublic(String projectName, String folderName, String user, boolean isSubmitter, boolean addPublication)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderName);
        impersonate(user);
        goToDashboard();
        TargetedMsExperimentWebPart expWebPart = new TargetedMsExperimentWebPart(this);

        String version = expWebPart.getDataVersion();
        boolean isCurrent = version == null || "Current".equals(version);
        if (isCurrent && isSubmitter)
        {
            assertTrue("Data submitter should see a \"Make Public\" button in the current copy of the data on Panorama Public",
                    expWebPart.hasMakePublicButton());

            makeDataPublic();
            if (addPublication)
            {
                addPublication();
            }
        }
        else
        {
            assertFalse(String.format("Data copy is %scurrent. User is %sthe submitter. \"Make Public\" button should not be displayed.",
                    isCurrent ? "" : "not ", isSubmitter ? "" : "not "),
                    expWebPart.hasMakePublicButton());
        }
        stopImpersonating();
    }

    private void makeDataPublic()
    {
        makePublic(true);
    }

    private void addPublication()
    {
        makePublic(false);
    }

    private void makePublic(boolean unpublishedData)
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

    private void createFolderAdmin(String projectName, String folderName, String user)
    {
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        _userHelper.deleteUser(user);
        _userHelper.createUser(user);
        permissionsHelper.addMemberToRole(user, "Folder Administrator", PermissionsHelper.MemberType.user, projectName + "/" + folderName);
    }
}
