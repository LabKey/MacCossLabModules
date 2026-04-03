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
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.TextSearcher;

import java.io.File;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Selenium tests for the testresults module.
 *
 * Covers the main view actions and their URL parameter binding:
 * BeginAction, ShowRunAction, ShowUserAction, LongTermAction, ShowFailures,
 * ShowFlaggedAction, and TrainingDataViewAction.
 *
 * Run before and after the Spring binding refactor to confirm no regressions.
 */
@Category({External.class, MacCossLabModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class TestResultsTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static final String PROJECT_NAME = "TestResultsTest" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    static final String COMPUTER_NAME = "TESTPC-AUTOMATION";
    private static final Locator SUBMIT_BUTTON = Locator.css("input[type='submit'][value='Submit']");

    // Run IDs populated in @BeforeClass, used across test methods
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

        postSampleXml("testresults/clean-run.xml");
        postSampleXml("testresults/run-with-failures.xml");
        postSampleXml("testresults/run-with-leaks.xml");

        // All runs in this fresh container are our sample runs, sorted by posttime ascending
        List<Map<String, Object>> runs = queryRuns();
        assertEquals("Expected 3 posted runs", 3, runs.size());
        _cleanRunId = (Integer) runs.get(0).get("id");
        _failRunId  = (Integer) runs.get(1).get("id");
        _leakRunId  = (Integer) runs.get(2).get("id");
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
                assertTrue("PostAction failed for " + xmlFile.getName() + ": " + body,
                        body.contains("\"Success\" : true"));
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
            cmd.setColumns(List.of("id", "posttime", "passedtests", "failedtests", "leakedtests"));
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
        // Navigate to the begin page via module menu
        goToModule("TestResults");

        // Navigate to 01/16/2026 — the clean run (started 01/15 at 9 PM)
        selectDateInDatepicker(1, 16, 2026);
        assertTextPresent(COMPUTER_NAME);
        assertTextNotPresent("Top Failures");
        assertTextNotPresent("Top Leaks");

        // Click ">>>" to advance to 01/17/2026 — the run with 2 failures
        clickAndWait(Locator.linkWithText(">>>"));
        assertTextPresent(COMPUTER_NAME);
        assertTextPresent("Top Failures");
        assertTextPresent("TestFailOne", "TestFailTwo");
        assertTextNotPresent("Top Leaks");

        // Click ">>>" to advance to 01/18/2026 — the run with 2 leaks
        clickAndWait(Locator.linkWithText(">>>"));
        assertTextPresent(COMPUTER_NAME);
        assertTextPresent("Top Leaks");
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");

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
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 0", "Leaks : 2");
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");

        // Sort by Duration (descending) and verify order in the test passes table
        clickAndWait(Locator.linkWithText("Duration"));
        assertEquals("duration", getUrlParam("filter"));
        assertTestPassesSortedAs("TestWithMemoryLeak", "TestWithHandleLeak", "TestGamma", "TestEpsilon", "TestAlpha");

        // Sort by Managed Memory (descending) and verify order
        clickAndWait(Locator.linkContainingText("Managed Memory"));
        assertEquals("managed", getUrlParam("filter"));
        assertTestPassesSortedAs("TestWithMemoryLeak", "TestWithHandleLeak", "TestGamma", "TestEpsilon", "TestAlpha");

        // Sort by Total Memory (descending) and verify order
        clickAndWait(Locator.linkContainingText("Total Memory"));
        assertEquals("total", getUrlParam("filter"));
        assertTestPassesSortedAs("TestWithMemoryLeak", "TestWithHandleLeak", "TestGamma", "TestEpsilon", "TestAlpha");

        // Navigate to user page again for the failures run
        navigateToUserPageWithDateRange();
        clickAndWait(Locator.linkWithText("run details").index(1));
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 2", "Leaks : 0");
        assertTextPresent("TestFailOne", "TestFailTwo");

        // Navigate to user page again for the clean run
        navigateToUserPageWithDateRange();
        clickAndWait(Locator.linkWithText("run details").index(2));
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 0", "Leaks : 0");
    }

    @Test
    public void testRunLookup()
    {
        // Look up the leaks run
        navigateToRunById(_leakRunId);
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 0", "Leaks : 2");
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");

        // Look up the failures run
        navigateToRunById(_failRunId);
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 2", "Leaks : 0");
        assertTextPresent("TestFailOne", "TestFailTwo");

        // Look up the clean run
        navigateToRunById(_cleanRunId);
        assertTextPresent(COMPUTER_NAME, "Passed Tests : 5", "Failures : 0", "Leaks : 0");
    }

    @Test
    public void testLongTermPage()
    {
        // Navigate to Long Term page via tab click
        goToModule("TestResults");
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
        goToModule("TestResults");
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
        goToModule("TestResults");
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME, "No Training Data");

        // Add the clean run to the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Add to training set");
        toggleTrainingSet();
        assertTextPresent("Remove from training set");

        // Verify the Training Data page now shows the run
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME);
        assertElementPresent(Locator.css("#trainingdata .removedata"));

        // Remove the run from the training set
        navigateToRunById(_cleanRunId);
        assertTextPresent("Remove from training set");
        toggleTrainingSet();
        assertTextPresent("Add to training set");

        // Verify the Training Data page no longer shows training runs
        clickAndWait(Locator.linkWithText("Training Data"));
        assertTextPresent(COMPUTER_NAME, "No Training Data");
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
        goToModule("TestResults");
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
     * covering all three sample runs.
     */
    private void navigateToUserPageWithDateRange()
    {
        goToModule("TestResults");
        clickAndWait(Locator.linkWithText("User"));
        Locator usersSelect = Locator.id("users");
        doAndWaitForPageToLoad(() -> selectOptionByValue(usersSelect, COMPUTER_NAME));
        setDateRange("01/15/2026", "01/18/2026");
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
     * Selects a date in the jQuery UI datepicker on the begin page by clicking
     * through the calendar widget. Navigates backward from the currently displayed
     * month to the target month/year, then clicks the target day. The datepicker's
     * onSelect callback triggers a page navigation.
     */
    private void selectDateInDatepicker(int month, int day, int year)
    {
        click(Locator.id("datepicker"));
        waitForElement(Locator.tagWithClass("div", "ui-datepicker"));

        // Navigate backward to the target month/year
        String targetTitle = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        Locator titleLoc = Locator.tagWithClass("div", "ui-datepicker-title");
        Locator prevButton = Locator.tagWithClass("a", "ui-datepicker-prev");

        for (int i = 0; i < 24 && !getText(titleLoc).contains(targetTitle); i++)
        {
            click(prevButton);
        }

        // Click the target day (exclude days from adjacent months)
        Locator dayLink = Locator.xpath(
                "//div[contains(@class,'ui-datepicker')]" +
                "//td[not(contains(@class,'ui-datepicker-other-month'))]/a[text()='" + day + "']");
        clickAndWait(dayLink);
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
