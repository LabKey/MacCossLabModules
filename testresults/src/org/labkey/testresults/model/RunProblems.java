package org.labkey.testresults.model;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class RunProblems
{
    private Map<Integer, RunDetail> runs; // run id -> run
    private ArrayList<ArrayList<ArrayList<Object>>> issues; // test -> run -> objects
    private Map<String, Integer> testIndices; // test name -> issues index
    private Map<Integer, Integer> runIndices; // run id -> issues index

    public RunProblems(RunDetail[] runs)
    {
        this.runs = new TreeMap<>();
        this.issues = new ArrayList<>();
        this.testIndices = new TreeMap<>();
        this.runIndices = new TreeMap<>();

        for (RunDetail run : runs)
        {
            if (run == null || run.isFlagged())
                continue;
            this.runs.put(run.getId(), run);
            for (TestLeakDetail leak : run.getLeaks())
                add(run, leak.getTestName(), leak);
            for (TestFailDetail fail : run.getFailures())
                add(run, fail.getTestName(), fail);
            if (run.getHang() != null)
                add(run, run.getHang().getTestName(), run.getHang());
        }
        this.runs.entrySet().removeIf(entry -> !runIndices.containsKey(entry.getKey()));
    }

    public boolean any() { return !runs.isEmpty(); }

    public String[] getTestNames()
    {
        return testIndices.keySet().toArray(new String[0]);
    }

    public RunDetail[] getRuns()
    {
        return runs.values().toArray(new RunDetail[0]);
    }

    public boolean isFail(RunDetail run, String testName)
    {
        return objExists(run, testName, TestFailDetail.class);
    }

    public boolean isLeak(RunDetail run, String testName)
    {
         return isMemoryLeak(run, testName) || isHang(run, testName);
    }

    public boolean isMemoryLeak(RunDetail run, String testName)
    {
        return objExists(run, testName, TestMemoryLeakDetail.class);
    }

    public boolean isHandleLeak(RunDetail run, String testName)
    {
        return objExists(run, testName, TestHandleLeakDetail.class);
    }

    public boolean isHang(RunDetail run, String testName)
    {
        return objExists(run, testName, TestHangDetail.class);
    }

    private boolean objExists(RunDetail run, String testName, Class<?> objType)
    {
        if (!runIndices.containsKey(run.getId()) || !testIndices.containsKey(testName))
            return false;
        int runIndex = runIndices.get(run.getId());
        ArrayList<ArrayList<Object>> runList = issues.get(testIndices.get(testName));
        if (runIndex >= runList.size())
            return false;
        for (Object obj : runList.get(runIndex))
        {
            if (obj.getClass() == objType)
                return true;
        }
        return false;
    }

    private void add(RunDetail run, String testName, Object o)
    {
        if (!testIndices.containsKey(testName))
        {
            testIndices.put(testName, testIndices.size());
            issues.add(new ArrayList<>());
        }
        if (!runIndices.containsKey(run.getId()))
        {
            runIndices.put(run.getId(), runIndices.size());
        }
        int testIndex = testIndices.get(testName);
        int runIndex = runIndices.get(run.getId());
        ArrayList<ArrayList<Object>> testIssues = issues.get(testIndex);
        while (runIndex >= testIssues.size())
        {
            testIssues.add(new ArrayList<>());
        }
        testIssues.get(runIndex).add(o);
    }
}
