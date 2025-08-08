package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.components.panoramapublic.TargetedMsExperimentWebPart;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class PrivateDataReminderTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";
    static final String SUBMITTER_2 = "submitter_2@panoramapublic.test";
    static final String SUBMITTER_3 = "submitter_3@panoramapublic.test";
    private static final String ADMIN_2 = "admin_2@panoramapublic.test";
    private static final String ADMIN_3 = "admin_3@panoramapublic.test";

    @Test
    public void testPrivateDataReminder()
    {
        String projectName = getProjectName();
        String folderName_1 = "Private Data 1";
        String targetFolder_1 = "Private Data 1 Copy";
        String experimentTitle_1 = "Test for private data message reminder - DATA ONE";
        log("Creating data 1 folder and copying to Panorama Public.");
        String shortAccessUrl_1 = setupFolderSubmitAndCopy(projectName, folderName_1, targetFolder_1, experimentTitle_1, SUBMITTER, "One", ADMIN_USER, SKY_FILE_1);

        String folderName_2 = "Private Data 2";
        String targetFolder_2 = "Private Data 2 Copy";
        String experimentTitle_2 = "Test for private data message reminder - DATA TWO";
        log("Creating data 2 folder and copying to Panorama Public.");
        String shortAccessUrl_2 = setupFolderSubmitAndCopy(projectName, folderName_2, targetFolder_2, experimentTitle_2, SUBMITTER_2, "Two", ADMIN_2, SKY_FILE_1);

        String folderName_3 = "Private Data 3";
        String targetFolder_3 = "Private Data 3 Copy";
        String experimentTitle_3 = "Test for private data message reminder - DATA THREE";
        log("Creating data 3 folder and copying to Panorama Public.");
        String shortAccessUrl_3 = setupFolderSubmitAndCopy(projectName, folderName_3, targetFolder_3, experimentTitle_3, SUBMITTER_3, "Three", ADMIN_3, SKY_FILE_1);

        log("Making data 3 public.");
        makePublic(projectName, folderName_3, SUBMITTER_3);

        log("Verifying data 1 is private");
        verifyIsPublicColumn(PANORAMA_PUBLIC, experimentTitle_1, false);
        log("Verifying data 2 is private");
        verifyIsPublicColumn(PANORAMA_PUBLIC, experimentTitle_2, false);
        log("Verifying data 3 is public");
        verifyIsPublicColumn(PANORAMA_PUBLIC, experimentTitle_3, true);

        log("Sending reminders");
        sendReminders();

        portalHelper.enterAdminMode();


        goToProjectFolder(projectName, folderName_1);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment ", true, "Support Messages");
        assertTextPresent("Title: Action Required: Status Update for Your Private Dataset on Panorama Public");

        goToProjectFolder(projectName, folderName_2);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment ", true, "Support Messages");
        assertTextPresent("Title: Action Required: Status Update for Your Private Dataset on Panorama Public");

        goToProjectFolder(projectName, folderName_3);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment ", true, "Support Messages");
        assertTextNotPresent("Title: Action Required: Status Update for Your Private Dataset on Panorama Public");
    }

    private void makePublic(String projectName, String folderName, String user)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderName);
        impersonate(user);
        goToDashboard();
        makeDataPublic(true);
        stopImpersonating();
    }

    protected void sendReminders()
    {
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));
        // checkCheckbox(Locator.input("testMode"));
        setFormElement(Locator.input("extensionLength"), "2");
        setFormElement(Locator.input("reminderFrequency"), "0");
        clickButton("Save");
        waitForText("Private data message settings saved");
        clickAndWait(Locator.linkWithText("Back to Panorama Public Admin Console"));

        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));
        doAndWaitForPageToLoad(() ->
        {
            clickButton("Send Reminders Now");
            assertAlertContains("Are you sure you want to send reminder messages for private datasets?");
            dismissAllAlerts();
        });

        waitForPipelineJobsToComplete(1, "Post private data reminder messages", false);
    }
}
