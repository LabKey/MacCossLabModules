package org.labkey.testresults.view;


import org.json.JSONObject;
import org.labkey.testresults.model.User;
import org.labkey.testresults.model.RunDetail;
import org.labkey.testresults.model.TestFailDetail;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LongTermBean extends TestsDataBean
{
    private TestFailDetail[] nonAssociatedFailures;


    public LongTermBean(RunDetail[] runs, User[] users)
    {
        super(runs, users);
    }

    public LongTermBean(RunDetail[] runs, User[] users, String viewType, Date startDate, Date endDate)
    {
        super(runs, users, viewType, startDate, endDate);
    }

    public void setNonAssociatedFailures(TestFailDetail[] nonAssociatedFailures)
    {
        this.nonAssociatedFailures = nonAssociatedFailures;
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
}
