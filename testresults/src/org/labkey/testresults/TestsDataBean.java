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
package org.labkey.testresults;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.remoteapi.assay.Run;

import java.lang.Override;
import java.lang.String;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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
        if(endDate == null)
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


    private TestFailDetail[] nonAssociatedFailures;

    public TestsDataBean(RunDetail[] runs) {
        setRuns(runs);
        statRuns = setStatRuns();
    }

    public TestsDataBean(RunDetail[] runs, User[] users) {
        this(runs);
        setUsers(users);
    }

    public TestsDataBean(RunDetail[] runs, User[] users, String viewType) {
        this(runs, users);
        setViewType(viewType);
    }

    public TestsDataBean(RunDetail[] runs, User[] users, String viewType, Date startDate, Date endDate) {
        this(runs, users, viewType);
        setStartDate(startDate);
        setEndDate(endDate);
    }

    // Getters and Setters for fields
    public RunDetail[] getRuns() {
        RunDetail[] r = runs.values().toArray(new RunDetail[runs.size()]);
        Arrays.sort(r);
        return r;
    }
    public RunDetail[] setStatRuns() {
        List<RunDetail> statRuns = new ArrayList<>();
        for(RunDetail run: runs.values())
            if(!excludeRun(run.getId()))
                statRuns.add(run);
        Collections.sort(statRuns);
        return statRuns.toArray(new RunDetail[statRuns.size()]);
    }

    public RunDetail[] getStatRuns() {
        return statRuns;
    }

    public Map<String, Map<String, Double>> getLanguageBreakdown(Map<String, List<TestFailDetail>> topFailures){
                Map<String, Map<String, Double>> m = new TreeMap<String, Map<String, Double>>();
        for(String f: topFailures.keySet()) {
            double total = 0.0;
            if(!m.containsKey(f))
                m.put(f, new TreeMap<String, Double>());
            List<TestFailDetail> l = topFailures.get(f);
            for(TestFailDetail detail: l) {
                if(detail != null) {
                    if(!m.get(f).containsKey(detail.getLanguage()))
                        m.get(f).put(detail.getLanguage(), 0.0);
                    m.get(f).put(detail.getLanguage(), m.get(f).get(detail.getLanguage()) + 1);
                    total += 1;
                }
            }
            for(String language: m.get(f).keySet()) {
                m.get(f).put(language, m.get(f).get(language) / total);
            }
        }

        return m;
    }

    public User[] getMissingUsers(RunDetail[] daysRuns) {
        if(users == null)
            return new User[0];
        List<User> missingUsers = new ArrayList<>();
        for(User u: users) {
            if(u != null && u.isActive()) {
                boolean userFound = false;
                for(RunDetail r: daysRuns) {
                    if(r != null && r.getUserid() == u.getId()) {
                        userFound = true;
                        break;
                    }
                }
                if(!userFound)
                    missingUsers.add(u);
            }
        }
        return  missingUsers.toArray(new User[missingUsers.size()]);
    }

    public double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

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

    public TestLeakDetail[] getLeaks() {
        List<TestLeakDetail> leaks = new ArrayList<>();
        for(RunDetail r: runs.values()) {
                leaks.addAll(Arrays.asList(r.getLeaks()));
        }
        return leaks.toArray(new TestLeakDetail[leaks.size()]);
    }

    public TestFailDetail[] getFailures() {
        List<TestFailDetail> fails = new ArrayList<>();
        for(RunDetail r: runs.values())
                fails.addAll(Arrays.asList(r.getFailures()));
        return fails.toArray(new TestFailDetail[fails.size()]);
    }

    public TestFailDetail[] getFailuresByName(String testName) {
        List<TestFailDetail> fails = new ArrayList<>();
        for(RunDetail r: runs.values())
            for(TestFailDetail f: r.getFailures())
                if(f.getTestName().equals(testName))
                    fails.add(f);
        return fails.toArray(new TestFailDetail[fails.size()]);
    }

    public TestPassDetail[] getPasses() {
        List<TestPassDetail> passes = new ArrayList<>();
        for(RunDetail run : runs.values())
                passes.addAll(Arrays.asList(run.getPasses()));
        return passes.toArray(new TestPassDetail[passes.size()]);
    }

    private void addRuns(RunDetail[] runs) {
        if(runs == null)
            return;
        for (RunDetail run : runs) {
            // round all memory data
            TestPassDetail[] passes = run.getPasses();
            if(passes != null && passes.length > 0 && passes[0] != null) {
                for(int i = 0; i <passes.length; i++) {
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
        if(isStatRun && excludeRun(runId))
            return new TestPassDetail[0];
        RunDetail run = getRunDetailById(runId);
        return run!=null?run.getPasses():new TestPassDetail[0];
    }
    public User getUserById(int userId) {
        for(User u : users) {
            if(u.getId() == userId)
                return u;
        }
        return null;
    }
    // failures and leaks arent referenced to users to this method can be used to find user of failure or leak
    public User getUserByRunId(int runId) {
        RunDetail r = getRunDetailById(runId);
        User u = null;
        if(r != null) {
             u = getUserById(r.getUserid());
        }
        return u;
    }
    // returns array of failures in a specified run
    public TestFailDetail[] getFailuresByRunId(int runId) {
        RunDetail f = getRunDetailById(runId);
        return f!=null?f.getFailures():new TestFailDetail[0];
    }

    // gets run detail by id
    public RunDetail getRunDetailById(int runId) {
        return runs.get(runId);
    }

    // returns a map of test name to list of TestLeakDetails for a specified day
    public Map<String, List<TestLeakDetail>> getLeaksByDate(Date d, boolean isStatRun) {
        Map<String, List<TestLeakDetail>> m = new TreeMap<String, List<TestLeakDetail>>();
        RunDetail[] dayRuns = getRunsByDate(d, true);
        for(RunDetail r: dayRuns) {
            if(isStatRun && !excludeRun(r.getId()))
            {
                for(TestLeakDetail l: r.getLeaks()) {
                if(!m.containsKey(l.getTestName()))
                    m.put(l.getTestName(), new ArrayList<TestLeakDetail>());
                m.get(l.getTestName()).add(l);
                }
            }
        }
        return m;
    }

    // returns a map of test name to list of TestFailDetails for a specified day
    public Map<String, List<TestFailDetail>> getFailedTestsByDate(Date d, boolean isStatRun) {
        Map<String, List<TestFailDetail>> m = new TreeMap<String, List<TestFailDetail>>();
        RunDetail[] dayRuns = getRunsByDate(d, true);
        for(RunDetail r: dayRuns) {
            if(isStatRun && !excludeRun(r.getId()))
            {
                for(TestFailDetail f: r.getFailures()) {
                    if(!m.containsKey(f.getTestName()))
                    {
                        m.put(f.getTestName(), new ArrayList<TestFailDetail>());
                    }
                    m.get(f.getTestName()).add(f);
                }
            }
        }
        return m;
    }

    public RunDetail[] getRunsByDate(Date day, boolean isStatRun) {
        List<RunDetail> runByDay = new ArrayList<RunDetail>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        day = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();
        for(RunDetail runDetail:getStatRuns()) {
            boolean isSameDay = (runDetail.getPostTime().getTime() < day.getTime() && runDetail.getPostTime().getTime() > dateBefore1Day.getTime());
            if(isSameDay)
                runByDay.add(runDetail);
        }
        runByDay.sort(null);
        RunDetail[] returnedRuns = runByDay.toArray(new RunDetail[runByDay.size()]);
        return returnedRuns;
    }

    /*
    * returns trends of avg over time where the total time is the range of RunDetails we have in this object
    * if there are no runs returns NULL
    */
    public JSONObject getTrends() {
        if(runs.size() == 0)
            return null;
        Map<Date, List<RunDetail>> dates = new TreeMap<Date, List<RunDetail>>();
        Calendar cal = Calendar.getInstance();
        for(RunDetail run: getStatRuns()) {
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
        int i = 0;
        for(Map.Entry<Date, List<RunDetail>> entry : dates.entrySet()) {
            List<RunDetail> runs = entry.getValue();
            int passTotal = 0;
            int failTotal = 0;
            int avgMemoryTotal = 0;
            int durationTotal = 0;
            for(RunDetail run: runs) {
                passTotal += run.getPassedtests();
                failTotal += run.getFailedtests();
                durationTotal += run.getDuration();
                avgMemoryTotal += run.getAverageMemory();
            }
            avgTestRuns[i] = round((double) passTotal/runs.size(),2);
            avgFailures[i] = round(((double)failTotal)/runs.size(),2);
            avgMemory[i] = avgMemoryTotal/runs.size();
            avgDuration[i] = round(((double)durationTotal)/runs.size(),2);
            i++;
        }
        long[] milliSecondDates = new long[dates.size()];
        int j = 0;
        for(Date d: dates.keySet()) {
            milliSecondDates[j] = d.getTime();
            j++;
        }
        // create and populate the JSONObject that will be returned
        JSONObject jo = new JSONObject();
        jo.put("avgDuration", avgDuration);
        jo.put("avgMemory", avgMemory);
        jo.put("avgFailures", avgFailures);
        jo.put("avgTestRuns", avgTestRuns);
        jo.put("dates", milliSecondDates);

        return jo;
    }

    /*
    * returns an aggregated JSON map of tests memory usage for all runs stored in the bean
    * if isToday is set to true then it will only include runs from today
    */
    public JSONObject getTodaysCompactMemoryJson(Date day) throws Exception
    {
        if(runs.size() == 0)
            return null;
        List<RunDetail> selectedRuns = new ArrayList<>();
        Map<String, Double[]> points = new HashMap<>();
        Map<String, int[]> memoryusagebyrun = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        day = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();
        for (RunDetail run:runs.values()) {
            boolean isSameDay = (run.getPostTime().getTime() < day.getTime() && run.getPostTime().getTime() > dateBefore1Day.getTime());
            if(isSameDay && !run.isFlagged()) { // filter by today's runs
                if(run.getPointsummary() == null)
                    throw new Exception("null memory json for run id=" + run.getId());
                selectedRuns.add(run);
                points.put(run.getUserName() + "(id." + run.getId() + ")", run.getPoints());
            }
        }

        JSONObject json = new JSONObject();
        Map<Integer, List<Double>> allPassPointsMap = new HashMap<>();
        Map<Integer, Double> averagePassPointMap = new HashMap<>();

        Map<String, Double[]> dataPoints = new HashMap<>();
        for (Map.Entry<String, Double[]> entry : points.entrySet()) {
            if(entry.getValue().length ==0)
                continue;

            Double[] encodedArray = entry.getValue();
            int index = Arrays.asList(encodedArray).indexOf(-1.0);
            if(index == -1)
                continue;

            Double[] passLocations = new Double[index];
            Double[] memoryusage = new Double[encodedArray.length-index-1]; // -1 because we no longer include the -1 flag
            System.arraycopy(encodedArray, 0, passLocations, 0, index);
            System.arraycopy(encodedArray, index+1, memoryusage, 0, encodedArray.length-index-1);
            int[] memoryusageint = new int[memoryusage.length];
            for(int i = 0; i < memoryusage.length; i++)
                memoryusageint[i] = memoryusage[i].intValue();

            memoryusagebyrun.put(entry.getKey(), memoryusageint);
            for(int i =  0; i < passLocations.length; i++) {
                double location = passLocations[i]*TestResultsController.POINT_RATIO;
                if(location == 0.0)
                    continue;
                    if(allPassPointsMap.get(i) == null)
                    allPassPointsMap.put(i, new ArrayList<>());
                allPassPointsMap.get(i).add(location);
            }
        }
        StatsService service = ServiceRegistry.get().getService(StatsService.class);
        for (Map.Entry<Integer, List<Double>> entry : allPassPointsMap.entrySet())
        {
            double total = 0;
            List<Double> list = entry.getValue();

            MathStat stats = service.getStats(ArrayUtils.toPrimitive(list.toArray(new Double[list.size()])));
            double median = stats.getMedian();
            int largestkey = averagePassPointMap.keySet().size() == 0 ? 0 : Collections.max(averagePassPointMap.keySet());
            int tolerance = 500; // pases must be at least 500 runs away from eachother
            if(averagePassPointMap.size() == 0 ||
                    (averagePassPointMap.get(largestkey) != null && median > tolerance + averagePassPointMap.get(largestkey))) {
                averagePassPointMap.put(largestkey+1, median);
            }
        }
        int i = averagePassPointMap.size();
        JSONObject jo = new JSONObject();
        jo.put("passes", averagePassPointMap.values());
        jo.put("runs", memoryusagebyrun);

        JSONObject mainObj = new JSONObject();
        mainObj.put("graphJSON", jo);
        return jo;
    }

    public Map<User, List<RunDetail>> getUserToRunsMap(Date selectedDate) {
        // treemap sorted by usernames
        Map<User, List<RunDetail>> map = new TreeMap<>(new Comparator<User>() {
            public int compare(User u1, User u2) {
                return u1.getUsername().compareTo(u2.getUsername());
            }
        });
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        selectedDate = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();

        for(RunDetail run: getRuns()) {  // Currently uses getPostTime() (time of post) for same day instead of getTimestamp() which is the actual timestamp run started
            Date runDate = run.getPostTime();
            if(runDate == null)
                runDate = run.getPostTime();
            boolean isSameDay = (runDate.getTime() < selectedDate.getTime() && runDate.getTime() > dateBefore1Day.getTime());
            if(!isSameDay)
                continue;
            if(map.get(getUserById(run.getUserid())) == null)
                map.put(getUserById(run.getUserid()), new ArrayList<>());
            map.get(getUserById(run.getUserid())).add(run);
        }
        return map;
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
        for(TestPassDetail testPassDetails : mostRecent) {
            if(index % 10 == 0) {
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

    /* returns a map of (n)top leaks and their corresponding TestLeakDetail objects
    *sorts by most frequent leak then avg leak size if frequencies are the same
    * if 0 is passed in will return ALL leaks
    */
    public Map<String, List<TestLeakDetail>> getTopLeaks(int n, boolean isStatRun) {
        TestsDataBean runs = new TestsDataBean(getRuns());
        if(isStatRun)
            runs = new TestsDataBean(getStatRuns());
        Map<String, List<TestLeakDetail>> m = new HashMap<>();
        TestLeakDetail[] leaks = runs.getLeaks();
        for(TestLeakDetail leak: leaks) {
            List<TestLeakDetail> list = m.get(leak.getTestName());
            if (list == null) {
                list = new ArrayList<>();
                m.put(leak.getTestName(), list);
            }
            list.add(leak);
        }

        Map.Entry<String, List<TestLeakDetail>>[] entries = m.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(entries, new Comparator<Map.Entry<String, List<TestLeakDetail>>>()
        {
            @Override
            public int compare(Map.Entry<String, List<TestLeakDetail>> o1, Map.Entry<String, List<TestLeakDetail>> o2)
            {
                if (o2.getValue().size() - o1.getValue().size() == 0)
                {
                    double[] leak1bytes = new double[o1.getValue().size()];
                    double[] leak2bytes = new double[o2.getValue().size()];
                    for (int i = 0; i < o1.getValue().size(); i++)
                    {
                        leak1bytes[i] = o1.getValue().get(i).getBytes();
                    }
                    for (int i = 0; i < o2.getValue().size(); i++)
                    {
                        leak2bytes[i] = o2.getValue().get(i).getBytes();
                    }
                    StatsService service = ServiceRegistry.get().getService(StatsService.class);
                    MathStat l1 = service.getStats(leak1bytes);
                    MathStat l2 = service.getStats(leak2bytes);
                    return (int) (l1.getMean() - l2.getMean());
                }

                return o2.getValue().size() - o1.getValue().size();
            }
        });
        Map<String, List<TestLeakDetail>> newMap = new LinkedHashMap<>();
        if(n == 0)
            n = entries.length;
        for (int i = 0; i < n && i < entries.length; i++) {
            newMap.put(entries[i].getKey(), entries[i].getValue());
        }

        return newMap;
    }

    /* returns a map of (n)top failed tests and their corresponding TestFailDetail objects
    * sorts by most frequent failure then failure name if frequencies are the same
    * if 0 is passed in will return all failures
    */
    public Map<String, List<TestFailDetail>> getTopFailures(int n, boolean isStatRun) {
        Map<String, List<TestFailDetail>> m = new HashMap<>();
        TestsDataBean runs = new TestsDataBean(getRuns());
        if(isStatRun)
            runs = new TestsDataBean(getStatRuns());
        TestFailDetail[] failures = runs.getFailures();
        for(TestFailDetail fail: failures) {
            List<TestFailDetail> list = m.get(fail.getTestName());
            if (list == null) {
                list = new ArrayList<>();
                m.put(fail.getTestName(), list);
            }
            list.add(fail);
        }
        Map.Entry<String, List<TestFailDetail>>[] entries = m.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(entries, new Comparator<Map.Entry<String, List<TestFailDetail>>>()
        {
            @Override
            public int compare(Map.Entry<String, List<TestFailDetail>> o1, Map.Entry<String, List<TestFailDetail>> o2)
            {
                if (o2.getValue().size() == o1.getValue().size())
                    return o1.getKey().compareTo(o2.getKey());
                return o2.getValue().size() - o1.getValue().size();
            }
        });
        if(n == 0)
            n = entries.length;
        Map<String, List<TestFailDetail>> newMap = new LinkedHashMap<>();
        for (int i = 0; i < n && i < entries.length; i++) {
            newMap.put(entries[i].getKey(), entries[i].getValue());
        }

        return newMap;
    }

    /*
    * returns a map of mean, min, and max values for Duration, Test Runs, Failures, and Leaks
    */
    public Map<String, double[]> getAvgMinMaxTableData(boolean isStatRun) {
        Map<String, double[]> map = new HashMap<>();
        StatsService service = ServiceRegistry.get().getService(StatsService.class);

        // Calculate durations
        TestsDataBean runsBean;
        if(isStatRun)
            runsBean = new TestsDataBean(getStatRuns());
        else
            runsBean = new TestsDataBean(getRuns());
        RunDetail[] runs = runsBean.getRuns();
        double[] durations = new double[runs.length];
        for(int i = 0; i < runs.length; i++)
            durations[i] = runs[i].getDuration();

        MathStat duration = service.getStats(durations);
        double[] toMap = {duration.getMean(), duration.getMinimum(), duration.getMaximum()};
        map.put("duration", toMap);

        // Calculate test passes, failures, and leak statistics
        List<Double> passed = new ArrayList<>();
        List<Double> failed = new ArrayList<>();
        List<Double> leaked = new ArrayList<>();
        for(RunDetail run: runs) {
            int passCount = run.getPasses() == null ? 0 : run.getPassedtests();
            int failCount = run.getFailures() == null ? 0 : run.getFailedtests();
            int leakCount = run.getLeaks() == null ? 0 : run.getLeakedtests();

            passed.add((double)passCount);
            failed.add((double)failCount);
            leaked.add((double)leakCount);
        }
        Double[] passedTests = passed.toArray(new Double[passed.size()]);
        Double[] failedTests = failed.toArray(new Double[failed.size()]);
        Double[] leakedTests = leaked.toArray(new Double[leaked.size()]);

        MathStat passStats = service.getStats(ArrayUtils.toPrimitive(passedTests));
        double[] testRuns = {passStats.getMean(), passStats.getMinimum(), passStats.getMaximum()};
        map.put("testruns", testRuns);

        MathStat failStats = service.getStats(ArrayUtils.toPrimitive(failedTests));
        double[] testFailures = {failStats .getMean(), failStats.getMinimum(), failStats.getMaximum()};
        map.put("failures", testFailures);

        MathStat leakStats = service.getStats(ArrayUtils.toPrimitive(leakedTests));
        double[] testLeaks = {leakStats.getMean(), leakStats.getMinimum(), leakStats.getMaximum()};
        map.put("leaks", testLeaks);

        return map;
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
        if(run == null)
            return true;
        if(run.isFlagged())
            return true;
        return false;
    }

    public void setNonAssociatedFailures(TestFailDetail[] nonAssociatedFailures)
    {
        this.nonAssociatedFailures = nonAssociatedFailures;
    }
    public JSONObject getRunsPerDayJson() {
        Map<String, Integer> m = new TreeMap<>();
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        Calendar cal = Calendar.getInstance();
        for(RunDetail run : getRuns()) {
            cal.setTime(run.getTimestamp());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long time = cal.getTimeInMillis();
            Date d = new Date(time);
            String dateString = df.format(d);
            if(!m.containsKey(dateString)) {
                m.put(dateString, 0);
            }
            int count = m.get(dateString);
            m.put(dateString, count+1);
        }
        return new JSONObject(m);
    }
    public JSONObject getFailuresJson()
    {
        Map<String, List<Map<String, String>>> m = new TreeMap<>();

        Calendar cal = Calendar.getInstance();
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        for (TestFailDetail fail: nonAssociatedFailures)
        {
            cal.setTime(fail.getTimestamp());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long time = cal.getTimeInMillis();
            Date d = new Date(time);
            String dateString = df.format(d);
            fail.setTimestamp(d);

            if(!m.containsKey(dateString)) {
                m.put(dateString, new ArrayList<>());
            }
            Map<String, String> failDetails = new TreeMap<>();
            failDetails.put("testname", fail.getTestName());
            failDetails.put("language", fail.getLanguage());
            m.get(dateString).add(failDetails);

        }
        JSONObject json = new JSONObject(m);
        return json;
    }
}
