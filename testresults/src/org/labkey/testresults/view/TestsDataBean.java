/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.testresults.view;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.testresults.TestResultsSchema;
import org.labkey.testresults.model.RunDetail;
import org.labkey.testresults.model.TestLeakDetail;
import org.labkey.testresults.model.TestMemoryLeakDetail;
import org.labkey.testresults.model.User;
import org.labkey.testresults.model.TestFailDetail;
import org.labkey.testresults.model.TestPassDetail;

import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: Yuval Boss, yuval(at)uw.edu
 * Date: 1/14/2015
 */
public class TestsDataBean
{
    private Map<Integer, RunDetail> runs;
    private RunDetail[] statRuns;
    private User[] users;
    private String viewType;
    private Date startDate;
    private Date endDate;
    private Integer boundaryWarning;
    private Integer boundaryError;

    public TestsDataBean(RunDetail[] runs, User[] users) {
        setRuns(runs);
        setUsers(users);
    }

    public TestsDataBean(RunDetail[] runs, User[] users, String viewType, Date startDate, Date endDate) {
        this(runs, users);
        setViewType(viewType);
        setStartDate(startDate);
        setEndDate(endDate);
    }

    public Date getStartDate()
    {
        if(startDate == null)
            startDate = new Date();
        return startDate;
    }

    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    public Date getEndDate()
    {
        if (endDate == null)
            setEndDate(new Date());
        return endDate;
    }

    public void setEndDate(Date day)
    {
        this.endDate = day;
    }

    public String getViewType()
    {
        return viewType;
    }

    public void setViewType(String viewType)
    {
        this.viewType = viewType;
    }

    // Getters and Setters for fields
    public RunDetail[] getRuns() {
        RunDetail[] r = runs.values().toArray(new RunDetail[0]);
        Arrays.sort(r);
        return r;
    }
    public RunDetail[] setStatRuns() {
        List<RunDetail> statRuns = new ArrayList<>();
        for (RunDetail run: runs.values())
            if (!excludeRun(run.getId()))
                statRuns.add(run);
        Collections.sort(statRuns);
        return statRuns.toArray(new RunDetail[0]);
    }

    public RunDetail[] getStatRuns() {
        return statRuns;
    }

    public Map<String, Map<String, Double>> getLanguageBreakdown(Map<String, List<TestFailDetail>> topFailures)
    {
        // Test name -> Language -> Percentage
        Map<String, Map<String, Double>> result = new TreeMap<>();
        Map<String, Double> allTestsBreakdown = new TreeMap<>();

        for (Map.Entry<String, List<TestFailDetail>> failure : topFailures.entrySet())
        {
            String testName = failure.getKey();
            Map<String, Double> testBreakdown = result.computeIfAbsent(testName, k -> new TreeMap<>());

            for (TestFailDetail detail : failure.getValue())
            {
                if (detail == null)
                {
                    continue;
                }

                String language = detail.getLanguage();
                allTestsBreakdown.put(language, allTestsBreakdown.getOrDefault(language, 0d) + 1);
                testBreakdown.put(language, testBreakdown.getOrDefault(language, 0d) + 1);
            }
        }

        if (!allTestsBreakdown.isEmpty())
        {
            result.put("", allTestsBreakdown);
        }

        // Convert counts to percentages.
        for (Map<String, Double> testBreakdown : result.values())
        {
            final Double testTotal = testBreakdown.values().stream().mapToDouble(d -> d).sum();
            testBreakdown.replaceAll((lang, count) -> count / testTotal);
        }

        return result;
    }

