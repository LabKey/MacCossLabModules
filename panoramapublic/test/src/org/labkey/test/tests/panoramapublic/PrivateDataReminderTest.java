package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.pages.LabkeyErrorPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PrivateDataReminderTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE_1 = "MRMer.zip";

    static final String SUBMITTER_1 = "submitter_1@panoramapublic.test";
    static final String SUBMITTER_2 = "submitter_2@panoramapublic.test";
    static final String SUBMITTER_3 = "submitter_3@panoramapublic.test";
    private static final String ADMIN_1 = "admin_1@panoramapublic.test";
    private static final String ADMIN_2 = "admin_2@panoramapublic.test";
    private static final String ADMIN_3 = "admin_3@panoramapublic.test";

    private static final String REMINDER_MESSAGE_TITLE = "Title: Action Required: Status Update for Your Private Data on Panorama Public";
    private static final String EXTENSION_MESSAGE_TITLE = "Title: Private Status Extended - ";
    private static final String DELETION_MESSAGE_TITLE = "Title: Data Deletion Requested - ";
    private static final String MESSAGE_PAGE_TITLE = "Submitted - ";


    @Test
    public void testPrivateDataReminder()
    {
        String panoramaPublicProject = PANORAMA_PUBLIC;
        goToProjectHome(panoramaPublicProject);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.setSiteGroupPermissions("Guests", "Reader");


        String testProject = getProjectName();

        DataFolderInfo folderInfo_1 = createAndSubmitFolder(testProject,
                "Private Data 1",
                panoramaPublicProject,
                "Private Data 1 Copy",
                "Test for private data message reminder - DATA ONE",
                SUBMITTER_1, "One",
                ADMIN_1);

        DataFolderInfo folderInfo_2 = createAndSubmitFolder(testProject,
                "Private Data 2",
                panoramaPublicProject,
                "Private Data 2 Copy",
                "Test for private data message reminder - DATA TWO",
                SUBMITTER_2, "Two",
                ADMIN_2);

        DataFolderInfo folderInfo_3 = createAndSubmitFolder(testProject,
                "Private Data 3",
                panoramaPublicProject,
                "Private Data 3 Copy",
                "Test for private data message reminder - DATA THREE",
                SUBMITTER_3, "Three",
                ADMIN_3);

        log("Making data 3 public.");
        makePublic(panoramaPublicProject, folderInfo_3);
        folderInfo_3.setPublic(true);

        log("Verifying data 1 is private");
        verifyIsPublicColumn(panoramaPublicProject, folderInfo_1.getExperimentTitle(), false);
        log("Verifying data 2 is private");
        verifyIsPublicColumn(panoramaPublicProject, folderInfo_2.getExperimentTitle(), false);
        log("Verifying data 3 is public");
        verifyIsPublicColumn(panoramaPublicProject, folderInfo_3.getExperimentTitle(), true);

        List<DataFolderInfo> dataFolderInfos = List.of(folderInfo_1, folderInfo_2, folderInfo_3);
        testSendingReminders(panoramaPublicProject, dataFolderInfos);
    }

    private DataFolderInfo createAndSubmitFolder(String testProject, String sourceFolder,
                                                 String panoramaPublicProject, String targetFolder,
                                                 String experimentTitle,
                                                 String submitter, String submitterName, String admin)
    {
        log(String.format("Creating folder '%s' and copying to Panorama Public folder '%s'.", sourceFolder, targetFolder));
        String shortAccessUrl = setupFolderSubmitAndCopy(testProject, sourceFolder, targetFolder, experimentTitle,
                submitter, submitterName, admin, SKY_FILE_1);
        goToProjectFolder(panoramaPublicProject, targetFolder);
        goToExperimentDetailsPage();
        int exptAnnotationsId = Integer.parseInt(portalHelper.getUrlParam("id"));
        return new DataFolderInfo(sourceFolder, targetFolder, shortAccessUrl, experimentTitle, exptAnnotationsId, submitter);
    }

    private void gotoSupportMessage(DataFolderInfo folderInfo)
    {
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment", true, "Support Messages");
        waitForText("Submitted - " + folderInfo.getShortUrl());
    }

    private void makePublic(String projectName, DataFolderInfo folderInfo)
    {
        if (isImpersonating())
        {
            stopImpersonating(true);
        }
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        impersonate(folderInfo.getSubmitter());
        goToDashboard();
        makeDataPublic(true);
        stopImpersonating();
    }

    private void testSendingReminders(String projectName, List<DataFolderInfo> dataFolderInfos)
    {
        goToProjectHome(projectName);
        goToDataPipeline();
        int pipelineJobCount = getPipelineStatusValues().size();

        List<DataFolderInfo> privateData = dataFolderInfos.stream().filter(f -> !f.isPublic()).toList();
        int privateDataCount = privateData.size();
        assertEquals(2, privateDataCount);

        log("Changing reminder settings. Setting reminder frequency to 0.");
        saveSettings("2", "12", "0");

        // Do not select any experiments.  Job will not run.
        log("Attempt to send reminders without selecting any experiments. Job should not run.");
        postRemindersNoExperimentsSelected(projectName, privateDataCount);

        // Post reminders. None should get posted since the delayUntilFirstReminder is set to 12 months.
        log("Posting reminders. Select all experiment rows.");
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        // Verify that no reminders posted
        verifyNoReminderPosted(projectName, dataFolderInfos);
        String message = String.format("Skipping reminder for experiment Id %d - First reminder not due until ", privateData.get(0).getExperimentAnnotationsId());
        String message2 = String.format("Skipping reminder for experiment Id %d - First reminder not due until ", privateData.get(1).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, message2, "Skipped posting reminders for 2 experiments");

        log("Changing reminder settings. Setting delay until first reminder to 0.");
        saveSettings("2", "0", "0");

        // Post reminders in test mode.
        log("Posting reminders in test mode. Select all experiment rows.");
        postRemindersInTestMode(projectName, privateDataCount, -1, ++pipelineJobCount);
        // Verify that no reminders posted since test mode was checked
        verifyNoReminderPosted(projectName, dataFolderInfos);

        // Now really post the reminder. Select only the first experiment.
        postReminders(projectName, false, privateDataCount, 1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0), 1); // Reminder should be posted only to the selected experiment
        verifyNoReminderPosted(projectName, privateData.get(1)); // No reminder on the second experiment, since it was not selected
        verifyNoReminderPosted(projectName, dataFolderInfos.get(2)); // No reminder since this is public data

        // Change the reminder frequency to 1.
        log("Changing reminder settings. Setting reminder frequency to 1.");
        saveSettings("2", "0", "1");
        // Post reminders again. Since reminder frequency is set to 1, no reminders will be posted to the first data.
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0), 1); // No new reminders since reminder frequency is set to 1.
        verifyReminderPosted(projectName, privateData.get(1), 1);
        message = String.format("Skipping reminder for experiment Id %d - Recent reminder already sent", privateData.get(0).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, "Skipped posting reminders for 1 experiment");

        // Change reminder frequency to 0 again.
        log("Changing reminder settings. Setting reminder frequency to 0.");
        saveSettings("2", "0", "0");

        // Request extension for the first experiment.
        log("Requesting extension for experiment Id " + privateData.get(0).getExperimentAnnotationsId());
        requestExtension(projectName, privateData.get(0));
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0),1); // No new reminders since extension requested.
        verifyReminderPosted(projectName, privateData.get(1), 2); // Reminder posted since reminder frequency is 0.
        message = String.format("Skipping reminder for experiment Id %d - Submitter requested an extension. Extension is current",
                privateData.get(0).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, "Skipped posting reminders for 1 experiment");

        // Request deletion for the second experiment.
        log("Requesting deletion for experiment Id " + privateData.get(1).getExperimentAnnotationsId());
        requestDeletion(projectName, privateData.get(1));
        // Post reminders again - none should be posted
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0),1); // No new reminders since extension requested.
        verifyReminderPosted(projectName, privateData.get(1), 2); // No new reminders since deletion requested.
        message2 = String.format("Skipping reminder for experiment Id %d - Submitter has requested deletion", privateData.get(1).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, message2, "Skipped posting reminders for 2 experiments");
    }

    private void requestExtension(String projectName, DataFolderInfo folderInfo)
    {
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);

        String actionName = "Request Extension";
        checkUnauthorizedAccess(actionName);

        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        impersonate(folderInfo.getSubmitter());

        assertTextPresent(actionName);
        click(Locator.linkWithText(actionName));
        waitForText("Request Extension For Panorama Public Data");
        assertTextPresent("You are requesting an extension for the private data on Panorama Public at " + folderInfo.getShortUrl());
        clickButton("OK", 0);
        waitForText("An extension request was successfully submitted for the data at " + folderInfo.getShortUrl());

        stopImpersonating();
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        assertTextPresent(EXTENSION_MESSAGE_TITLE + folderInfo.getShortUrl());
    }

    private void requestDeletion(String projectName, DataFolderInfo folderInfo)
    {
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);

        String actionName = "Request Deletion";
        checkUnauthorizedAccess(actionName);

        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        impersonate(folderInfo.getSubmitter());

        assertTextPresent(actionName);
        click(Locator.linkWithText(actionName));
        waitForText("Request Deletion For Panorama Public Data");
        assertTextPresent("You are requesting deletion of the private data on Panorama Public at " + folderInfo.getShortUrl());
        clickButton("OK", 0);
        waitForText("A deletion request was successfully submitted for the data at " + folderInfo.getShortUrl());

        stopImpersonating();
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        assertTextPresent(DELETION_MESSAGE_TITLE + folderInfo.getShortUrl());
    }

    private void checkUnauthorizedAccess(String actionName)
    {
        impersonate(SUBMITTER_3); // This submitter does not have access
        waitForText(actionName);
        click(Locator.linkWithText(actionName));
        new LabkeyErrorPage(getDriver()).assertUnauthorized(checker());
        stopImpersonating(false); // Don't go home
        waitForText(actionName);
    }

    private void verifyPipelineJobLogMessage(String project, String... message)
    {
        goToProjectHome(project);
        goToDataPipeline();
        goToDataPipeline().clickStatusLink(0);
        assertTextPresent(message);
    }

    private void verifyNoReminderPosted(String projectName, List<DataFolderInfo> folderInfos)
    {
        for (DataFolderInfo folderInfo: folderInfos)
        {
            verifyNoReminderPosted(projectName, folderInfo);
        }
    }

    private void verifyNoReminderPosted(String projectName, DataFolderInfo folderInfo)
    {
        verifyReminderPosted(projectName, folderInfo, 0);
    }

    private void verifyReminderPosted(String projectName, DataFolderInfo folderInfo, int count)
    {
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        waitForText(MESSAGE_PAGE_TITLE + folderInfo.getShortUrl());
        if (count == 0)
        {
            assertTextNotPresent(REMINDER_MESSAGE_TITLE);
        }
        else
        {
            assertTextPresent(REMINDER_MESSAGE_TITLE, 1);
            assertTextPresent("Is the paper associated with this work already published?", count);
        }
    }

    private void saveSettings(String extensionLength, String delayUntilFirstReminder, String reminderFrequency)
    {
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));

        setFormElement(Locator.input("delayUntilFirstReminder"), delayUntilFirstReminder);
        setFormElement(Locator.input("reminderFrequency"), reminderFrequency);
        setFormElement(Locator.input("extensionLength"), extensionLength);
        clickButton("Save", 0);
        waitForText("Private data message settings saved");
        clickAndWait(Locator.linkWithText("Back to Panorama Public Admin Console"));

        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));
        assertEquals(String.valueOf(delayUntilFirstReminder), getFormElement(Locator.input("delayUntilFirstReminder")));
        assertEquals(String.valueOf(reminderFrequency), getFormElement(Locator.input("reminderFrequency")));
        assertEquals(String.valueOf(extensionLength), getFormElement(Locator.input("extensionLength")));
    }

    private void postRemindersNoExperimentsSelected(String projectName, int expectedExperimentCount)
    {
        postReminders(projectName, true, expectedExperimentCount, 0, 0);
    }

    private void postRemindersInTestMode(String projectName, int expectedExperimentCount, int selectExperimentCount, int pipelineJobCount)
    {
        postReminders(projectName, true, expectedExperimentCount, selectExperimentCount, pipelineJobCount);
    }


    private void postReminders(String projectName, boolean testMode, int expectedExperimentCount, int selectRowCount, int pipelineJobCount)
    {
        goToSendRemindersPage(projectName);

        DataRegionTable table = new DataRegionTable("ExperimentAnnotationsTable", getDriver());
        assertEquals(expectedExperimentCount, table.getDataRowCount());

        table.clearAllFilters();
        table.uncheckAllOnPage();

        if (selectRowCount == -1)
        {
            table.checkAllOnPage();
        }
        else
        {
            for(int i = 0; i < selectRowCount; i++)
            {
                table.checkCheckbox(i);
            }
        }

        if (testMode)
        {
            checkCheckbox(Locator.checkboxByName("testMode"));
        }

        clickButton("Post Reminders", 0);

        if (selectRowCount == 0)
        {
            waitForText("Please select at least one experiment");
            if (testMode)
            {
                assertChecked(Locator.checkboxByName("testMode"));
            }
        }
        else
        {
            waitForPipelineJobsToComplete(pipelineJobCount, "Post private data reminder messages", false);
            goToDataPipeline().clickStatusLink(0);
            if (testMode)
            {
                assertTextPresent("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");
            }
            else
            {
                assertTextNotPresent("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");
            }
        }
    }

    private void goToSendRemindersPage(String projectName)
    {
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));
        selectOptionByText(Locator.name("journal"), projectName);
        clickAndWait(Locator.linkWithText("Send Reminders Now"));
        waitForText(projectName, "A reminder message will be sent to the submitters of the selected experiments");
    }

    private static class DataFolderInfo
    {
        private final String _sourceFolder;
        private final String _targetFolder;
        private final String _shortUrl;
        private final String _experimentTitle;
        private final int _experimentAnnotationsId;
        private final String _submitter;
        private boolean _isPublic = false;

        public DataFolderInfo(String sourceFolder, String targetFolder, String shortUrl, String experimentTitle, int experimentAnnotationsId, String submitter)
        {
            _sourceFolder = sourceFolder;
            _targetFolder = targetFolder;
            _shortUrl = shortUrl;
            _experimentTitle = experimentTitle;
            _experimentAnnotationsId = experimentAnnotationsId;
            _submitter = submitter;
        }

        public String getSourceFolder()
        {
            return _sourceFolder;
        }

        public String getTargetFolder()
        {
            return _targetFolder;
        }

        public String getShortUrl()
        {
            return _shortUrl;
        }

        public String getExperimentTitle()
        {
            return _experimentTitle;
        }

        public int getExperimentAnnotationsId()
        {
            return _experimentAnnotationsId;
        }

        public String getSubmitter()
        {
            return _submitter;
        }

        public boolean isPublic()
        {
            return _isPublic;
        }

        public void setPublic(boolean isPublic)
        {
            _isPublic = isPublic;
        }
    }
}
