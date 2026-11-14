/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.test.tests.panoramapublic;

import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.pages.LabkeyErrorPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.PermissionsHelper.READER_ROLE;

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
    private static final String PUBLICATION_FOUND_MESSAGE_TITLE = "Title: Action Required: Publication Found for Your Data on Panorama Public";

    private static final String DATA_2_TITLE = "Test for private data message reminder - DATA TWO";
    private static final String DATA_2_SUBMITTER_FIRST_NAME = "Sam";
    private static final String DATA_2_PMID = "90000002";
    private static final String DATA_2_PMC_ID = "90000003";
    // The job log when the dataset at the reminder limit is sent a "Publication Found" message, and the other private
    // dataset, which has a current extension, is skipped
    private static final String PUBLICATION_NOTICE_LOG = "1 processed (0 reminders, 1 new publication notices), 1 skipped";
    private static final String AT_LIMIT_NOTIFIED_LOG = "1 dataset at the reminder limit (0 skipped, 1 sent a publication notice)";

    private Map<String, String> _originalReminderSettings;
    private boolean _useMockNcbi = false;

    @Test
    public void testPrivateDataReminder()
    {
        // Capture the site wide reminder settings so restoreReminderSettings can put them back.
        _originalReminderSettings = getPrivateDataReminderSettings();

        String panoramaPublicProject = PANORAMA_PUBLIC;
        goToProjectHome(panoramaPublicProject);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.setSiteGroupPermissions("Guests", READER_ROLE);


        String testProject = getProjectName();

        DataFolderInfo folderInfo_1 = createAndSubmitFolder(testProject,
                "Private Data 1",
                panoramaPublicProject,
                "Private Data 1 Copy",
                "Test for private data message reminder - DATA ONE",
                SUBMITTER_1, "One", null,
                ADMIN_1);

        // The PubMed search by author and title needs the submitter's first name.
        DataFolderInfo folderInfo_2 = createAndSubmitFolder(testProject,
                "Private Data 2",
                panoramaPublicProject,
                "Private Data 2 Copy",
                DATA_2_TITLE,
                SUBMITTER_2, "Two", DATA_2_SUBMITTER_FIRST_NAME,
                ADMIN_2);

        DataFolderInfo folderInfo_3 = createAndSubmitFolder(testProject,
                "Private Data 3",
                panoramaPublicProject,
                "Private Data 3 Copy",
                "Test for private data message reminder - DATA THREE",
                SUBMITTER_3, "Three", null,
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
                                                 String submitter, String submitterLastName, @Nullable String submitterFirstName,
                                                 String admin)
    {
        log(String.format("Creating folder '%s' and copying to Panorama Public folder '%s'.", sourceFolder, targetFolder));
        String shortAccessUrl = setupFolderSubmitAndCopy(testProject, sourceFolder, targetFolder, experimentTitle,
                submitter, submitterLastName, submitterFirstName, admin, SKY_FILE_1);
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
        savePrivateDataReminderSettings("2", "12", "0");
        saveStopRemindersAfter("");

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
        savePrivateDataReminderSettings("2", "0", "0");

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
        savePrivateDataReminderSettings("2", "0", "1");
        // Post reminders again. Since reminder frequency is set to 1, no reminders will be posted to the first data.
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0), 1); // No new reminders since reminder frequency is set to 1.
        verifyReminderPosted(projectName, privateData.get(1), 1);
        message = String.format("Skipping reminder for experiment Id %d - Recent reminder already sent", privateData.get(0).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, "Skipped posting reminders for 1 experiment");

        // Change reminder frequency to 0 again.
        log("Changing reminder settings. Setting reminder frequency to 0.");
        savePrivateDataReminderSettings("2", "0", "0");

        // Request extension for the first experiment.
        log("Requesting extension for experiment Id " + privateData.getFirst().getExperimentAnnotationsId());
        requestExtension(projectName, privateData.getFirst());
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0),1); // No new reminders since extension requested.
        verifyReminderPosted(projectName, privateData.get(1), 2); // Reminder posted since reminder frequency is 0.
        message = String.format("Skipping reminder for experiment Id %d - Submitter requested an extension. Extension is current",
                privateData.get(0).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, "Skipped posting reminders for 1 experiment");

        // The second experiment has been sent 2 reminders. Stop reminders after 2.
        log("Changing reminder settings. Setting stop reminders after to 2.");
        saveStopRemindersAfter("2");
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(1), 2); // No new reminders since 2 have been sent without a response.
        message2 = String.format("Skipping reminder for experiment Id %d - Reminders stopped after 2 reminders",
                privateData.get(1).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, message2, "Skipped posting reminders for 2 experiments");

        // Ignore the reminder limit. A reminder is posted to the second experiment, but not to the first, which has a current extension.
        postReminders(projectName, false, true, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0), 1);
        verifyReminderPosted(projectName, privateData.get(1), 3);
        verifyPipelineJobLogMessage(projectName, message, "Skipped posting reminders for 1 experiment");

        testPublicationFoundAtReminderLimit(projectName, privateData.get(1), privateDataCount, pipelineJobCount);
        pipelineJobCount += 2;

        // Request deletion for the second experiment.
        log("Requesting deletion for experiment Id " + privateData.get(1).getExperimentAnnotationsId());
        requestDeletion(projectName, privateData.get(1));

        // Post reminders again - none should be posted
        postReminders(projectName, false, privateDataCount, -1, ++pipelineJobCount);
        verifyReminderPosted(projectName, privateData.get(0),1); // No new reminders since extension requested.
        verifyReminderPosted(projectName, privateData.get(1), 3); // No new reminders since deletion requested.
        message2 = String.format("Skipping reminder for experiment Id %d - Submitter has requested deletion", privateData.get(1).getExperimentAnnotationsId());
        verifyPipelineJobLogMessage(projectName, message, message2, "Skipped posting reminders for 2 experiments");
    }

    /**
     * A dataset at the reminder limit is still searched for a publication. The submitter is sent a "Publication Found"
     * message for a paper matched on author and title, and again when the same paper is later matched on the Panorama
     * URL. Neither message adds to ReminderCount. Uses the mock NCBI service on every server.
     */
    private void testPublicationFoundAtReminderLimit(String projectName, DataFolderInfo atLimit, int privateDataCount,
                                                     int pipelineJobCount)
    {
        useMockNcbiService();
        _useMockNcbi = true;
        int exptId = atLimit.getExperimentAnnotationsId();
        int reminderCountBefore = reminderCount(getDatasetStatus(projectName, atLimit.getTargetFolder(), exptId));

        // The PubMed search term has the form "LastName FirstName[Author] AND Title ..."
        registerMockPublication("pubmed", DATA_2_PMID, "Two " + DATA_2_SUBMITTER_FIRST_NAME,
                null, DATA_2_TITLE, "Two S", "2026/10/01 00:00", "J Proteome Res", "Journal of proteome research", null);
        postReminders(projectName, false, false, true, privateDataCount, -1, ++pipelineJobCount);
        verifyPipelineJobLogMessage(projectName, PUBLICATION_NOTICE_LOG, AT_LIMIT_NOTIFIED_LOG);
        verifyPublicationFoundPosted(projectName, atLimit, 1);
        Map<String, Object> status = getDatasetStatus(projectName, atLimit.getTargetFolder(), exptId);
        assertEquals("The paper matched on author and title should be saved", DATA_2_PMID, status.get("PotentialPublicationId"));
        assertEquals("The saved match should be on author and title", "Author, Title", status.get("PublicationMatchInfo"));
        assertEquals("A \"Publication Found\" message for a new paper should not add to ReminderCount",
                reminderCountBefore, reminderCount(status));

        // The same paper, found in PubMed Central by the dataset's Panorama URL. The search is not due for another month,
        // so "Search for Publications" on the Send Reminders page forces it.
        String shortUrlCode = atLimit.getShortUrl().substring(atLimit.getShortUrl().lastIndexOf('/') + 1);
        registerMockPublication("pmc", DATA_2_PMC_ID, shortUrlCode,
                DATA_2_PMID, DATA_2_TITLE, "Two S", "2026/10/01 00:00", "J Proteome Res", "Journal of proteome research", null);
        postReminders(projectName, false, false, true, privateDataCount, -1, ++pipelineJobCount);
        verifyPipelineJobLogMessage(projectName, PUBLICATION_NOTICE_LOG, AT_LIMIT_NOTIFIED_LOG);
        verifyPublicationFoundPosted(projectName, atLimit, 2);
        status = getDatasetStatus(projectName, atLimit.getTargetFolder(), exptId);
        assertEquals("The paper matched on the Panorama URL is the same paper", DATA_2_PMID, status.get("PotentialPublicationId"));
        assertTrue("The match on the Panorama URL should replace the match on author and title. Saved match: "
                + status.get("PublicationMatchInfo"), String.valueOf(status.get("PublicationMatchInfo")).contains("Panorama URL"));
        assertEquals("A \"Publication Found\" message for a new paper should not add to ReminderCount",
                reminderCountBefore, reminderCount(status));
    }

    private void verifyPublicationFoundPosted(String projectName, DataFolderInfo folderInfo, int count)
    {
        goToProjectFolder(projectName, folderInfo.getTargetFolder());
        gotoSupportMessage(folderInfo);
        assertTextPresent(PUBLICATION_FOUND_MESSAGE_TITLE, 1);
        assertTextPresent("We found a paper that appears to be associated with your private data on Panorama Public", count);
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


    private void postRemindersNoExperimentsSelected(String projectName, int expectedExperimentCount)
    {
        postReminders(projectName, true, expectedExperimentCount, 0, 0);
    }

    private void postRemindersInTestMode(String projectName, int expectedExperimentCount, int selectExperimentCount, int pipelineJobCount)
    {
        postReminders(projectName, true, expectedExperimentCount, selectExperimentCount, pipelineJobCount);
    }


    private void saveStopRemindersAfter(String stopRemindersAfter)
    {
        goToAdminConsole().goToSettingsSection();
        clickAndWait(Locator.linkWithText("Panorama Public"));
        clickAndWait(Locator.linkWithText("Private Data Reminder Settings"));
        setFormElement(Locator.input("stopRemindersAfter"), stopRemindersAfter);
        clickButton("Save", 0);
        waitForText("Private data reminder settings saved");
    }

    private void postReminders(String projectName, boolean testMode, int expectedExperimentCount, int selectRowCount, int pipelineJobCount)
    {
        postReminders(projectName, testMode, false, expectedExperimentCount, selectRowCount, pipelineJobCount);
    }

    private void postReminders(String projectName, boolean testMode, boolean ignoreReminderLimit, int expectedExperimentCount,
                               int selectRowCount, int pipelineJobCount)
    {
        postReminders(projectName, testMode, ignoreReminderLimit, false, expectedExperimentCount, selectRowCount, pipelineJobCount);
    }

    private void postReminders(String projectName, boolean testMode, boolean ignoreReminderLimit, boolean searchPublications,
                               int expectedExperimentCount, int selectRowCount, int pipelineJobCount)
    {
        goToSendRemindersPage(projectName);

        DataRegionTable table = new DataRegionTable("ExperimentAnnotations", getDriver());
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
        if (ignoreReminderLimit)
        {
            checkCheckbox(Locator.checkboxByName("ignoreReminderLimit"));
        }
        if (searchPublications)
        {
            checkCheckbox(Locator.checkboxByName("searchPublications"));
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


    @After
    public void restoreReminderSettings()
    {
        if (_useMockNcbi)
        {
            restoreNcbiService();
        }
        if (_originalReminderSettings != null)
        {
            restorePrivateDataReminderSettings(_originalReminderSettings, false);
        }
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false,SUBMITTER_1, SUBMITTER_2, SUBMITTER_3, ADMIN_1, ADMIN_2, ADMIN_3);

        super.doCleanup(afterTest);
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
