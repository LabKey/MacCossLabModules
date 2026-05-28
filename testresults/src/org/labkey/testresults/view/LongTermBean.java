/*
 * Copyright (c) 2018-2026 LabKey Corporation
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
            m.compute(dateString, (_, count) -> count + 1);
        }
        return new JSONObject(m);
    }
}
