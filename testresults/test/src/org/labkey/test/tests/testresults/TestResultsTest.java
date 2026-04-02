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

        postXmlFixture("testresults/clean-run.xml");
        postXmlFixture("testresults/run-with-failures.xml");
        postXmlFixture("testresults/run-with-leaks.xml");

        // All runs in this fresh container are our fixtures, sorted by posttime ascending
        List<Map<String, Object>> runs = queryRuns();
        assertEquals("Expected 3 posted runs", 3, runs.size());
        _cleanRunId = (Integer) runs.get(0).get("id");
        _failRunId  = (Integer) runs.get(1).get("id");
        _leakRunId  = (Integer) runs.get(2).get("id");
    }

    /**
     * Posts an XML fixture file to PostAction.
     */
    private void postXmlFixture(String sampleDataRelativePath)
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
            throw new RuntimeException("Failed to post XML fixture: " + sampleDataRelativePath, e);
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

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testBeginPage()
    {
        // Navigate to the default begin page
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "begin"));
        checkErrors();

        // Use the datepicker to navigate to a date with fixture data (01/18/2026)
        selectDateInDatepicker(1, 18, 2026);
        checkErrors();
        assertTextPresent(COMPUTER_NAME);

        // Verify viewType selector defaults to Month
        Locator viewTypeSelect = Locator.id("viewType");
        assertEquals("Default viewType should be Month", "Month", getSelectedOptionText(viewTypeSelect));

        // Select Week — verify URL parameter and selector state after page reload
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "wk"));
        checkErrors();
        assertEquals("wk", getUrlParam("viewType"));
        assertEquals("Week", getSelectedOptionText(viewTypeSelect));

        // Select Year
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "yr"));
        checkErrors();
        assertEquals("yr", getUrlParam("viewType"));
        assertEquals("Year", getSelectedOptionText(viewTypeSelect));

        // Select back to Month
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "mo"));
        checkErrors();
        assertEquals("mo", getUrlParam("viewType"));
        assertEquals("Month", getSelectedOptionText(viewTypeSelect));
    }

    @Test
    public void testShowRunPage()
    {
        // Clean run — shows passes, no failure or leak tables
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showRun",
                Map.of("runId", _cleanRunId)));
        checkErrors();
        assertTextPresent(COMPUTER_NAME, "Passed Tests");

        // filter parameter binding
        for (String filter : List.of("duration", "managed", "total"))
        {
            beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showRun",
                    Map.of("runId", _cleanRunId, "filter", filter)));
            checkErrors();
        }

        // Run with failures — failure table visible
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showRun",
                Map.of("runId", _failRunId)));
        checkErrors();
        assertTextPresent("TestFailOne", "TestFailTwo");

        // Run with leaks — leak table visible
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showRun",
                Map.of("runId", _leakRunId)));
        checkErrors();
        assertTextPresent("TestWithMemoryLeak", "TestWithHandleLeak");
    }

    @Test
    public void testShowUserPage()
    {
        // Navigate to user page without a user, then select from the dropdown
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showUser"));
        checkErrors();

        Locator usersSelect = Locator.id("users");
        doAndWaitForPageToLoad(() -> selectOptionByValue(usersSelect, COMPUTER_NAME));
        checkErrors();
        assertEquals(COMPUTER_NAME, getUrlParam("user", true));
        assertTextPresent(COMPUTER_NAME);

        // With explicit date range covering all three fixtures
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showUser",
                Map.of("user", COMPUTER_NAME, "start", "01/15/2026", "end", "01/18/2026")));
        checkErrors();
        assertTextPresent(COMPUTER_NAME);
    }

    @Test
    public void testLongTermPage()
    {
        for (String viewType : List.of("wk", "mo", "yr"))
        {
            beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "longTerm",
                    Map.of("viewType", viewType)));
            checkErrors();
        }
    }

    @Test
    public void testShowFailuresPage()
    {
        // Failure detail for a known failed test — Month view
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showFailures",
                Map.of("failedTest", "TestFailOne", "viewType", "mo")));
        checkErrors();
        assertTextPresent("TestFailOne");

        // Verify the view type selector shows Month
        Locator viewTypeSelect = Locator.id("view-type-combobox");
        assertEquals("Month", getSelectedOptionText(viewTypeSelect));

        // Switch to Week via the selector and verify
        doAndWaitForPageToLoad(() -> selectOptionByValue(viewTypeSelect, "wk"));
        checkErrors();
        assertEquals("wk", getUrlParam("viewType"));
        assertEquals("Week", getSelectedOptionText(viewTypeSelect));
    }

    @Test
    public void testShowFlaggedPage()
    {
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "showFlagged"));
        checkErrors();
    }

    @Test
    public void testTrainingDataPage()
    {
        beginAt(WebTestHelper.buildRelativeUrl("testresults", PROJECT_NAME, "trainingDataView"));
        checkErrors();
        assertTextPresent(COMPUTER_NAME);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
