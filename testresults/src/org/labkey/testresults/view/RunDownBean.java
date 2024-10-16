package org.labkey.testresults.view;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.testresults.TestResultsController;
import org.labkey.testresults.model.RunDetail;
import org.labkey.testresults.model.TestFailDetail;
import org.labkey.testresults.model.TestHandleLeakDetail;
import org.labkey.testresults.model.TestLeakDetail;
import org.labkey.testresults.model.TestMemoryLeakDetail;
import org.labkey.testresults.model.User;

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

public class RunDownBean extends TestsDataBean
{
    public RunDownBean(RunDetail[] runs, User[] users)
    {
        super(runs, users);
    }

    public RunDownBean(RunDetail[] runs, User[] users, String viewType, Date startDate, Date endDate)
    {
        super(runs, users, viewType, startDate, endDate);
    }

    public double getLeakMemoryAverage(List<TestLeakDetail> leaks)
    {
        int numMem = 0;
        int totalMem = 0;
        for (TestLeakDetail leak : leaks)
        {
            if (leak instanceof TestMemoryLeakDetail)
            {
                numMem++;
                totalMem += ((TestMemoryLeakDetail) leak).getBytes();
            }
        }
        return numMem != 0 ? (double)totalMem / numMem : 0;
    }

    public double getLeakHandleAverage(List<TestLeakDetail> leaks)
    {
        int numHandle = 0;
        double totalHandle = 0;
        for (TestLeakDetail leak : leaks)
        {
            if (leak instanceof TestHandleLeakDetail)
            {
                numHandle++;
                totalHandle += ((TestHandleLeakDetail) leak).getHandles();
            }
        }
        return numHandle != 0 ? totalHandle / numHandle : 0;
    }

    /* returns a map of (n)top leaks and their corresponding TestMemoryLeakDetail objects
    *sorts by most frequent leak then avg leak size if frequencies are the same
    * if 0 is passed in will return ALL leaks
    */
    public Map<String, List<TestLeakDetail>> getTopLeaks(int n, boolean isStatRun) {
        TestsDataBean runs = !isStatRun
            ? new TestsDataBean(getRuns(), new User[0])
            : new TestsDataBean(getStatRuns(), new User[0]);
        Map<String, List<TestLeakDetail>> m = new HashMap<>();
        for (TestLeakDetail leak: runs.getLeaks()) {
            List<TestLeakDetail> list = m.computeIfAbsent(leak.getTestName(), k -> new ArrayList<>());
            list.add(leak);
        }

        Map.Entry<String, List<TestLeakDetail>>[] entries = m.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(entries, (o1, o2) -> {
            List<TestLeakDetail> l1 = o1.getValue();
            List<TestLeakDetail> l2 = o2.getValue();
            if (l1.size() != l2.size())
            {
                return l2.size() - l1.size();
            }

            double mem1 = getLeakMemoryAverage(l1);
            double mem2 = getLeakMemoryAverage(l2);
            if (Double.compare(mem2, mem1) != 0)
            {
                return Double.compare(mem2, mem1);
            }

            double handle1 = getLeakHandleAverage(l1);
            double handle2 = getLeakHandleAverage(l2);
            return Double.compare(handle2, handle1);
        });
        Map<String, List<TestLeakDetail>> newMap = new LinkedHashMap<>();
        if (n == 0)
            n = entries.length;
        for (int i = 0; i < n && i < entries.length; i++) {
            newMap.put(entries[i].getKey(), entries[i].getValue());
        }

        return newMap;
    }

    public User[] getMissingUsers(RunDetail[] daysRuns) {
        User[] users = getUsers();
        if (users == null)
            return new User[0];
        List<User> missingUsers = new ArrayList<>();
        for (User u : users) {
            if (u != null && u.isActive()) {
                boolean userFound = false;
                for (RunDetail r : daysRuns) {
                    if (r != null && r.getUserid() == u.getId()) {
                        userFound = true;
                        break;
                    }
                }
                if (!userFound)
                    missingUsers.add(u);
            }
        }
        return missingUsers.toArray(new User[0]);
    }

    /* returns a map of (n)top failed tests and their corresponding TestFailDetail objects
    * sorts by most frequent failure then failure name if frequencies are the same
    * if 0 is passed in will return all failures
    */
    public Map<String, List<TestFailDetail>> getTopFailures(int n, boolean isStatRun) {
        Map<String, List<TestFailDetail>> m = new HashMap<>();
        TestsDataBean runs = !isStatRun
            ? new TestsDataBean(getRuns(), new User[0])
            : new TestsDataBean(getStatRuns(), new User[0]);
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
        Arrays.sort(entries, (o1, o2) -> {
            if (o2.getValue().size() == o1.getValue().size())
                return o1.getKey().compareTo(o2.getKey());
            return o2.getValue().size() - o1.getValue().size();
        });
        if (n == 0)
            n = entries.length;
        Map<String, List<TestFailDetail>> newMap = new LinkedHashMap<>();
        for (int i = 0; i < n && i < entries.length; i++) {
            newMap.put(entries[i].getKey(), entries[i].getValue());
        }

        return newMap;
    }

