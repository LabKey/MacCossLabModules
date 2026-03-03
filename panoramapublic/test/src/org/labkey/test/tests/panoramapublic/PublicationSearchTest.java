package org.labkey.test.tests.panoramapublic;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.SimpleGetCommand;
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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.labkey.test.util.PermissionsHelper.READER_ROLE;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 7)
public class PublicationSearchTest extends PanoramaPublicBaseTest
{
    private static final String SKY_FILE = "MRMer.zip";

    private static final String SUBMITTER_USER = "submitter@panoramapublic.test";
    private static final String ADMIN_USER = "admin@panoramapublic.test";

    private static final String EXPERIMENT_TITLE = "Design, Implementation and Multisite Evaluation of a System Suitability Protocol" +
            " for the Quantitative Assessment of Instrument Performance in Liquid Chromatography-Multiple Reaction Monitoring-MS (LC-MRM-MS)";
    private static final String PXD_ID = "PXD010535";
    private static final String PMID = "23689285";

    private static final String SOURCE_FOLDER = "Pub Search Source";
    private static final String TARGET_FOLDER = "Pub Search Copy";

    private boolean _useMockNcbi = false;

    /**
     * Tests the publication search flow:
     * > search for publications
     * > notify submitter
     * > submitter dismisses suggestion
     * > verify publication not suggested again
     * Uses mock NCBI service when running on TeamCity, and NCBI API is not reachable.
     * The mock service returns saved data for PMID 23689285 (Abbatiello et al., Mol Cell Proteomics 2013).
     * The test dataset uses:
     * - Submitter: Birgit Schilling (author #3 on the paper)
     * - PXD: PXD010535
     * - Title matches the paper title
     */
    @Test
    public void testPublicationSearchAndDismiss()
    {
        String panoramaPublicProject = PANORAMA_PUBLIC;
        goToProjectHome(panoramaPublicProject);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.setSiteGroupPermissions("Guests", READER_ROLE);

        // Step 1: Set up mock NCBI service if running on TeamCity
        // On dev machine only mock if NCBI is not reachable.
        setupMockNcbiService();

        // Step 2: Create folder, submit to Panorama Public, and copy
        String testProject = getProjectName();
        String shortAccessUrl = setupFolderSubmitAndCopy(testProject, SOURCE_FOLDER, TARGET_FOLDER,
                EXPERIMENT_TITLE, SUBMITTER_USER, "Schilling", "Birgit", ADMIN_USER, SKY_FILE);

        // Navigate to the Panorama Public copy folder and get the experiment ID
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        goToExperimentDetailsPage();
        int exptId = Integer.parseInt(portalHelper.getUrlParam("id"));

        // Step 3: Assign PXD ID to the experiment via API
        assignPxdId(panoramaPublicProject, TARGET_FOLDER, exptId, PXD_ID);

        // Step 4: Search publications for the dataset — should find PMID 23689285
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        beginAt(WebTestHelper.buildURL("panoramapublic", getCurrentContainerPath(),
                "searchPublicationsForDataset", Map.of("id", String.valueOf(exptId))));
        waitForText("Publications Matches for Dataset");

        // Verify the publication was found
        assertTextPresent(PMID);
        // assertTextPresent("ProteomeXchange ID");
        assertTextPresent("Author, Title");

        // Step 5: Select the publication and notify the submitter
        click(Locator.radioButtonByNameAndValue("publicationId", PMID));
        clickButton("Notify Submitter");

        // Step 6: Verify the reminder message was posted in the support thread
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment", true, "Support Messages");
        waitForText("Submitted - " + shortAccessUrl);
        assertTextPresent("Action Required: Publication Found for Your Data on Panorama Public");
        assertTextPresent("We found a paper that appears to be associated with your private data on Panorama Public");
        assertTextPresent("Abbatiello SE, Mani DR, Schilling B"); // Verify the citation is included in the message
        assertElementPresent(Locator.linkWithHref("https://pubmed.ncbi.nlm.nih.gov/" + PMID)); // Verify the PubMed link

        // Step 7: Impersonate submitter, dismiss the publication suggestion
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment", true, "Support Messages");
        impersonate(SUBMITTER_USER);
        waitForText("Submitted - " + shortAccessUrl);
        click(Locator.linkWithText("Dismiss Publication Suggestion"));
        waitForText("Dismiss Publication Suggestion");
        clickButton("OK", 0);
        waitForText("The publication suggestion has been dismissed");
        stopImpersonating();

        // Verify the dismissal notification was posted in the support thread
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        portalHelper.clickWebpartMenuItem("Targeted MS Experiment", true, "Support Messages");
        waitForText("Submitted - " + shortAccessUrl);
        assertTextPresentInThisOrder(
               // "Abbatiello SE, Mani DR, Schilling B", // From previous message
                "Publication Suggestion Dismissed",
                "Thank you for letting us know that the suggested paper is not associated with your data on Panorama Public");
               // "Abbatiello SE, Mani DR, Schilling B"); // Citation should appear within the dismissal message, after the title and body

        // Step 8: Search publications for the dataset again and verify the dismissed publication is flagged
        goToProjectFolder(panoramaPublicProject, TARGET_FOLDER);
        beginAt(WebTestHelper.buildURL("panoramapublic", getCurrentContainerPath(),
                "searchPublicationsForDataset", Map.of("id", String.valueOf(exptId))));
        waitForText("Publications Matches for Dataset");
        assertTextPresent(PMID);
        // The dismissed publication should show "Yes" in the User Dismissed column
        assertTextPresent("User Dismissed");

        // Find the row containing the PMID radio button and verify the last cell contains "Yes"
        Locator dismissedCell = Locator.xpath("//tr[.//input[@value='" + PMID + "']]/td[last()]");
        assertEquals("Yes", getText(dismissedCell));

        // Verify that we cannot notify the submitter for a dismissed publication
        click(Locator.radioButtonByNameAndValue("publicationId", PMID));
        clickButton("Notify Submitter");
        assertTextPresent("has already dismissed the publication suggestion");

        // Step 9: Run the post reminders pipeline job and verify the dismissed publication is skipped
        // Set reminder settings: delayUntilFirstReminder=0, reminderFrequency=0, enablePublicationSearch=true
        savePrivateDataReminderSettings("2", "0", "0", true);

        // Post reminders with publication search enabled
        goToSendRemindersPage(panoramaPublicProject);
        DataRegionTable table = new DataRegionTable("ExperimentAnnotations", getDriver());
        table.checkAllOnPage();
        checkCheckbox(Locator.checkboxByName("searchPublications"));
        clickButton("Post Reminders", 0);
        waitForPipelineJobsToComplete(1, "Post private data reminder messages", false);

        // Verify the pipeline job log contains the skip message for the dismissed publication
        goToProjectHome(panoramaPublicProject);
        goToDataPipeline();
        goToDataPipeline().clickStatusLink(0);
        String skipMessage = String.format("User has dismissed publication suggestion for experiment %d; skipping search", exptId);
        assertTextPresent(skipMessage);
    }

