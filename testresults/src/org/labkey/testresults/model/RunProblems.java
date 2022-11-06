package org.labkey.testresults.model;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public class RunProblems
{
    private final Map<Integer, RunDetailProblems> allProblems;

    public RunProblems(RunDetail[] runs)
    {
        allProblems = new TreeMap<>();

        for (RunDetail run : runs)
        {
            RunDetailProblems problems = new RunDetailProblems(run);
            if (problems.any())
            {
                allProblems.put(run.getId(), problems);
            }
        }
    }

    public boolean any() { return !allProblems.isEmpty(); }

    public String[] getTestNames()
    {
        Set<String> names = new TreeSet<>();
        for (RunDetailProblems problems : allProblems.values())
        {
            names.addAll(Arrays.asList(problems.getTestNames()));
        }
        return names.toArray(String[]::new);
    }

    public RunDetail[] getRuns()
    {
        return allProblems.values().stream().map(problems -> problems.run).toArray(RunDetail[]::new);
    }

    public boolean hasFailure(RunDetail run, String testName)
    {
        return hasProblem(run, testName, TestFailDetail.class);
    }

    public boolean hasLeak(RunDetail run, String testName)
    {
        return hasMemoryLeak(run, testName) || hasHandleLeak(run, testName);
    }

    public boolean hasMemoryLeak(RunDetail run, String testName)
    {
        return hasProblem(run, testName, TestMemoryLeakDetail.class);
    }

    public boolean hasHandleLeak(RunDetail run, String testName)
    {
        return hasProblem(run, testName, TestHandleLeakDetail.class);
    }

    public boolean hasHang(RunDetail run, String testName)
    {
        return hasProblem(run, testName, TestHangDetail.class);
    }

    private boolean hasProblem(RunDetail run, String testName, Class<?> objType)
    {
        return getProblems(run, testName, objType).anyMatch(o -> true);
    }

    public TestFailDetail[] getFailures(RunDetail run, String testName)
    {
        return getProblems(run, testName, TestFailDetail.class).toArray(TestFailDetail[]::new);
    }

    public TestLeakDetail[] getLeaks(RunDetail run, String testName)
    {
        return getProblems(run, testName, TestLeakDetail.class).toArray(TestLeakDetail[]::new);
    }

    public TestHangDetail[] getHangs(RunDetail run, String testName)
    {
        return getProblems(run, testName, TestHangDetail.class).toArray(TestHangDetail[]::new);
    }

    private <T> Stream<T> getProblems(RunDetail run, String testName, Class<T> objType)
    {
        RunDetailProblems problems = run != null ? allProblems.get(run.getId()) : null;
        return problems != null ? problems.getProblems(testName, objType) : Stream.empty();
    }

    private static class RunDetailProblems
    {
        private final RunDetail run;
        private final Map<String, ArrayList<Object>> problems; // test name -> problems

        public RunDetailProblems(RunDetail run)
        {
            this.run = run;
            this.problems = new HashMap<>();
            if (run == null || run.isFlagged())
            {
                return;
            }

            for (TestFailDetail fail : run.getFailures())
            {
                problems.computeIfAbsent(fail.getTestName(), s -> new ArrayList<>()).add(fail);
            }

            for (TestLeakDetail leak : run.getLeaks())
            {
                problems.computeIfAbsent(leak.getTestName(), s -> new ArrayList<>()).add(leak);
            }

            TestHangDetail hang = run.getHang();
            if (hang != null)
            {
                problems.computeIfAbsent(hang.getTestName(), s -> new ArrayList<>()).add(hang);
            }
        }

        public boolean any()
        {
            return !problems.isEmpty();
        }

        public String[] getTestNames()
        {
            return problems.keySet().toArray(String[]::new);
        }

        public <T> Stream<T> getProblems(String testName, Class<T> objType)
        {
            Stream<Object> stream = Stream.empty();
            if (StringUtils.isEmpty(testName))
            {
                for (ArrayList<Object> list : problems.values())
                {
                    stream = Stream.concat(stream, list.stream());
                }
            }
            else
            {
                stream = problems.getOrDefault(testName, new ArrayList<>()).stream();
            }

            return stream.filter(objType::isInstance).map(objType::cast);
        }
    }
}