    public double round(double value, int places) {
        if (places < 0)
            throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public void setRuns(RunDetail[] runs) {
        this.runs = new LinkedHashMap<>();
        addRuns(runs);
        statRuns = setStatRuns();
    }

    public List<TestLeakDetail> getLeaks() {
        List<TestLeakDetail> leaks = new ArrayList<>();
        for (RunDetail r: runs.values()) {
            leaks.addAll(Arrays.asList(r.getLeaks()));
        }
        return leaks;
    }

    public TestMemoryLeakDetail[] getMemoryLeaks() {
        List<TestMemoryLeakDetail> leaks = new ArrayList<>();
        for (RunDetail r: runs.values()) {
            leaks.addAll(Arrays.asList(r.getMemoryLeaks()));
        }
        return leaks.toArray(new TestMemoryLeakDetail[0]);
    }

    public TestFailDetail[] getFailures() {
        List<TestFailDetail> fails = new ArrayList<>();
        for (RunDetail r: runs.values())
            fails.addAll(Arrays.asList(r.getFailures()));
        return fails.toArray(new TestFailDetail[0]);
    }

    public TestPassDetail[] getPasses() {
        List<TestPassDetail> passes = new ArrayList<>();
        for (RunDetail run : runs.values())
            passes.addAll(Arrays.asList(run.getPasses()));
        return passes.toArray(new TestPassDetail[0]);
    }

    private void addRuns(RunDetail[] runs) {
        if (runs == null)
            return;
        for (RunDetail run : runs) {
            // round all memory data
            TestPassDetail[] passes = run.getPasses();
            if (passes != null && passes.length > 0 && passes[0] != null) {
                for (int i = 0; i < passes.length; i++) {
                    passes[i].setManagedMemory(round(passes[i].getManagedMemory(), 2));
                    passes[i].setTotalMemory(round(passes[i].getTotalMemory(), 2));
                }
                run.setPasses(passes);
            }
            this.runs.put(run.getId(), run);
        }
    }

    // adds another TestDataBean to this by merging all array fields
    public void add(TestsDataBean other) {
        addRuns(other.getRuns());
    }

    // returns array of passes in a specified run
    public TestPassDetail[] getPassesByRunId(int runId, boolean isStatRun) {
        if (isStatRun && excludeRun(runId))
            return new TestPassDetail[0];
        RunDetail run = getRunDetailById(runId);
        return run != null ? run.getPasses() : new TestPassDetail[0];
    }
    public User getUserById(int userId) {
        for (User u : users) {
            if (u.getId() == userId)
                return u;
        }
        return null;
    }

    // gets run detail by id
    public RunDetail getRunDetailById(int runId) {
        return runs.get(runId);
    }

    /*
    * returns trends of avg over time where the total time is the range of RunDetails we have in this object
    * if there are no runs returns NULL
    */
    public JSONObject getTrends() {
        if (runs.isEmpty())
            return null;
        Map<Date, List<RunDetail>> dates = new TreeMap<>();
        Calendar cal = Calendar.getInstance();
        for (RunDetail run: getStatRuns()) {
            cal.setTime(run.getPostTime());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date strippedDay = cal.getTime();
            List<RunDetail> list = dates.get(strippedDay);
            if (list == null) {
                dates.put(strippedDay, list = new ArrayList<>());
            }
            list.add(run);
        }

        int size = dates.size();
        double[] avgDuration = new double[size];
        double[] avgTestRuns = new double[size];
        int[] avgMemory = new int[size];
        double[] avgFailures = new double[size];
//        double[] medianMemory = new double[size];
        int i = 0;
        for (Map.Entry<Date, List<RunDetail>> entry : dates.entrySet()) {
            List<RunDetail> runs = entry.getValue();
            int passTotal = 0;
            int failTotal = 0;
            int avgMemoryTotal = 0;
            int durationTotal = 0;
            for (RunDetail run: runs) {
                passTotal += run.getPassedtests();
                failTotal += run.getFailedtests();
                durationTotal += run.getDuration();
                avgMemoryTotal += run.getAverageMemory();
//                medianMem = run.getMedian1000Memory();
            }
            avgTestRuns[i] = round((double) passTotal/runs.size(),2);
            avgFailures[i] = round(((double)failTotal)/runs.size(),2);
            avgMemory[i] = avgMemoryTotal/runs.size();
            avgDuration[i] = round(((double)durationTotal)/runs.size(),2);
//            medianMemory[i] = medianMem;
            i++;
        }
        long[] milliSecondDates = new long[dates.size()];
        int j = 0;
        for (Date d: dates.keySet()) {
            milliSecondDates[j] = d.getTime();
            j++;
        }
        // create and populate the JSONObject that will be returned
        JSONObject jo = new JSONObject();
        jo.put("avgDuration", avgDuration);
        jo.put("avgMemory", avgMemory);
        jo.put("avgFailures", avgFailures);
        jo.put("avgTestRuns", avgTestRuns);
//        jo.put("medianMemory", medianMemory);
        jo.put("dates", milliSecondDates);
        return jo;
    }

    /*
    * given a run @testRunId will return a JSON formatted map of memory data
    * because of browser limitations one out of every ten points is chosen to be displayed
    */
    public JSONObject getMemoryJson(int testRunId, boolean isStatRun) {
        TestPassDetail[] mostRecent = getPassesByRunId(testRunId, isStatRun);
        Arrays.sort(mostRecent);
        List<Double> totalMemory = new ArrayList<>();
        List<Double> managedMemory = new ArrayList<>();
        List<String> testName = new ArrayList<>();
        int index = 0;
        for (TestPassDetail testPassDetails : mostRecent) {
            if (index % 10 == 0) {
                String thisTestName = testPassDetails.getTestName() + " Pass:" + testPassDetails.getPass();
                totalMemory.add(testPassDetails.getTotalMemory());
                managedMemory.add(testPassDetails.getManagedMemory());
                testName.add(thisTestName);
            }
            index++;
        }

        JSONObject jo = new JSONObject();
        jo.put("totalMemory", totalMemory);
        jo.put("managedMemory", managedMemory);
        jo.put("testName", testName);

        JSONArray ja = new JSONArray();
        ja.put(jo);

        JSONObject mainObj = new JSONObject();
        mainObj.put("json", ja);
        return mainObj;
    }

    public User[] getUsers()
    {
        return users;
    }

    public void setUsers(User[] users)
    {
        this.users = users;
    }

    // returns true if the run with id @runId can be used in a statistical analysis
    // if a run has no test runs returns false
    public boolean excludeRun(int runId) {
        RunDetail run = runs.get(runId);
        return run == null || run.isFlagged();
    }

    public int getWarningBoundary()
    {
        if (boundaryWarning == null)
            readBoundaries();
        return boundaryWarning;
    }

    public int getErrorBoundary()
    {
        if (boundaryError == null)
            readBoundaries();
        return boundaryError;
    }

    private void readBoundaries()
    {
        SQLFragment sqlFragment = new SQLFragment();
        sqlFragment.append("select * from " + TestResultsSchema.getTableInfoGlobalSettings());
        SqlSelector sqlSelector = new SqlSelector(TestResultsSchema.getSchema(), sqlFragment);
        List<Integer> values = new ArrayList<>();
        sqlSelector.forEach(rs -> {
            values.add(rs.getInt("warningb"));
            values.add(rs.getInt("errorb"));
        });
        boundaryWarning = values.isEmpty() ? 2 : values.get(0);
        boundaryError = values.isEmpty() ? 3 : values.get(1);
    }
}
