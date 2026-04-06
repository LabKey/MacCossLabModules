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
import org.labkey.remoteapi.Connection;
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
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.TextSearcher;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class TestResultsTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static final String PROJECT_NAME = "TestResultsTest" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    static final String COMPUTER_NAME_1 = "TEST-PC-1";
    static final String COMPUTER_NAME_2 = "TEST-PC-2";

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
     * Posts a sample XML file to PostAction.
     */
    private void postSampleXml(String sampleDataRelativePath)
    {
        File xmlFile = TestFileUtils.getSampleData(sampleDataRelativePath);
        String postUrl = WebTestHelper.buildURL("testresults", PROJECT_NAME, "post");

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

    private static final Locator SUBMIT_BUTTON = Locator.css("input[type='submit'][value='Submit']");
    // XPath for the problems matrix table (header cell contains "Fail: | Leak: | Hang:")
    private static final String PROBLEMS_TABLE_XPATH =
            "//table[contains(@class,'decoratedtable')]" +
            "[.//td[contains(.,'Fail:') and contains(.,'Leak:') and contains(.,'Hang:')]]";

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
        // Navigate to the failures run via the Run tab
        navigateToRunById(_failRunId);

        // Click the failure test name link on the run detail page
        clickAndWait(Locator.linkWithText("TestFailOne"));
        assertTextPresent("TestFailOne");

        // Verify the view type selector and switch views
        Locator viewTypeSelect = Locator.id("view-type-combobox");
        assertEquals("Week", getSelectedOptionText(viewTypeSelect));

        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "mo"));
        assertEquals("mo", getUrlParam("viewType"));
        assertEquals("Month", getSelectedOptionText(viewTypeSelect));
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

        // Verify the Flags page now shows the flagged run
        clickAndWait(Locator.linkWithText("Flags"));
        assertTextNotPresent("There are currently no flagged runs.");
        assertTextPresent("Flagged Runs");

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
        // Navigate to Training Data page — no runs in training set yet
        goToProjectHome(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME_1, COMPUTER_NAME_2, "No Training Data");

        // Add the clean run to the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Add to training set");
        toggleTrainingSet();
        assertTextPresent("Remove from training set");

        // Verify the Training Data page now shows the run
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME_1);
        assertElementPresent(Locator.css("#trainingdata .removedata"));

        // Remove the run from the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Remove from training set");
        toggleTrainingSet();
        assertTextPresent("Add to training set");

        // Verify the Training Data page no longer shows training runs
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME_1, "No Training Data");
    }

    @Test
    public void testViewLog()
    {
        // The clean run has a <Log> element — ViewLogAction should return it
        String logContent = getApiString("testresults", "viewLog", _cleanRunId, "log");
        assertTrue("ViewLog should return log content", logContent != null && !logContent.isEmpty());
        assertTrue("Log should contain test names", logContent.contains("TestAlpha"));
    }

    @Test
    public void testViewXml()
    {
        // ViewXmlAction should return the stored XML (without the <Log> element)
        String xmlContent = getApiString("testresults", "viewXml", _cleanRunId, "xml");
        assertTrue("ViewXml should return XML content", xmlContent != null && !xmlContent.isEmpty());
        assertTrue("XML should contain nightly element", xmlContent.contains("nightly"));
        assertTrue("XML should contain test data", xmlContent.contains("TestAlpha"));
        assertTrue("XML should not contain Log element (stripped before storage)", !xmlContent.contains("<Log>"));
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
        Locator flagImage = Locator.id("flagged");
        boolean wasFlagged = getAttribute(flagImage, "title").contains("unflag");
        click(flagImage);
        acceptAlert();
        // Wait for the page to reload with the toggled flag state
        String expectedTitle = wasFlagged ? "Click to flag run" : "Click to unflag run";
        waitForElement(Locator.xpath("//img[@id='flagged'][@title='" + expectedTitle + "']"));
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
                expectedCount, getElementCount(icons));
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
     * Makes an API GET request and returns the value of the specified field from the JSON response.
     */
    private String getApiString(String controller, String action, int runId, String field)
    {
        String url = WebTestHelper.buildURL(controller, PROJECT_NAME, action) + "?runId=" + runId;
        try (CloseableHttpClient httpClient = WebTestHelper.getHttpClient())
        {
            var request = new org.apache.hc.client5.http.classic.methods.HttpGet(url);
            APITestHelper.injectCookies(request);
            return httpClient.execute(request, response -> {
                String body = EntityUtils.toString(response.getEntity());
                org.json.JSONObject json = new org.json.JSONObject(body);
                return json.optString(field, null);
            });
        }
        catch (Exception e)
        {
            throw new RuntimeException("API call failed: " + action, e);
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