    /*
  * returns an aggregated JSON map of tests memory usage for all runs stored in the bean
  * if isToday is set to true then it will only include runs from today
  */
    public JSONObject getTodaysCompactMemoryJson(Date day) throws Exception
    {
        RunDetail[] runs = getRuns();
        if (runs.length == 0)
            return null;
        Map<String, Double[]> points = new HashMap<>();
        Map<String, int[]> memoryusagebyrun = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        TestResultsController.setToEightAM(cal);
        day = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date dateBefore1Day = cal.getTime();
        for (RunDetail run : runs) {
            boolean isSameDay = run.getPostTime().getTime() < day.getTime() && run.getPostTime().getTime() > dateBefore1Day.getTime();
            if (isSameDay && !run.isFlagged()) { // filter by today's runs
                if (run.getPointsummary() == null)
                    throw new Exception("null memory json for run id=" + run.getId());
                points.put(run.getUserName() + "(id." + run.getId() + ")", run.getPoints());
            }
        }

        Map<Integer, List<Double>> allPassPointsMap = new HashMap<>();
        Map<Integer, Double> averagePassPointMap = new HashMap<>();

        for (Map.Entry<String, Double[]> entry : points.entrySet()) {
            if (entry.getValue().length ==0)
                continue;

            Double[] encodedArray = entry.getValue();
            int index = Arrays.asList(encodedArray).indexOf(-1.0);
            if (index == -1)
                continue;

            Double[] passLocations = new Double[index];
            Double[] memoryusage = new Double[encodedArray.length-index-1]; // -1 because we no longer include the -1 flag
            System.arraycopy(encodedArray, 0, passLocations, 0, index);
            System.arraycopy(encodedArray, index+1, memoryusage, 0, encodedArray.length-index-1);
            int[] memoryusageint = new int[memoryusage.length];
            for (int i = 0; i < memoryusage.length; i++)
                memoryusageint[i] = memoryusage[i].intValue();

            memoryusagebyrun.put(entry.getKey(), memoryusageint);
            for(int i =  0; i < passLocations.length; i++) {
                double location = passLocations[i]* TestResultsController.POINT_RATIO;
                if (location == 0.0)
                    continue;
                allPassPointsMap.computeIfAbsent(i, k -> new ArrayList<>());
                allPassPointsMap.get(i).add(location);
            }
        }
        StatsService service = ServiceRegistry.get().getService(StatsService.class);
        for (Map.Entry<Integer, List<Double>> entry : allPassPointsMap.entrySet())
        {
            List<Double> list = entry.getValue();

            MathStat stats = service.getStats(ArrayUtils.toPrimitive(list.toArray(new Double[0])));
            double median = stats.getMedian();
            int largestkey = averagePassPointMap.keySet().size() == 0 ? 0 : Collections.max(averagePassPointMap.keySet());
            int tolerance = 500; // pases must be at least 500 runs away from eachother
            if (averagePassPointMap.size() == 0 ||
                (averagePassPointMap.get(largestkey) != null && median > tolerance + averagePassPointMap.get(largestkey))) {
                averagePassPointMap.put(largestkey+1, median);
            }
        }
        JSONObject jo = new JSONObject();
        jo.put("passes", averagePassPointMap.values());
        jo.put("runs", memoryusagebyrun);

        // TODO: Unused!?
        JSONObject mainObj = new JSONObject();
        mainObj.put("graphJSON", jo);
        return jo;
    }

    public RunDetail[] getRunsByDate(Date day) {
        Date matchDate = getGroupDate(day);
        RunDetail[] runsByDay = Arrays.stream(getStatRuns())
                .filter(run -> getGroupDate(run.getPostTime()).equals(matchDate))
                .toArray(RunDetail[]::new);
        Arrays.sort(runsByDay);
        return runsByDay;
    }

    public Map<User, List<RunDetail>> groupRunsByUser(RunDetail[] runs) {
        Map<User, List<RunDetail>> map = new TreeMap<>(Comparator.comparing(User::getUsername));
        for (RunDetail run : runs)
        {
            User u = getUserById(run.getUserid());
            map.computeIfAbsent(u, k -> new ArrayList<>());
            map.get(u).add(run);
        }
        return map;
    }
}
