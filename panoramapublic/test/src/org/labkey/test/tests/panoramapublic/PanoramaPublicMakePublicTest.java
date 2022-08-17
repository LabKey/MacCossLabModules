package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PanoramaPublicMakePublicTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    private static final String ADMIN_2 = "admin_2@panoramapublic.test";

    @Test
    public void testExperimentCopy()
    {
        // Set up our source folder. We will create an experiment and submit it to our "Panorama Public" project.
        String projectName = getProjectName();
        String folderName = "Folder 1";
        String targetFolder = "Test Copy 1";
        String experimentTitle = "This is an experiment to test making data public";
        setupSourceFolder(projectName, folderName, SUBMITTER);

        createFolderAdmin(projectName, folderName, ADMIN_2);

        impersonate(SUBMITTER);
        updateSubmitterAccountInfo("One");

        // Import a Skyline document to the folder
        importData(SKY_FILE_1, 1);

        // Add the "Targeted MS Experiment" webpart and submit
        TargetedMsExperimentWebPart expWebPart = createExperimentCompleteMetadata(experimentTitle);
        expWebPart.clickSubmit();
        submitWithoutPXId();
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Copy the experiment to the Panorama Public project
        makeCopy(projectName, folderName, experimentTitle, targetFolder, false, false);

        // Verify that the submitter can make the data public
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, SUBMITTER, true);
        // Verify that a folder admin in the source folder, who is not the submitter or lab head will not see the
        // "Make Public" button in the Panorama Public copy.
        verifyMakePublic(PANORAMA_PUBLIC, targetFolder, ADMIN_2, false);

        // Resubmit the folder.  This is still possible since the Panorama Public copy is not yet associated with a publication.
        goToProjectFolder(projectName, folderName);
        impersonate(SUBMITTER);
        goToDashboard();
        expWebPart.clickResubmit();
        resubmitWithoutPxd();
        goToDashboard();
        assertTextPresent("Copy Pending!");

        // Re-copy the experiment to the Panorama Public project. Do not delete the previous copy
        makeCopy(projectName, folderName, experimentTitle, targetFolder, true, false);

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
        expWebPart = new TargetedMsExperimentWebPart(this);
        assertFalse("Data has been made public, and a publication link has been added. Resubmit button should not be displayed.", expWebPart.hasResubmitLink());
    }

    private void resubmitWithoutPxd()
    {
        clickAndWait(Locator.linkContainingText("Submit without a ProteomeXchange ID"));
        waitForText("Resubmit Request to ");
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
        clickButton("Back to Folder");
    }

    private void createFolderAdmin(String projectName, String folderName, String user)
    {
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        _userHelper.deleteUser(user);
        _userHelper.createUser(user);
        permissionsHelper.addMemberToRole(user, "Folder Administrator", PermissionsHelper.MemberType.user, projectName + "/" + folderName);
    }
}