    /**
     * POST to SetupMockNcbiServiceAction.
     */
    private void setupMockNcbiService()
    {
        boolean mock = TestProperties.isTestRunningOnTeamCity();
        if (!mock) return;
        boolean checkNcbiReachable = false;

        try
        {
            Connection connection = createDefaultConnection();
            SimpleGetCommand command = new SimpleGetCommand("panoramapublic", "setupMockNcbiService");
            command.setParameters(Map.of("checkNcbiReachable", checkNcbiReachable));
            CommandResponse response = command.execute(connection, "/");
            Object mockValue = response.getProperty("mock");
            _useMockNcbi = Boolean.TRUE.equals(mockValue);
            if (_useMockNcbi)
            {
                log("Using mock NCBI service" + (checkNcbiReachable ? " (NCBI not reachable)" : ""));
            }
            else
            {
                log("Using real NCBI service (NCBI is reachable)");
            }
        }
        catch (IOException | CommandException e)
        {
            fail("Failed to set up mock NCBI service: " + e.getMessage());
        }
    }

    /**
     * POST to RestoreNcbiServiceAction to restore the real NCBI service.
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

    /**
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

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        if (_useMockNcbi)
        {
            restoreNcbiService();
        }

        _userHelper.deleteUsers(false, SUBMITTER_USER, ADMIN_USER);
        super.doCleanup(afterTest);
    }
}
