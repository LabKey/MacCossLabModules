package org.labkey.test.tests.panoramapublic;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.SimpleGetCommand;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestProperties;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.test.util.PermissionsHelper.READER_ROLE;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PublicationSearchTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE = "MRMer.zip";

    private static final String SUBMITTER_1 = "submitter1@panoramapublic.test";
    private static final String SUBMITTER_2 = "submitter2@panoramapublic.test";
    private static final String ADMIN_USER = "admin@panoramapublic.test";

    // --- Dataset 1: Abbatiello et al. (Mol Cell Proteomics 2013) ---
    // Found via PubMed fallback (author + title match). Paper does not reference PXD010535 in PMC.
    private static final String FOLDER_1 = "System Suitability Study";
    private static final String TARGET_FOLDER_1 = "System Suitability Copy";
    private static final String TITLE_1 = "Design, implementation and multisite evaluation of a system suitability protocol " +
            "for the quantitative assessment of instrument performance in liquid chromatography-multiple reaction monitoring-MS (LC-MRM-MS)";
    private static final String PXD_1 = "PXD010535";
    private static final String PMID_1 = "23689285";
    private static final String ARTICLE_TITLE_1 = TITLE_1; // Same as experiment title
    private static final String CITATION_1 = "Abbatiello SE, Mani DR, Schilling B, Maclean B, Zimmerman LJ, " +
            "Feng X, Cusack MP, Sedransk N, Hall SC, Addona T, Allen S, Dodder NG, Ghosh M, Held JM, Hedrick V, " +
            "Inerowicz HD, Jackson A, Keshishian H, Kim JW, Lyssand JS, Riley CP, Rudnick P, Sadowski P, " +
            "Shaddox K, Smith D, Tomazela D, Wahlander A, Waldemarson S, Whitwell CA, You J, Zhang S, " +
            "Kinsinger CR, Mesri M, Rodriguez H, Borchers CH, Buck C, Fisher SJ, Gibson BW, Liebler D, " +
            "Maccoss M, Neubert TA, Paulovich A, Regnier F, Skates SJ, Tempst P, Wang M, Carr SA. " +
            "Design, implementation and multisite evaluation of a system suitability protocol for the quantitative " +
            "assessment of instrument performance in liquid chromatography-multiple reaction monitoring-MS (LC-MRM-MS). " +
            "Mol Cell Proteomics. 2013 Sep;12(9):2623-39. doi: 10.1074/mcp.M112.027078. Epub 2013 May 20. " +
            "PMID: 23689285; PMCID: PMC3769335.";

    // --- Dataset 2: Wen et al. (Nat Commun 2025) ---
    // Found via PMC search by PXD. PMC returns two results: Nature article and bioRxiv preprint.
    // The preprint is filtered out by isPreprint().
    private static final String FOLDER_2 = "Carafe";
    private static final String TARGET_FOLDER_2 = "Carafe Copy";
    private static final String TITLE_2 = "Carafe: a tool for in silico spectral library generation for DIA proteomics";
    private static final String PXD_2 = "PXD056793";
    private static final String PMID_2 = "41198693";
    private static final String PMC_ID_2 = "12592563";
    private static final String PMC_ID_2_PREPRINT = "11507862";
    private static final String ARTICLE_TITLE_2 = "Carafe enables high quality in silico spectral library generation " +
            "for data-independent acquisition proteomics."; // Not identical to the experiment title but keywords match
    private static final String CITATION_2 = "Wen B, Hsu C, Shteynberg D, Zeng WF, Riffle M, Chang A, Mudge MC, " +
            "Nunn BL, MacLean BX, Berg MD, Villén J, MacCoss MJ, Noble WS. " +
            "Carafe enables high quality in silico spectral library generation for data-independent acquisition proteomics. " +
            "Nat Commun. 2025 Nov 6;16(1):9815. doi: 10.1038/s41467-025-64928-4. " +
            "PMID: 41198693; PMCID: PMC12592563.";

    private boolean _useMockNcbi = false;
    private Map<String, String> _originalReminderSettings = null;

    /**
     * Tests the publication search flow with two datasets:
     * Dataset 1 (Abbatiello et al.): search → notify → dismiss → verify not suggested again
     * Dataset 2 (Wen et al.): verify publication found via pipeline job reminders
     * Uses mock NCBI service when running on TeamCity.
     */
    @Test
    public void testPublicationSearchAndDismiss()
    {
        String panoramaPublicProject = PANORAMA_PUBLIC;
        goToProjectHome(panoramaPublicProject);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.setSiteGroupPermissions("Guests", READER_ROLE);

        // Step 1: Set up mock NCBI service if running on TeamCity
        setupMockNcbiService();

        // Step 2: Create dataset 1 folder, submit to Panorama Public, and copy
        String testProject = getProjectName();
        String shortAccessUrl1 = setupFolderSubmitAndCopy(testProject, FOLDER_1, TARGET_FOLDER_1,
                TITLE_1, SUBMITTER_1, "Schilling", "Birgit", ADMIN_USER, SKY_FILE);
        int exptId1 = getExperimentId(panoramaPublicProject, TARGET_FOLDER_1);
        assignPxdId(panoramaPublicProject, TARGET_FOLDER_1, exptId1, PXD_1);

        // Step 3: Create dataset 2 folder, submit to Panorama Public, and copy
        String shortAccessUrl2 = setupFolderSubmitAndCopy(testProject, FOLDER_2, TARGET_FOLDER_2,
                TITLE_2, SUBMITTER_2, "Wen", "Bo", ADMIN_USER, SKY_FILE);
        int exptId2 = getExperimentId(panoramaPublicProject, TARGET_FOLDER_2);
        assignPxdId(panoramaPublicProject, TARGET_FOLDER_2, exptId2, PXD_2);

        // Step 4: Search publications for dataset 1 — should find PMID 23689285 via PubMed fallback
        searchPublicationsForDataset(panoramaPublicProject, TARGET_FOLDER_1, exptId1);
        assertTextPresent(PMID_1);
        assertTextPresent("Author, Title");
        assertTextNotPresent("ProteomeXchange ID"); // Paper does not contain the PXD

        // Step 5: Select the publication and notify the submitter
        click(Locator.radioButtonByNameAndValue("publicationId", PMID_1));
        clickButton("Notify Submitter");

        // Step 6: Verify the reminder message was posted in the support thread
        goToSupportMessages(panoramaPublicProject, TARGET_FOLDER_1);
        waitForText("Submitted - " + shortAccessUrl1);
        assertTextPresent("Action Required: Publication Found for Your Data on Panorama Public");
        assertTextPresent("We found a paper that appears to be associated with your private data on Panorama Public");
        assertTextPresent("Abbatiello SE, Mani DR, Schilling B");
        assertElementPresent(Locator.linkWithHref("https://pubmed.ncbi.nlm.nih.gov/" + PMID_1));

        // Step 6a: Verify DatasetStatus was updated for dataset 1 after notifying submitter
        Map<String, Object> dsStatus1 = getDatasetStatus(panoramaPublicProject, TARGET_FOLDER_1, exptId1);
        assertNotNull("Expected DatasetStatus row for dataset 1 after notification", dsStatus1);
        assertNotNull("Expected lastReminderDate to be set after notification", dsStatus1.get("LastReminderDate"));
        assertEquals("Expected potentialPublicationId to be set", PMID_1, dsStatus1.get("PotentialPublicationId"));
        assertEquals("Expected publicationType to be PubMed", "PubMed", dsStatus1.get("PublicationType"));
        assertNotNull("Expected citation to be cached", dsStatus1.get("Citation"));

        // Verify DatasetStatus does NOT exist for dataset 2 yet (no notification sent)
        Map<String, Object> dsStatus2Before = getDatasetStatus(panoramaPublicProject, TARGET_FOLDER_2, exptId2);
        assertNull("Expected no DatasetStatus row for dataset 2 before reminders", dsStatus2Before);

        // Step 7: Impersonate submitter, dismiss the publication suggestion
        goToSupportMessages(panoramaPublicProject, TARGET_FOLDER_1);
        impersonate(SUBMITTER_1);
        waitForText("Submitted - " + shortAccessUrl1);
        click(Locator.linkWithText("Dismiss Publication Suggestion"));
        waitForText("You are dismissing the publication suggestion for your data on Panorama Public");
        assertTextPresent(shortAccessUrl1);
        clickButton("OK", 0);
        waitForText("The publication suggestion has been dismissed");
        stopImpersonating();

        // Verify the dismissal notification was posted in the support thread
        goToSupportMessages(panoramaPublicProject, TARGET_FOLDER_1);
        waitForText("Submitted - " + shortAccessUrl1);
        assertTextPresentInThisOrder(
                "Publication Suggestion Dismissed",
                "Thank you for letting us know that the suggested paper is not associated with your data on Panorama Public");

        // Step 8: Search publications for dataset 1 again and verify the dismissed publication is flagged
        searchPublicationsForDataset(panoramaPublicProject, TARGET_FOLDER_1, exptId1);
        assertTextPresent(PMID_1);
        assertTextPresent("User Dismissed");

        // Verify the dismissed cell contains "Yes"
        Locator dismissedCell = Locator.xpath("//tr[.//input[@value='" + PMID_1 + "']]/td[last()]");
        assertEquals("Yes", getText(dismissedCell));

        // Verify that we cannot notify the submitter for a dismissed publication
        click(Locator.radioButtonByNameAndValue("publicationId", PMID_1));
        clickButton("Notify Submitter");
        assertTextPresent("The user has already dismissed the publication suggestion PubMed ID " + PMID_1 + " for this dataset");

        // Step 9: Run reminders in TEST MODE — verify DatasetStatus is NOT updated
        // Save current settings so they can be restored in doCleanup
        _originalReminderSettings = getPrivateDataReminderSettings();
        savePrivateDataReminderSettings("2", "0", "0", true);

        // Post reminders in test mode with publication search enabled
        goToSendRemindersPage(panoramaPublicProject);
        DataRegionTable table = new DataRegionTable("ExperimentAnnotations", getDriver());
        table.checkAllOnPage();
        assertChecked(Locator.checkboxByName("searchPublications"));
        checkCheckbox(Locator.checkboxByName("testMode"));
        clickButton("Post Reminders", 0);
        waitForPipelineJobsToComplete(1, "Post private data reminder messages", false);

        // Verify test mode was logged
        goToProjectHome(panoramaPublicProject);
        goToDataPipeline();
        goToDataPipeline().clickStatusLink(0);
        assertTextPresent("RUNNING IN TEST MODE - MESSAGES WILL NOT BE POSTED.");

        // Verify DatasetStatus for dataset 2 was NOT updated in test mode
        Map<String, Object> dsStatus2TestMode = getDatasetStatus(panoramaPublicProject, TARGET_FOLDER_2, exptId2);
        assertNull("Expected no DatasetStatus row for dataset 2 after test-mode run", dsStatus2TestMode);

        // Step 10: Run reminders in ACTUAL mode and verify the following:
        // 1. pipeline job skips searching for publicaiton for dataset 1
        // 2. message posted for dataset 1 is about reminding user to make data public rather than about a publication
        // 3. a publication is found for dataset 2 and message posted to the message thread
        // 4. verify DatasetStatus for dataset 2 IS updated
        goToSendRemindersPage(panoramaPublicProject);
        table = new DataRegionTable("ExperimentAnnotations", getDriver());
        table.checkAllOnPage();
        assertChecked(Locator.checkboxByName("searchPublications"));
        clickButton("Post Reminders", 0);
        waitForPipelineJobsToComplete(2, "Post private data reminder messages", false);

        // Verify the pipeline job log contains the skip message for the dismissed dataset 1 publication
        goToProjectHome(panoramaPublicProject);
        goToDataPipeline();
        goToDataPipeline().clickStatusLink(0);
        String skipMessage = String.format("User dismissed publication for experiment %d", exptId1);
        assertTextPresent(skipMessage);
        assertTextPresent("Publication search deferred");

        // Verify that the reminder for the dismissed dataset 1 is about making data public, not about a publication.
        // The message thread already has a "Publication Found" message from Step 6 (before dismissal),
        // so we check that the pipeline job posted a "Status Update" message that will appear after the "Publication Found" message.
        goToSupportMessages(panoramaPublicProject, TARGET_FOLDER_1);
        waitForText("Submitted - " + shortAccessUrl1);
        assertTextPresentInThisOrder("Action Required: Publication Found for Your Data on Panorama Public",
                "Action Required: Status Update for Your Private Data on Panorama Public");

        // Verify that the pipeline job found a publication for dataset 2 and posted a message
        goToSupportMessages(panoramaPublicProject, TARGET_FOLDER_2);
        waitForText("Submitted - " + shortAccessUrl2);
        assertTextPresent("Action Required: Publication Found for Your Data on Panorama Public");
        assertTextPresent("We found a paper that appears to be associated with your private data on Panorama Public");
        assertTextPresent("Wen B, Hsu C, Shteynberg D");
        assertElementPresent(Locator.linkWithHref("https://pubmed.ncbi.nlm.nih.gov/" + PMID_2));

        // Verify DatasetStatus for dataset 2 was updated after actual posting
        Map<String, Object> dsStatus2AfterPost = getDatasetStatus(panoramaPublicProject, TARGET_FOLDER_2, exptId2);
        assertNotNull("Expected DatasetStatus row for dataset 2 after actual posting", dsStatus2AfterPost);
        assertEquals("Expected potentialPublicationId for dataset 2", PMID_2, dsStatus2AfterPost.get("PotentialPublicationId"));
        assertNotNull("Expected publicationType for dataset 2", dsStatus2AfterPost.get("PublicationType"));
        assertNotNull("Expected lastReminderDate for dataset 2", dsStatus2AfterPost.get("LastReminderDate"));
        assertNotNull("Expected citation to be cached for dataset 2", dsStatus2AfterPost.get("Citation"));
    }

    /*
     * Navigate to the Panorama Public copy folder and get the experiment ID.
     */
    private int getExperimentId(String panoramaPublicProject, String targetFolder)
    {
        goToProjectFolder(panoramaPublicProject, targetFolder);
        goToExperimentDetailsPage();
        return Integer.parseInt(portalHelper.getUrlParam("id"));
    }

    /*
     * Assign a PXD ID to an experiment using the UpdatePxDetails page.
     * Cannot use UpdateRowsCommand because the ExperimentAnnotations table is not updatable via the HTTP-based APIs.
     */
    private void assignPxdId(String projectName, String folderName, int exptId, String pxdId)
    {
        goToProjectFolder(projectName, folderName);
        beginAt(WebTestHelper.buildURL("panoramapublic", getCurrentContainerPath(),
                "updatePxDetails", Map.of("id", String.valueOf(exptId))));
        waitForText("Update ProteomeXchange Details");
        setFormElement(Locator.input("pxId"), pxdId);
        clickButton("Update");
        log("Assigned PXD ID " + pxdId + " to experiment " + exptId);
    }

    /*
     * Navigate to the search publications page for a dataset.
     */
    private void searchPublicationsForDataset(String panoramaPublicProject, String targetFolder, int exptId)
    {
        goToProjectFolder(panoramaPublicProject, targetFolder);
        beginAt(WebTestHelper.buildURL("panoramapublic", getCurrentContainerPath(),
                "searchPublicationsForDataset", Map.of("id", String.valueOf(exptId))));
        waitForText("Publications Matches for Dataset");
    }

    /*
     * Navigate to the support messages for a dataset.
     */
    private void goToSupportMessages(String panoramaPublicProject, String targetFolder)
    {
        goToProjectFolder(panoramaPublicProject, targetFolder);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment", true, "Support Messages");
    }

    /*
     * Query the DatasetStatus table for a given experiment ID.
     * Returns null if no row exists, or a map of column name → value.
     */
    private Map<String, Object> getDatasetStatus(String panoramaPublicProject, String targetFolder, int exptId)
    {
        try
        {
            Connection connection = createDefaultConnection();
            SelectRowsCommand cmd = new SelectRowsCommand("panoramapublic", "DatasetStatus");
            cmd.addFilter(new Filter("ExperimentAnnotationsId", exptId));
            cmd.setColumns(List.of("ExperimentAnnotationsId", "PotentialPublicationId", "PublicationType",
                    "PublicationMatchInfo", "Citation", "LastReminderDate", "UserDismissedPublication"));
            String containerPath = "/" + panoramaPublicProject + "/" + targetFolder;
            SelectRowsResponse resp = cmd.execute(connection, containerPath);
            List<Map<String, Object>> rows = resp.getRows();
            if (rows.isEmpty())
            {
                return null;
            }
            assertEquals("Expected at most one DatasetStatus row for experiment " + exptId, 1, rows.size());
            return rows.getFirst();
        }
        catch (IOException | CommandException e)
        {
            fail("Failed to query DatasetStatus: " + e.getMessage());
            return null;
        }
    }

    /*
     * On TeamCity: set up the mock NCBI service and register mock publication data.
     * On dev machine: verify that NCBI is reachable; fail with a clear message if not.
     */
    private void setupMockNcbiService()
    {
        boolean useMockNcbiService = TestProperties.isTestRunningOnTeamCity();
        if (useMockNcbiService)
        {
            initMockNcbiService();
        }
        else
        {
            assertTrue("NCBI E-utilities API is not reachable. Check your network connection.", isNcbiReachable());
            log("Using real NCBI service");
        }
    }

    private void initMockNcbiService()
    {
        try
        {
            Connection connection = createDefaultConnection();
            SimpleGetCommand command = new SimpleGetCommand("panoramapublic", "setupMockNcbiService");
            command.execute(connection, "/");
            _useMockNcbi = true;
            log("Using mock NCBI service");
            registerMockPublications();
        }
        catch (IOException | CommandException e)
        {
            fail("Failed to set up mock NCBI service: " + e.getMessage());
        }
    }

    private static boolean isNcbiReachable()
    {
        try
        {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/einfo.fcgi").openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "PanoramaPublic/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            boolean reachable = responseCode >= 200 && responseCode < 300;
            conn.disconnect();
            return reachable;
        }
        catch (IOException e)
        {
            return false;
        }
    }


    /*
     * Register mock publication data with the mock NCBI service.
     */
    private void registerMockPublications()
    {
        // Dataset 1: PMID 23689285 (Abbatiello et al.) — found via PubMed fallback (author + title match)
        // Paper does not reference PXD010535 in PMC.
        registerMockPublication("pubmed", PMID_1, "Schilling",
                null, ARTICLE_TITLE_1,
                "Abbatiello SE,Mani DR,Schilling B,Maclean B,Zimmerman LJ,Carr SA",
                "2013/09/01 00:00", "Mol Cell Proteomics", "Molecular & cellular proteomics : MCP",
                CITATION_1);

        // Dataset 2: PMID 41198693 (Wen et al.) — found via PMC search by PXD ID
        // PMC search for PXD056793 returns two articles: Nature + bioRxiv preprint.

        // Nature article (published) — PMC ID 12592563, PMID 41198693
        registerMockPublication("pmc", PMC_ID_2, PXD_2,
                PMID_2, ARTICLE_TITLE_2,
                "Wen B,Hsu C,Shteynberg D,Zeng WF,Riffle M,Chang A,Mudge MC,Nunn BL,MacLean BX,Berg MD,Villén J,MacCoss MJ,Noble WS",
                "2025/11/06 00:00", "Nat Commun", "Nature communications",
                CITATION_2);

        // bioRxiv preprint — PMC ID 11507862, PMID 39463980 (will be filtered out by isPreprint)
        registerMockPublication("pmc", PMC_ID_2_PREPRINT, PXD_2,
                "39463980", ARTICLE_TITLE_2,
                "Wen B,Hsu C,Shteynberg D,Zeng WF,Riffle M,Chang A,Mudge M,Nunn BL,MacLean BX,Berg MD,Villén J,MacCoss MJ,Noble WS",
                "2025/08/04 00:00", "bioRxiv", "bioRxiv : the preprint server for biology",
                null);
    }

    /*
     * Register a single mock article with the mock NCBI service via the RegisterMockPublicationAction API.
     */
    private void registerMockPublication(String database, String id, String searchKey,
                                         String pmid, String title, String authors,
                                         String sortDate, String source, String journalFull,
                                         String citation)
    {
        try
        {
            Connection connection = createDefaultConnection();
            SimpleGetCommand command = new SimpleGetCommand("panoramapublic", "registerMockPublication");
            Map<String, Object> params = new HashMap<>();
            params.put("database", database);
            params.put("id", id);
            params.put("searchKey", searchKey);
            params.put("title", title);
            params.put("authors", authors);
            params.put("pubDate", sortDate);
            params.put("source", source);
            params.put("journalFull", journalFull);
            if (pmid != null) params.put("pmid", pmid);
            if (citation != null) params.put("citation", citation);
            command.setParameters(params);
            command.execute(connection, "/");
            log("Registered mock publication: " + database + " " + id);
        }
        catch (IOException | CommandException e)
        {
            fail("Failed to register mock publication: " + e.getMessage());
        }
    }

    /*
     * Restore the real NCBI service.
     */
    private void restoreNcbiService()
    {
        try
        {
            Connection connection = createDefaultConnection();
            SimpleGetCommand command = new SimpleGetCommand("panoramapublic", "restoreNcbiService");
            command.execute(connection, "/");
            log("Restored real NCBI service");
        }
        catch (IOException | CommandException e)
        {
            log("Warning: Failed to restore NCBI service: " + e.getMessage());
        }
    }

    @After
    public void resetAfterTest()
    {
        if (_useMockNcbi)
        {
            restoreNcbiService();
        }

        if (_originalReminderSettings != null)
        {
            savePrivateDataReminderSettings(
                    _originalReminderSettings.get("extensionLength"),
                    _originalReminderSettings.get("delayUntilFirstReminder"),
                    _originalReminderSettings.get("reminderFrequency"),
                    Boolean.parseBoolean(_originalReminderSettings.get("enablePublicationSearch")));
        }
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false, SUBMITTER_1, SUBMITTER_2, ADMIN_USER);
        super.doCleanup(afterTest);
    }
}
