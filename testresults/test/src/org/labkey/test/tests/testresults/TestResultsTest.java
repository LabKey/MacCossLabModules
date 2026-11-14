/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.test.tests.testresults;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.MacCossLabModules;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.APITestHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.TextSearcher;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class TestResultsTest extends BaseWebDriverTest
{
    private static final String PROJECT_NAME = "TestResultsTest" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    static final String COMPUTER_NAME_1 = "TEST-PC-1";
    static final String COMPUTER_NAME_2 = "TEST-PC-2";

    // Training stats are exact (avg/stddev of integer averagemem), so this only absorbs the
    // floating round-trip through the query API. It is not a real tolerance.
    private static final double EPSILON = 1e-6;

    private static final Locator SUBMIT_BUTTON = Locator.css("input[type='submit'][value='Submit']");

    // XPath for the problems matrix table (header cell contains "Fail: | Leak: | Hang:")
    private static final String PROBLEMS_TABLE_XPATH =
            "//table[contains(@class,'decoratedtable')]" +
            "[.//td[contains(.,'Fail:') and contains(.,'Leak:') and contains(.,'Hang:')]]";

    // Run IDs populated in @BeforeClass, used across test methods
    private static int _disposableRunId = -1;
    private static int _cleanRunId = -1;
    private static int _failRunId  = -1;
    private static int _leakRunId  = -1;

    @BeforeClass
    public static void setupProject()
    {
        TestResultsTest init = getCurrentTest();
        init.doSetup();
    }

    @LogMethod
    private void doSetup()
    {
        _containerHelper.createProject(PROJECT_NAME, null);
        _containerHelper.enableModule("TestResults");
        new PortalHelper(this).addWebPart("Test Results");

        // TEST-PC-1 runs
        postSampleXml("testresults/pc1-run-0114-disposable.xml");
        postSampleXml("testresults/pc1-run-0115-clean.xml");
        postSampleXml("testresults/pc1-run-0116-failures.xml");
        postSampleXml("testresults/pc1-run-0117-leaks.xml");

        // TEST-PC-2 runs on the same dates
        postSampleXml("testresults/pc2-run-0115-clean.xml");
        postSampleXml("testresults/pc2-run-0116-failures.xml");
        postSampleXml("testresults/pc2-run-0117-leaks.xml");

        // All runs in this fresh container are our sample runs, sorted by posttime ascending.
        // PC2 runs are interleaved with PC1 runs, so identify PC1 runs by computer name.
        List<Map<String, Object>> runs = queryRuns();
        assertEquals("Expected 7 posted runs", 7, runs.size());

        List<Map<String, Object>> pc1Runs = runs.stream()
                .filter(r -> COMPUTER_NAME_1.equals(r.get("userid/username")))
                .toList();
        assertEquals("Expected 4 " + COMPUTER_NAME_1 + " runs", 4, pc1Runs.size());
        _disposableRunId = (Integer) pc1Runs.get(0).get("id");
        _cleanRunId = (Integer) pc1Runs.get(1).get("id");
        _failRunId  = (Integer) pc1Runs.get(2).get("id");
        _leakRunId  = (Integer) pc1Runs.get(3).get("id");
    }

    /**
     * Posts a sample XML file to PostAction in the main project container.
     */
    private void postSampleXml(String sampleDataRelativePath)
    {
        postSampleXml(sampleDataRelativePath, PROJECT_NAME);
    }

    /**
     * Posts a sample XML file to PostAction in the given container.
     */
    private void postSampleXml(String sampleDataRelativePath, String containerPath)
    {
        File xmlFile = TestFileUtils.getSampleData(sampleDataRelativePath);
        String postUrl = WebTestHelper.buildURL("testresults", containerPath, "post");

        try (CloseableHttpClient httpClient = WebTestHelper.getHttpClient())
        {
            HttpPost request = new HttpPost(postUrl);
            APITestHelper.injectCookies(request);
            request.setEntity(MultipartEntityBuilder.create()
                    .addBinaryBody("xml_file", xmlFile, ContentType.TEXT_XML, xmlFile.getName())
                    .build());
            httpClient.execute(request, response -> {
                String body = EntityUtils.toString(response.getEntity());
                JSONObject json = new JSONObject(body);
                assertTrue("PostAction failed for " + xmlFile.getName() + ": " + body,
                        json.optBoolean("Success", false));
                return null;
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to post sample XML:" + sampleDataRelativePath, e);
        }
    }

    /**
     * Queries all testruns in the test container, sorted by posttime ascending.
     */
    private List<Map<String, Object>> queryRuns()
    {
        try
        {
            Connection connection = WebTestHelper.getRemoteApiConnection();
            SelectRowsCommand cmd = new SelectRowsCommand("testresults", "testruns");
            cmd.setSorts(List.of(new Sort("posttime")));
            cmd.setColumns(List.of("id", "posttime", "userid/username", "passedtests", "failedtests", "leakedtests"));
            SelectRowsResponse response = cmd.execute(connection, PROJECT_NAME);
            return response.getRows();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to query test runs", e);
        }
    }

    @Before
    public void navigateToProject()
    {
        goToProjectHome(PROJECT_NAME);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testBeginPage()
    {
        // Start at 01/17/2026 via URL so the datepicker opens near our sample data dates
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "begin", Map.of("end", "01/17/2026")));
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2);
        assertTextPresent("Top Failures");
        assertTextNotPresent("Top Leaks");

        // Verify the problems matrix: TestFailOne fails on both PCs (2 icons),
        // TestFailTwo fails only on PC-1 (1 icon)
        assertProblemsMatrixPresent(COMPUTER_NAME_1, COMPUTER_NAME_2);
        assertProblemIconCount("TestFailOne", "fail.png", 2);
        assertProblemIconCount("TestFailTwo", "fail.png", 1);

        // Verify Top Failures summary table: occurrences across all runs in the view period
        assertTopSummaryEntry("Top Failures", "TestFailOne", 2);
        assertTopSummaryEntry("Top Failures", "TestFailTwo", 1);

        // Verify the datepicker reflects our starting date, then navigate BACKWARD
        // one day to 01/16/2026 — clean run, no failures or leaks
        verifyDateInDatepicker(1, 17, 2026);
        goToPrevDay(1);
        verifyDateInDatepicker(1, 16, 2026);
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2);
        assertTextNotPresent("Top Failures");
        assertTextNotPresent("Top Leaks");

        // Navigate FORWARD two days to 01/18/2026 — leaks on both PCs
        goToNextDay(2);
        verifyDateInDatepicker(1, 18, 2026);
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2);
        assertTextPresent("Top Failures"); // cumulative — failures from 01/17 still in the view period
        assertTextPresent("Top Leaks");

        // Verify the problems matrix: TestWithMemoryLeak leaks on both PCs (2 icons),
        // TestWithHandleLeak leaks only on PC-1 (1 icon)
        assertProblemsMatrixPresent(COMPUTER_NAME_1, COMPUTER_NAME_2);
        assertProblemIconCount("TestWithMemoryLeak", "leak.png", 2);
        assertProblemIconCount("TestWithHandleLeak", "leak.png", 1);

        // Verify Top Leaks summary table: occurrences and mean leak values
        assertTopSummaryEntry("Top Leaks", "TestWithMemoryLeak", 2);
        assertTopSummaryEntry("Top Leaks", "TestWithHandleLeak", 1);
        assertTopLeakMean("TestWithMemoryLeak", "3 kb");
        assertTopLeakMean("TestWithHandleLeak", "5 handles");

        // Verify viewType selector defaults to Month
        Locator viewTypeSelect = Locator.id("viewType");
        assertEquals("Default viewType should be Month", "Month", getSelectedOptionText(viewTypeSelect));

        // Select Week — verify URL parameter and selector state after page reload
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "wk"));
        assertEquals("wk", getUrlParam("viewType"));
        assertEquals("Week", getSelectedOptionText(viewTypeSelect));

        // Select Year
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "yr"));
        assertEquals("yr", getUrlParam("viewType"));
        assertEquals("Year", getSelectedOptionText(viewTypeSelect));

        // Select back to Month
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "mo"));
        assertEquals("mo", getUrlParam("viewType"));
        assertEquals("Month", getSelectedOptionText(viewTypeSelect));
    }

    @Test
    public void testShowRunPage()
    {
        // Navigate to user page with sample data dates to get "run details" links
        navigateToUserPageWithDateRange();

        // Runs are sorted descending by date: row 0 = 01/18 (leaks), row 1 = 01/17 (failures), row 2 = 01/16 (clean)

        // Click the first "run details" link (01/18 — leaks run)
        clickAndWait(Locator.linkWithText("run details").index(0));
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 0", "Leaks : 2");
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");

        // Sort by Duration (descending) and verify the sort parameter is applied
        clickAndWait(Locator.linkWithText("Duration"));
        assertEquals("duration", getUrlParam("filter"));

        // Sort by Managed Memory (descending) — tests later in the run have higher memory,
        // so TestWithHandleLeak (id=100) should appear before TestAlpha (id=1)
        clickAndWait(Locator.linkContainingText("Managed Memory"));
        assertEquals("managed", getUrlParam("filter"));
        assertTestPassesSortedAs("TestWithHandleLeak", "TestAlpha");

        // Sort by Total Memory (descending) — same ordering principle
        clickAndWait(Locator.linkContainingText("Total Memory"));
        assertEquals("total", getUrlParam("filter"));
        assertTestPassesSortedAs("TestWithHandleLeak", "TestAlpha");

        // Navigate to user page again for the failures run
        navigateToUserPageWithDateRange();
        clickAndWait(Locator.linkWithText("run details").index(1));
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 2", "Leaks : 0");
        assertTextPresent("TestFailOne", "TestFailTwo");

        // Navigate to user page again for the clean run
        navigateToUserPageWithDateRange();
        clickAndWait(Locator.linkWithText("run details").index(2));
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 0", "Leaks : 0");
    }

    @Test
    public void testRunLookup()
    {
        // Look up the leaks run
        navigateToRunById(_leakRunId);
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 0", "Leaks : 2");
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");

        // Look up the failures run
        navigateToRunById(_failRunId);
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 2", "Leaks : 0");
        assertTextPresent("TestFailOne", "TestFailTwo");

        // Look up the clean run
        navigateToRunById(_cleanRunId);
        assertTextPresent(COMPUTER_NAME_1, "Passed Tests : 150", "Failures : 0", "Leaks : 0");
    }

    @Test
    public void testLongTermPage()
    {
        // Navigate to Long Term page via tab click
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Long Term"));

        // Use the viewType selector to switch between views
        Locator viewTypeSelect = Locator.id("view-type-combobox");

        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "wk"));
        assertEquals("wk", getUrlParam("viewType"));

        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "mo"));
        assertEquals("mo", getUrlParam("viewType"));

        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "yr"));
        assertEquals("yr", getUrlParam("viewType"));
    }

    @Test
    public void testShowFailuresPage()
    {
        // All "Viewing data for: <start> - <end>" assertions below use
        // assertTextPresentInThisOrder with just the MM/dd/yyyy date parts
        // (no time-of-day). The controller stamps both start and end to
        // 08:01 via setToEightAM, but the start wall-clock time can shift
        // across DST boundaries (e.g. start 07:01 PST, end 08:01 PDT when
        // the window crosses spring-forward), so asserting just the dates
        // keeps the test stable.

        // --- Path 1: navigate via runDetail.jsp ---
        // The "TestFailOne" link on runDetail.jsp does NOT set the `end`
        // URL parameter (only `failedTest` and `viewType=wk`), so the
        // controller falls back to `new Date()` — the displayed end date
        // is today. Capture it once so we don't drift across a midnight
        // rollover during the test.
        LocalDate today = LocalDate.now();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String todayStr = today.format(dateFmt);

        navigateToRunById(_failRunId);
        clickAndWait(Locator.linkWithText("TestFailOne"));
        assertTextPresent("TestFailOne");

        // Default view is Week → start = today - 7 days
        Locator viewTypeSelect = Locator.id("view-type-combobox");
        assertEquals("Week", getSelectedOptionText(viewTypeSelect));
        assertTextPresentInThisOrder("Viewing data for:",
                today.minusDays(7).format(dateFmt), " - " + todayStr);

        // Switch to Month view → start = today - 30 days
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "mo"));
        assertEquals("mo", getUrlParam("viewType"));
        assertEquals("Month", getSelectedOptionText(viewTypeSelect));
        assertTextPresentInThisOrder("Viewing data for:",
                today.minusDays(30).format(dateFmt), " - " + todayStr);

        // --- Path 2: navigate via the Fail/Leak/Hang table on rundown.jsp ---
        // The link in this table sets `end` to the begin page's selected
        // date but does not set `viewType`, so the controller defaults to
        // ViewType.DAY → start = end - 1 day. The link
        // opens in a new browser tab via target="_blank".
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "begin",
                Map.of("end", "01/17/2026")));
        Locator failLeakHangLink = Locator.xpath(PROBLEMS_TABLE_XPATH + "//a[text()='TestFailOne']");
        click(failLeakHangLink);
        switchToWindow(1); // Link opens in a new tab
        try
        {
            assertTextPresent("TestFailOne");
            assertTextPresentInThisOrder("Viewing data for:", "01/16/2026", " - 01/17/2026");
        }
        finally
        {
            getDriver().close(); // Close the tab
            switchToMainWindow();
        }
    }

    @Test
    public void testShowFlaggedPage()
    {
        // Navigate to Flags page — no runs are flagged yet
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Flags"));
        assertTextPresent("There are currently no flagged runs.");

        // Navigate to a run and flag it
        navigateToRunById(_cleanRunId);
        toggleRunFlag();

        // Verify the Flags page now shows the flagged run. Each flagged row is
        // rendered (in flagged.jsp) as a link with text:
        //   "id: <runId> / <userId> / <postTime>"
        // Match by the runId prefix
        clickAndWait(Locator.linkWithText("Flags"));
        assertTextNotPresent("There are currently no flagged runs.");
        assertTextPresent("Flagged Runs");
        assertElementPresent(Locator.tag("a").startsWith("id: " + _cleanRunId + " /"));

        // Unflag the run — navigate back to the run detail page
        navigateToRunById(_cleanRunId);
        toggleRunFlag();

        // Verify the Flags page is empty again
        clickAndWait(Locator.linkWithText("Flags"));
        assertTextPresent("There are currently no flagged runs.");
    }

    @Test
    public void testTrainingDataPage()
    {
        // The trainingdata.jsp page renders users WITH training runs inside
        // <table id="trainingdata"> (each user gets a section header, run
        // rows with Date | Duration | Tests Run | Failure Count | Mean Memory
        // | Remove, and a `.stats-row` with "RunCount:N"). Users WITHOUT
        // training runs are listed in a separate <table> below, under a
        // "No Training Data --" header
        Locator.XPathLocator trainingTable = Locator.tagWithId("table", "trainingdata");
        Locator removeLink = trainingTable.descendant(Locator.tagWithClass("a", "removedata"));
        Locator statsRow = trainingTable.descendant(Locator.tagWithClass("tr", "stats-row"));

        // Initial state: no run has been added to the training set yet, so
        // the clean run's date should not appear in the training table and
        // no Remove link should be present.
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Training Data"));
        assertElementNotPresent(removeLink);
        assertElementNotPresent(trainingTable.containing("2026-01-16 06:00"));
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2, "No Training Data --");

        // Add the clean run (posted by COMPUTER_NAME_1) to the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Add to training set");
        toggleTrainingSet();
        assertTextPresent("Remove from training set");

        // Verify the Training Data page now shows the run for COMPUTER_NAME_1.
        // Scope assertions to <table id="trainingdata">
        clickAndWait(Locator.linkWithText("Training Data"));
        assertElementPresent(trainingTable.descendant(
                Locator.tagWithId("tr", "user-anchor-" + COMPUTER_NAME_1)));
        assertElementPresent(trainingTable.containing(COMPUTER_NAME_1));
        assertElementPresent(trainingTable.containing("2026-01-16 06:00"));
        assertElementPresent(removeLink);
        assertElementPresent(statsRow);
        assertElementPresent(statsRow.containing("RunCount:1"));
        // The other computer should still be in the "No Training Data" section
        assertTextPresent(COMPUTER_NAME_2, "No Training Data --");

        // Remove the run from the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Remove from training set");
        toggleTrainingSet();
        assertTextPresent("Add to training set");

        // After removing the user's last training run, TrainRunAction deletes their
        // UserData row, so TEST-PC-1 no longer has any training data: its section
        // disappears from <table id="trainingdata"> and the user moves to the
        // "No Training Data --" list (it now behaves like TEST-PC-2). Previously a
        // stale UserData row left a lingering RunCount:0 section here.
        clickAndWait(Locator.linkWithText("Training Data"));
        assertElementNotPresent(removeLink);
        assertElementNotPresent(trainingTable.containing("2026-01-16 06:00"));
        assertElementNotPresent(trainingTable.descendant(
                Locator.tagWithId("tr", "user-anchor-" + COMPUTER_NAME_1)));
        assertElementNotPresent(statsRow);
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2, "No Training Data --");
    }

    @Test
    public void testViewLog()
    {
        // The clean run has a <Log> element — ViewLogAction should return it
        String logContent = getApiString("viewLog", _cleanRunId, "log");
        assertTrue("ViewLog should return log content", logContent != null && !logContent.isEmpty());
        // Spot-check the nightly header and test entries from the beginning,
        // middle, and end of the log (see pc1-run-0115-clean.xml).
        assertTrue("Log should contain nightly header",
                logContent.contains("# Nightly started Thursday, January 15, 2026 9:00 PM"));
        assertTrue("Log should contain TestAlpha", logContent.contains("TestAlpha"));
        assertTrue("Log should contain TestBeta", logContent.contains("TestBeta"));
        assertTrue("Log should contain TestEpsilon", logContent.contains("TestEpsilon"));
        assertTrue("Log should contain Test075", logContent.contains("Test075"));
        assertTrue("Log should contain Test150", logContent.contains("Test150"));
    }

    @Test
    public void testViewXml()
    {
        // ViewXmlAction should return the stored XML (without the <Log> element)
        String xmlContent = getApiString("viewXml", _cleanRunId, "xml");
        assertTrue("ViewXml should return XML content", xmlContent != null && !xmlContent.isEmpty());
        assertTrue("XML should contain nightly element", xmlContent.contains("nightly"));
        assertTrue("XML should contain test data", xmlContent.contains("TestAlpha"));
        assertFalse("XML should not contain Log element (stripped before storage)", xmlContent.contains("<Log>"));
    }

    @Test
    public void testViewLogPopup()
    {
        // Verify the popup actually displays the stored log.
        navigateToRunById(_cleanRunId);
        click(Locator.lkButton("View Log"));
        switchToWindow(1);
        try
        {
            waitForElement(Locator.tag("pre"));
            String popupText = getText(Locator.tag("pre"));
            assertTrue("Log popup should show the nightly header, was: " + popupText,
                    popupText.contains("# Nightly started Thursday, January 15, 2026 9:00 PM"));
            assertTrue("Log popup should show test entries", popupText.contains("TestAlpha"));
        }
        finally
        {
            closeExtraWindows();
            switchToMainWindow();
        }
    }

    @Test
    public void testChangeBoundaries()
    {
        // Navigate to Training Data page and select the Error/Warning edits action
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Training Data"));
        selectOptionByValue(Locator.id("actionform"), "error");
        waitForElement(Locator.id("warningb"));

        // Set warning and error boundaries to custom values
        setFormElement(Locator.id("warningb"), "2");
        setFormElement(Locator.id("errorb"), "3");
        click(Locator.id("submit-button"));
        waitForText("success!");

        // Empty warning boundary → server rejects (parsed as null Integer)
        setFormElement(Locator.id("warningb"), "");
        setFormElement(Locator.id("errorb"), "3");
        click(Locator.id("submit-button"));
        waitForText("fail: warning boundary must be a number");

        // Empty error boundary → server rejects (parsed as null Integer)
        setFormElement(Locator.id("warningb"), "2");
        setFormElement(Locator.id("errorb"), "");
        click(Locator.id("submit-button"));
        waitForText("fail: error boundary must be a number");

        // Set back to defaults
        setFormElement(Locator.id("warningb"), "1");
        setFormElement(Locator.id("errorb"), "2");
        click(Locator.id("submit-button"));
        waitForText("success!");
    }

    @Test
    public void testSetUserActive()
    {
        // Add the clean run to the training set so the user appears with activate/deactivate buttons.
        // The userdata.active column defaults to FALSE, so the user starts inactive.
        navigateToRunById(_cleanRunId);
        toggleTrainingSet();
        assertTextPresent("Remove from training set");

        try
        {
            Locator activateButton = Locator.css("input.activate-user");
            Locator deactivateButton = Locator.css("input.deactivate-user");

            // Navigate to Training Data page — user should have "Activate user" button (inactive by default)
            clickAndWait(Locator.linkWithText("Training Data"));
            assertElementPresent(activateButton);

            // Click to activate — AJAX call followed by location.reload()
            click(activateButton);
            waitForElement(deactivateButton);

            // Click to deactivate — AJAX call followed by location.reload()
            click(deactivateButton);
            waitForElement(activateButton);
        }
        finally
        {
            // Always clean up: remove the run from the training set
            navigateToRunById(_cleanRunId);
            toggleTrainingSet();
            assertTextPresent("Add to training set");
        }
    }

    @Test
    public void testApiErrorResponses()
    {
        // TrainRunAction: missing runId
        JSONObject noRunId = postApi("trainRun", Map.of("train", "true"));
        assertFalse(noRunId.optBoolean("Success", true));
        assertEquals("runId is required", noRunId.optString("error"));

        // TrainRunAction: invalid train value
        JSONObject badTrain = postApi("trainRun",
                Map.of("runId", String.valueOf(_cleanRunId), "train", "garbage"));
        assertFalse(badTrain.optBoolean("Success", true));
        assertEquals("train must be one of: true, false, force", badTrain.optString("error"));

        // TrainRunAction: nonexistent runId
        JSONObject missingRun = postApi("trainRun",
                Map.of("runId", "999999", "train", "true"));
        assertFalse(missingRun.optBoolean("Success", true));
        assertEquals("run does not exist: 999999", missingRun.optString("error"));

        // TrainRunAction: force path also requires the run to exist. The existence check runs
        // before the recompute regardless of force, so a bad runId reports not-found instead of
        // throwing on an empty lookup.
        JSONObject forceMissingRun = postApi("trainRun",
                Map.of("runId", "999999", "train", "force"));
        assertFalse(forceMissingRun.optBoolean("Success", true));
        assertEquals("run does not exist: 999999", forceMissingRun.optString("error"));

        // SetUserActive: missing userId
        JSONObject noUserId = postApi("setUserActive", Map.of("active", "true"));
        assertEquals("userId is required", noUserId.optString("Message"));

        // SetUserActive: missing active
        JSONObject noActive = postApi("setUserActive", Map.of("userId", "1"));
        assertEquals("active parameter is required (true to activate, false to deactivate)",
                noActive.optString("Message"));

        // DeleteRunAction: missing runId
        JSONObject noDeleteRunId = postApi("deleteRun", Map.of());
        assertFalse(noDeleteRunId.optBoolean("Success", true));
        assertEquals("runId is required", noDeleteRunId.optString("error"));

        // FlagRunAction: missing runId
        JSONObject noFlagRunId = postApi("flagRun", Map.of("flag", "true"));
        assertFalse(noFlagRunId.optBoolean("Success", true));
        assertEquals("runId is required", noFlagRunId.optString("error"));
    }

    @Test
    public void testInvalidDateParameters()
    {
        // BeginAction (RunDownForm) — invalid end date
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "begin",
                Map.of("end", "garbage")));
        assertTextPresent("Invalid date format: garbage (expected MM/dd/yyyy)");

        // ShowUserAction — invalid start date
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showUser",
                Map.of("username", COMPUTER_NAME_1, "start", "not-a-date", "end", "01/17/2026")));
        assertTextPresent("Invalid start date format: not-a-date (expected MM/dd/yyyy)");

        // ShowUserAction — invalid end date
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showUser",
                Map.of("username", COMPUTER_NAME_1, "start", "01/15/2026", "end", "bogus")));
        assertTextPresent("Invalid end date format: bogus (expected MM/dd/yyyy)");

        // ShowFailures (ShowFailuresForm) — invalid end date
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showFailures",
                Map.of("end", "03-24-2026")));
        assertTextPresent("Invalid date format: 03-24-2026 (expected MM/dd/yyyy)");

        // Strict parsing: an out-of-range but MM/dd/yyyy-shaped date like 13/45/2026 used to
        // silently roll over to a valid date (02/14/2027) because the shared SimpleDateFormat
        // was lenient. It must now be rejected like any other invalid date.
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "begin",
                Map.of("end", "13/45/2026")));
        assertTextPresent("Invalid date format: 13/45/2026 (expected MM/dd/yyyy)");
    }

    @Test
    public void testDeleteRun()
    {
        // Verify the disposable run exists
        navigateToRunById(_disposableRunId);
        assertTextPresent(COMPUTER_NAME_1, "TestDisposableOne");

        // Delete it via the Delete Run button on the run detail page
        click(Locator.id("deleteRun"));
        acceptAlert();

        // AJAX delete followed by location.reload() — page reloads with deleted runId,
        // showing the "enter run ID" form since the bean is null
        waitForElement(Locator.css("input[name='runId']"));
        assertTextNotPresent("TestDisposableOne");

        // Re-submit the deleted run ID — the form should reappear (bean is
        // still null) and the run's content should still not be visible.
        setFormElement(Locator.name("runId"), String.valueOf(_disposableRunId));
        clickAndWait(SUBMIT_BUTTON);
        assertElementPresent(Locator.css("input[name='runId']"));
        assertTextNotPresent("TestDisposableOne");
    }

    @Test
    public void testDeleteRunWithChildRecordsRecomputesTraining()
    {
        // Post a fresh run that has both handle-leak and memory-leak child rows, then add it
        // to the training set. Deleting it must (1) succeed despite the handleleaks and
        // trainruns child rows that previously caused a foreign key violation, and (2) refresh
        // the user's training stats so no stale UserData row is left behind.
        int baselineUserData = userDataRowCount();

        int runId = postAndGetNewRunId("testresults/pc1-run-0117-leaks.xml", COMPUTER_NAME_1);
        assertTrue("Posted run should have handle-leak child rows", childRowCount("handleleaks", runId) > 0);
        assertTrue("Posted run should have memory-leak child rows", childRowCount("memoryleaks", runId) > 0);

        JSONObject trainResp = postApi("trainRun", Map.of("runId", String.valueOf(runId), "train", "true"));
        assertTrue("trainRun should succeed: " + trainResp, trainResp.optBoolean("Success", false));
        assertTrue("A UserData row should exist after adding a training run", userDataRowCount() >= 1);

        // Without the fix this returned Success=false with a foreign key violation on
        // handleleaks (and would also fail for the trainruns row).
        JSONObject deleteResp = postApi("deleteRun", Map.of("runId", String.valueOf(runId)));
        assertTrue("deleteRun should succeed without a foreign key violation: " + deleteResp,
                deleteResp.optBoolean("Success", false));

        // Child rows are cleaned up, and the training stats are refreshed: this was the run's
        // only training run, so its UserData row is removed rather than left stale.
        assertEquals("handleleaks child rows should be deleted", 0, childRowCount("handleleaks", runId));
        assertEquals("memoryleaks child rows should be deleted", 0, childRowCount("memoryleaks", runId));
        assertEquals("testpasses child rows should be deleted", 0, childRowCount("testpasses", runId));
        assertEquals("Deleting the only training run should remove its UserData row",
                baselineUserData, userDataRowCount());
    }

    @Test
    public void testRemoveTrainingRunRecomputesStatsWhenRunsRemain()
    {
        // Covers the recomputeUserData "update" branch: when a user still has training runs
        // after one is removed, their UserData row must survive (not be deleted) and its stats
        // must be recomputed from the remaining runs. The other tests only exercise the
        // "delete" branch (removing a user's only/last training run).
        int userId = getUserId(COMPUTER_NAME_1);
        int baselineUserData = userDataRowCount();
        double cleanMem = runAverageMem(_cleanRunId);
        double leakMem = runAverageMem(_leakRunId);

        try
        {
            // Add two TEST-PC-1 runs to the training set; both share one UserData row whose
            // mean memory is the average of the two runs.
            assertTrainRun(_cleanRunId, "true");
            assertTrainRun(_leakRunId, "true");
            assertEquals("Both training runs should share one UserData row",
                    baselineUserData + 1, userDataRowCount());
            assertEquals("Mean memory should be the average of both training runs",
                    (cleanMem + leakMem) / 2, userDataMeanMemory(userId), EPSILON);
            // Population stddev of two values {a, b} is |a - b| / 2.
            assertEquals("Stddev memory should reflect both training runs",
                    Math.abs(cleanMem - leakMem) / 2, userDataStdDevMemory(userId), EPSILON);

            // Remove one run: the row must remain (update branch, not delete) and its stats
            // must be recomputed from the single remaining run.
            assertTrainRun(_leakRunId, "false");
            assertEquals("Removing one of two training runs must not delete the UserData row",
                    baselineUserData + 1, userDataRowCount());
            assertEquals("Mean memory should be recomputed from the remaining run",
                    cleanMem, userDataMeanMemory(userId), EPSILON);
            assertEquals("Stddev memory of a single remaining run should be zero",
                    0.0, userDataStdDevMemory(userId), EPSILON);
        }
        finally
        {
            // Leave the training set empty for other tests (no-op if already removed).
            postApi("trainRun", Map.of("runId", String.valueOf(_cleanRunId), "train", "false"));
            postApi("trainRun", Map.of("runId", String.valueOf(_leakRunId), "train", "false"));
        }
    }

    @Test
    public void testRunAccessIsContainerScoped() throws IOException, CommandException
    {
        // Run ids are global, so the actions that look a run up by id must reject a run that lives
        // in another folder. Put a run in a subfolder, then from the PARENT folder confirm that
        // every run-by-id action refuses it, and that the run is still reachable from its own folder.
        final String subFolder = "CrossFolderAccessTest";
        final String subFolderPath = "/" + PROJECT_NAME + "/" + subFolder;
        _containerHelper.createSubfolder(PROJECT_NAME, subFolder);
        _containerHelper.enableModule(subFolderPath, "TestResults");
        postSampleXml("testresults/pc1-run-0116-failures.xml", subFolderPath);

        Connection conn = WebTestHelper.getRemoteApiConnection();
        SelectRowsCommand runsCmd = new SelectRowsCommand("testresults", "testruns");
        runsCmd.setColumns(List.of("id"));
        SelectRowsResponse subRuns = runsCmd.execute(conn, subFolderPath);
        assertEquals("Subfolder should have exactly 1 run", 1, subRuns.getRows().size());
        int subRunId = ((Number) subRuns.getRows().getFirst().get("id")).intValue();

        // --- Negative: act on the subfolder's run by id from the PARENT folder (PROJECT_NAME) ---

        JSONObject del = postApi("deleteRun", Map.of("runId", String.valueOf(subRunId)));
        assertFalse("Cross-folder deleteRun must fail: " + del, del.optBoolean("Success", true));
        assertEquals("run does not exist: " + subRunId, del.optString("error"));

        JSONObject train = postApi("trainRun", Map.of("runId", String.valueOf(subRunId), "train", "true"));
        assertFalse("Cross-folder trainRun must fail: " + train, train.optBoolean("Success", true));
        assertEquals("run does not exist: " + subRunId, train.optString("error"));

        JSONObject flag = postApi("flagRun", Map.of("runId", String.valueOf(subRunId), "flag", "true"));
        assertFalse("Cross-folder flagRun must fail: " + flag, flag.optBoolean("Success", true));
        assertEquals("run not found: " + subRunId, flag.optString("error"));

        assertNull("Cross-folder viewLog must not return content",
                getApiString("viewLog", subRunId, "log"));
        assertNull("Cross-folder viewXml must not return content",
                getApiString("viewXml", subRunId, "xml"));

        // showRun in the parent folder shows the "enter run ID" prompt, not the subfolder run.
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showRun",
                Map.of("runId", String.valueOf(subRunId))));
        assertElementPresent(Locator.css("input[name='runId']"));

        // The run is untouched in its own folder (the cross-folder delete was a no-op there).
        assertEquals("Subfolder run must be untouched", 1,
                runsCmd.execute(conn, subFolderPath).getRows().size());

        // --- Positive: the same run IS reachable from its own folder ---
        // (deleteRun/trainRun/viewLog/viewXml in-folder are covered by the other tests, which all
        // run in PROJECT_NAME and would fail if the container guard rejected same-folder access.)
        JSONObject flagOwn = postApi("flagRun",
                Map.of("runId", String.valueOf(subRunId), "flag", "true"), subFolderPath);
        assertTrue("In-folder flagRun should succeed: " + flagOwn, flagOwn.optBoolean("Success", false));

        // ShowFlaggedAction is folder-scoped: the flagged subfolder run shows on the subfolder's
        // Flags page but not the parent's.
        beginAt(WebTestHelper.buildRelativeUrl("testresults", subFolderPath, "showFlagged"));
        assertElementPresent(Locator.tag("a").startsWith("id: " + subRunId + " /"));
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showFlagged"));
        assertElementNotPresent(Locator.tag("a").startsWith("id: " + subRunId + " /"));
    }

    /**
     * Verifies that child tables in the testresults schema are container-filtered
     * via a join to testruns. Creates a subfolder, posts a single run into it,
     * then checks that testpasses, testfails, trainruns, and user queries from
     * each container only return rows for runs in that container.
     *
     * Not separately tested: hangs, memoryleaks, handleleaks. These go through the
     * same {@code createRunChildTable} helper as testpasses/testfails, so verifying
     * those covers them by construction.
     */
    @Test
    public void testContainerFiltering() throws IOException, CommandException
    {
        final String subFolder = "ContainerFilterTest";
        final String subFolderPath = "/" + PROJECT_NAME + "/" + subFolder;
        _containerHelper.createSubfolder(PROJECT_NAME, subFolder);
        _containerHelper.enableModule(subFolderPath, "TestResults");
        // pc1-run-0116-failures.xml: 150 tests (testsrun=150), 2 failures (TestFailOne, TestFailTwo).
        postSampleXml("testresults/pc1-run-0116-failures.xml", subFolderPath);

        Connection conn = WebTestHelper.getRemoteApiConnection();

        // The subfolder has exactly one run (the one we just posted). Fetch its ID.
        SelectRowsCommand runsCmd = new SelectRowsCommand("testresults", "testruns");
        runsCmd.setColumns(List.of("id", "userid/username"));
        SelectRowsResponse subRuns = runsCmd.execute(conn, subFolderPath);
        assertEquals("Subfolder should have exactly 1 run", 1, subRuns.getRows().size());
        int subRunId = ((Number) subRuns.getRows().getFirst().get("id")).intValue();

        // testpasses: 150 rows, all referencing subRunId. None visible from parent.
        assertTableContainerFiltered(conn, "testpasses", "testrunid", subFolderPath, subRunId, 150);

        // testfails: 2 failures in the posted XML.
        assertTableContainerFiltered(conn, "testfails", "testrunid", subFolderPath, subRunId, 2);

        // Add the subfolder's run to the training set, then verify trainruns filtering.
        JSONObject trainResp = postApi("trainRun",
                Map.of("runId", String.valueOf(subRunId), "train", "true"),
                subFolderPath);
        assertTrue("Adding run to training set failed: " + trainResp, trainResp.optBoolean("Success", false));

        assertTableContainerFiltered(conn, "trainruns", "runid", subFolderPath, subRunId, 1);

        // user: the subfolder's only run is for TEST-PC-1, so only that user should
        // be visible there. The parent has both TEST-PC-1 and TEST-PC-2.
        SelectRowsCommand userCmd = new SelectRowsCommand("testresults", "user");
        userCmd.setColumns(List.of("username"));
        SelectRowsResponse subUsers = userCmd.execute(conn, subFolderPath);
        assertEquals("Subfolder should see only TEST-PC-1", 1, subUsers.getRows().size());
        assertEquals(COMPUTER_NAME_1, subUsers.getRows().getFirst().get("username"));

        SelectRowsResponse parentUsers = userCmd.execute(conn, PROJECT_NAME);
        List<String> parentUsernames = parentUsers.getRows().stream()
                .map(r -> (String) r.get("username")).toList();
        assertTrue("Parent should see TEST-PC-1", parentUsernames.contains(COMPUTER_NAME_1));
        assertTrue("Parent should see TEST-PC-2", parentUsernames.contains(COMPUTER_NAME_2));

        // The SelectRows checks above hit the "user" query table (the FilteredTable from
        // TestResultsSchema.createUserTable) via the query API. The User-page dropdown is a
        // separate code path - built by TestResultsController.getUsers() - so verify it is
        // folder-scoped too: the subfolder lists only TEST-PC-1, the parent lists both.
        assertUserDropdownContains(subFolderPath, List.of(COMPUTER_NAME_1), List.of(COMPUTER_NAME_2));
        assertUserDropdownContains("/" + PROJECT_NAME, List.of(COMPUTER_NAME_1, COMPUTER_NAME_2), List.of());
    }

    /**
     * Navigates to the User page (ShowUserAction) in the given folder and asserts the computer
     * dropdown - populated by TestResultsController.getUsers() - lists the expected computers.
     * This exercises the server-rendered getUsers() path, separate from the query "user" table.
     */
    private void assertUserDropdownContains(String containerPath, List<String> present, List<String> absent)
    {
        beginAt(WebTestHelper.buildRelativeUrl("testresults", containerPath, "showUser"));
        for (String name : present)
            assertElementPresent(Locator.xpath("//select[@id='users']/option[@value='" + name + "']"));
        for (String name : absent)
            assertElementNotPresent(Locator.xpath("//select[@id='users']/option[@value='" + name + "']"));
    }

    /**
     * Asserts that {@code tableName} is container-filtered: the subfolder query
     * returns exactly {@code expectedSubfolderRows} rows, all referencing
     * {@code subRunId}; the parent query returns no rows referencing
     * {@code subRunId}.
     */
    private void assertTableContainerFiltered(Connection conn, String tableName, String fkColumn,
                                              String subFolderPath, int subRunId, int expectedSubfolderRows)
            throws IOException, CommandException
    {
        SelectRowsCommand cmd = new SelectRowsCommand("testresults", tableName);
        cmd.setColumns(List.of(fkColumn));

        SelectRowsResponse subfolderRows = cmd.execute(conn, subFolderPath);
        assertEquals(tableName + " row count in subfolder",
                expectedSubfolderRows, subfolderRows.getRows().size());
        for (Map<String, Object> row : subfolderRows.getRows())
            assertEquals("All " + tableName + " rows in subfolder must reference subRunId",
                    subRunId, ((Number) row.get(fkColumn)).intValue());

        SelectRowsResponse parentRows = cmd.execute(conn, PROJECT_NAME);
        for (Map<String, Object> row : parentRows.getRows())
            assertNotEquals("Parent " + tableName + " must not reference subfolder's run " + subRunId,
                    subRunId, ((Number) row.get(fkColumn)).intValue());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Navigates to a run detail page via the Run tab by entering the run ID
     * in the form and clicking Submit.
     */
    private void navigateToRunById(int runId)
    {
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Run"));
        setFormElement(Locator.name("runId"), String.valueOf(runId));
        clickAndWait(SUBMIT_BUTTON);
    }

    /**
     * Clicks the flag toggle image on the run detail page, accepts the confirmation
     * dialog, and waits for the page to reload.
     */
    private void toggleRunFlag()
    {
        Locator.XPathLocator flagImage = Locator.tag("img").withAttribute("id", "flagged");
        boolean wasFlagged = getAttribute(flagImage, "title").contains("unflag");
        click(flagImage);
        acceptAlert();
        // Wait for the page to reload with the toggled flag state
        String expectedTitle = wasFlagged ? "Click to flag run" : "Click to unflag run";
        waitForElement(flagImage.withAttribute("title", expectedTitle));
    }

    /**
     * Clicks the "Add to training set" / "Remove from training set" link on the
     * run detail page and waits for the page to reload.
     */
    private void toggleTrainingSet()
    {
        Locator trainLink = Locator.id("trainset");
        String expectedText = getText(trainLink).contains("Add") ? "Remove from training set" : "Add to training set";
        click(trainLink);
        waitForText(expectedText);
    }

    /**
     * Navigates to the user page, selects the test user, and sets the date range
     * covering all sample runs. End date is 01/19 because ShowUserAction uses
     * DateUtils.ceiling (midnight), and runs post at 6:00 AM.
     */
    private void navigateToUserPageWithDateRange()
    {
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("User"));
        Locator usersSelect = Locator.id("users");
        doAndWaitForPageToLoad(() -> selectOptionByValue(usersSelect, COMPUTER_NAME_1));
        setDateRange("01/15/2026", "01/19/2026");
    }

    /**
     * Sets the date range on the user page by typing into the multi-date range
     * picker input and clicking "Done". The Done button triggers paramRedirect()
     * which navigates to the page with the new date range.
     */
    private void setDateRange(String startDate, String endDate)
    {
        Locator dateInput = Locator.css("#jrange input");
        setFormElement(dateInput, startDate + " - " + endDate);

        // Focus the input to open the datepicker, then click Done
        click(dateInput);
        waitForElement(Locator.tagWithClass("button", "ui-datepicker-close"));
        clickAndWait(Locator.tagWithClass("button", "ui-datepicker-close"));
    }

    /**
     * Asserts that the problems matrix table is present and its header contains
     * columns for both expected computers.
     */
    private void assertProblemsMatrixPresent(String... computerNames)
    {
        assertElementPresent(Locator.xpath(PROBLEMS_TABLE_XPATH));
        for (String name : computerNames)
        {
            assertElementPresent(Locator.xpath(PROBLEMS_TABLE_XPATH +
                    "//thead//a[contains(text(),'" + name + "')]"));
        }
    }

    /**
     * Asserts that a test's row in the problems matrix has the expected number
     * of icons (e.g. fail.png or leak.png). This verifies which computers are
     * affected: 2 icons means both PCs, 1 icon means only one PC.
     */
    private void assertProblemIconCount(String testName, String iconFile, int expectedCount)
    {
        Locator icons = Locator.xpath(PROBLEMS_TABLE_XPATH +
                "//tr[.//a[text()='" + testName + "']]//img[contains(@src,'" + iconFile + "')]");
        assertEquals("Expected " + expectedCount + " " + iconFile + " icon(s) for " + testName,
                expectedCount, icons.findElements(getDriver()).size());
    }

    /**
     * Asserts that a test name appears in the specified summary table ("Top Failures"
     * or "Top Leaks") with the expected occurrence count.
     */
    private void assertTopSummaryEntry(String tableHeader, String testName, int expectedOccurrences)
    {
        Locator occurrenceTd = Locator.xpath(
                "//table[contains(@class,'decoratedtable')][.//h4[text()='" + tableHeader + "']]" +
                "//tr[.//a[text()='" + testName + "']]/td[2]");
        assertEquals(tableHeader + " occurrence count for " + testName,
                String.valueOf(expectedOccurrences), getText(occurrenceTd).trim());
    }

    /**
     * Asserts that the "Mean Leak" column in the Top Leaks table contains the
     * expected value (e.g. "3 kb" or "5 handles") for a given test.
     */
    private void assertTopLeakMean(String testName, String expectedMeanLeak)
    {
        Locator meanLeakTd = Locator.xpath(
                "//table[contains(@class,'decoratedtable')][.//h4[text()='Top Leaks']]" +
                "//tr[.//a[text()='" + testName + "']]/td[3]");
        String actual = getText(meanLeakTd).trim();
        assertTrue("Mean leak for " + testName + " should contain '" + expectedMeanLeak + "' but was '" + actual + "'",
                actual.contains(expectedMeanLeak));
    }

    /**
     * Asserts that the test names appear in the expected order within the test passes
     * table (the "decoratedtable" whose first cell contains "Test | Sort by:").
     */
    private void assertTestPassesSortedAs(String... expectedTestNames)
    {
        Locator testPassesTable = Locator.xpath(
                "//table[contains(@class,'decoratedtable')]" +
                "[.//tr[1]/td[1][contains(text(),'Test | Sort by:')]]");
        String tableText = getText(testPassesTable);
        assertTextPresentInThisOrder(new TextSearcher(tableText), expectedTestNames);
    }

    /**
     * Opens the jQuery UI datepicker on the begin page and verifies it is displaying
     * the expected month, year, and selected day.
     */
    private void verifyDateInDatepicker(int month, int day, int year)
    {
        String expected = String.format("%02d/%02d/%04d", month, day, year);
        assertEquals("Datepicker date", expected, getFormElement(Locator.id("datepicker")));
    }

    // The "<<<" and ">>>" links are element siblings of the #datepicker input
    // (a previous-day link before and a next-day link after).
    private static final Locator PREV_DAY_LINK = Locator.xpath(
            "//a[normalize-space(text())='<<<' and following-sibling::input[@id='datepicker']]");
    private static final Locator NEXT_DAY_LINK = Locator.xpath(
            "//a[normalize-space(text())='>>>' and preceding-sibling::input[@id='datepicker']]");

    /**
     * Clicks the ">>>" link next to the date field {@code count} times to advance
     * one day per click. Each click triggers a page navigation.
     */
    private void goToNextDay(int count)
    {
        for (int i = 0; i < count; i++)
            clickAndWait(NEXT_DAY_LINK);
    }

    /**
     * Clicks the "<<<" link next to the date field {@code count} times to go back
     * one day per click. Each click triggers a page navigation.
     */
    private void goToPrevDay(int count)
    {
        for (int i = 0; i < count; i++)
            clickAndWait(PREV_DAY_LINK);
    }

    /**
     * Makes an API GET request to a testresults action and returns the value
     * of the specified field from the JSON response.
     */
    private String getApiString(String action, int runId, String field)
    {
        String url = WebTestHelper.buildURL("testresults", PROJECT_NAME, action) + "?runId=" + runId;
        try (CloseableHttpClient httpClient = WebTestHelper.getHttpClient())
        {
            HttpGet request = new HttpGet(url);
            APITestHelper.injectCookies(request);
            return httpClient.execute(request, response -> {
                JSONObject json = new JSONObject(EntityUtils.toString(response.getEntity()));
                return json.optString(field, null);
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException("API call failed: " + action, e);
        }
    }

    /**
     * Makes an API POST request with the given query-string parameters and returns
     * the parsed JSON response. Used to exercise MutatingApiAction error paths.
     */
    private JSONObject postApi(String action, Map<String, String> params)
    {
        return postApi(action, params, PROJECT_NAME);
    }

    private JSONObject postApi(String action, Map<String, String> params, String containerPath)
    {
        StringBuilder url = new StringBuilder(WebTestHelper.buildURL("testresults", containerPath, action));
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet())
        {
            url.append(first ? '?' : '&').append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        try (CloseableHttpClient httpClient = WebTestHelper.getHttpClient())
        {
            HttpPost request = new HttpPost(url.toString());
            APITestHelper.injectCookies(request);
            return httpClient.execute(request, response ->
                    new JSONObject(EntityUtils.toString(response.getEntity())));
        }
        catch (Exception e)
        {
            throw new RuntimeException("API call failed: " + action, e);
        }
    }

    /**
     * Posts a sample XML run and returns the id of the run it created, identified as the
     * single new testruns row for the given computer (compared against the runs present
     * before the post).
     */
    private int postAndGetNewRunId(String sampleDataRelativePath, String computerName)
    {
        Set<Integer> before = runIdsForComputer(computerName);
        postSampleXml(sampleDataRelativePath);
        Set<Integer> after = runIdsForComputer(computerName);
        after.removeAll(before);
        assertEquals("Expected exactly one new run for " + computerName, 1, after.size());
        return after.iterator().next();
    }

    /**
     * Returns the set of testruns ids posted by the given computer in this container.
     */
    private Set<Integer> runIdsForComputer(String computerName)
    {
        return queryRuns().stream()
                .filter(r -> computerName.equals(r.get("userid/username")))
                .map(r -> (Integer) r.get("id"))
                .collect(Collectors.toSet());
    }

    /**
     * Counts rows in a testresults child table that reference the given run via testrunid.
     */
    private int childRowCount(String table, int runId)
    {
        try
        {
            Connection connection = WebTestHelper.getRemoteApiConnection();
            SelectRowsCommand cmd = new SelectRowsCommand("testresults", table);
            cmd.addFilter(new Filter("testrunid", runId));
            return cmd.execute(connection, PROJECT_NAME).getRows().size();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to count rows in " + table + " for run " + runId, e);
        }
    }

    /**
     * Counts UserData (training stats) rows in this container.
     */
    private int userDataRowCount()
    {
        try
        {
            Connection connection = WebTestHelper.getRemoteApiConnection();
            SelectRowsCommand cmd = new SelectRowsCommand("testresults", "userdata");
            return cmd.execute(connection, PROJECT_NAME).getRows().size();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to count userdata rows", e);
        }
    }

    /**
     * Posts trainRun for the given run and asserts it succeeded. {@code train} is "true" to add
     * to the training set or "false" to remove.
     */
    private void assertTrainRun(int runId, String train)
    {
        JSONObject resp = postApi("trainRun", Map.of("runId", String.valueOf(runId), "train", train));
        assertTrue("trainRun(" + runId + ", " + train + ") should succeed: " + resp,
                resp.optBoolean("Success", false));
    }

    /**
     * Returns the testresults.user id for the given computer name.
     */
    private int getUserId(String computerName)
    {
        List<Map<String, Object>> rows = selectRows("user",
                new Filter("username", computerName), "id");
        assertEquals("Expected exactly one user row for " + computerName, 1, rows.size());
        return ((Number) rows.get(0).get("id")).intValue();
    }

    /**
     * Returns the stored average managed memory for a run.
     */
    private double runAverageMem(int runId)
    {
        List<Map<String, Object>> rows = selectRows("testruns",
                new Filter("id", runId), "averagemem");
        assertEquals("Expected exactly one testruns row for run " + runId, 1, rows.size());
        return ((Number) rows.get(0).get("averagemem")).doubleValue();
    }

    /**
     * Returns the recomputed mean memory from the single UserData row for the given user.
     */
    private double userDataMeanMemory(int userId)
    {
        List<Map<String, Object>> rows = selectRows("userdata",
                new Filter("userid", userId), "meanmemory");
        assertEquals("Expected exactly one userdata row for user " + userId, 1, rows.size());
        return ((Number) rows.get(0).get("meanmemory")).doubleValue();
    }

    /**
     * Returns the recomputed population stddev of memory from the single UserData row for the
     * given user.
     */
    private double userDataStdDevMemory(int userId)
    {
        List<Map<String, Object>> rows = selectRows("userdata",
                new Filter("userid", userId), "stddevmemory");
        assertEquals("Expected exactly one userdata row for user " + userId, 1, rows.size());
        return ((Number) rows.get(0).get("stddevmemory")).doubleValue();
    }

    /**
     * Runs a filtered SelectRows against a testresults query, returning the selected columns.
     */
    private List<Map<String, Object>> selectRows(String queryName, Filter filter, String... columns)
    {
        try
        {
            Connection connection = WebTestHelper.getRemoteApiConnection();
            SelectRowsCommand cmd = new SelectRowsCommand("testresults", queryName);
            cmd.addFilter(filter);
            cmd.setColumns(List.of(columns));
            return cmd.execute(connection, PROJECT_NAME).getRows();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to query " + queryName, e);
        }
    }

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        new APIContainerHelper(this).deleteProject(PROJECT_NAME, afterTest);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("testresults");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
