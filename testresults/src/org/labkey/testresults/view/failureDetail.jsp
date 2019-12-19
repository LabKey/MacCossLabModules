<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.TestFailDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.text.ParseException" %>
<%@ page import="java.util.Calendar" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 10/05/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    String viewType = data.getViewType();
    String failedTest = getViewContext().getRequest().getParameter("failedTest");
    Container c = getContainer();
    RunDetail[] runs = data.getStatRuns();
    if(runs.length > 1) {
        Arrays.sort(runs, new Comparator<RunDetail>()
        {
            @Override
            public int compare(RunDetail o1, RunDetail o2)
            {
                return o2.getRevision() - o1.getRevision();
            }
        });
    }
    Map<String, List<TestFailDetail>> languageFailures = new TreeMap<>();
    languageFailures.put(failedTest, Arrays.asList(data.getFailures()));
    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(languageFailures);

    DateFormat df = new SimpleDateFormat("MM/dd/YYYY HH:mm");
    DateFormat dfEnd = new SimpleDateFormat("MM/dd/YYYY");
%>

<div id="menu">
    <ul>
        <li><a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>" style="color:#fff;">-Overview</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>" style="color:#fff;">-User</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>" style="color:#fff;">-Run</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.LongTermAction.class, c))%>" style="color:#fff;">-Long Term</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowFlaggedAction.class, c))%>" style="color:#fff;">-Flags</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, c))%>" style="color:#fff;">-Training Data</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ErrorFilesAction.class, c))%>" style="color:#fff;">-Posting Errors</a></li>
        <li><a href="https://skyline.gs.washington.edu/labkey/project/home/issues/begin.view?" target="_blank" title="Report bugs/Request features.  Use 'TestResults' as area when creating new issue" style="color:#fff;">-Issues</a></li>
        <img src="<%=h(contextPath)%>/TestResults/img/uw.png" id="uw">
    </ul>
</div>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
    LABKEY.requiresCss("/TestResults/css/tablesorter-default.css");
</script>
<script src="<%=h(contextPath)%>/TestResults/js/d3.v3.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/jquery.tablesorter.js"></script>
<br />
<%
    String value = (request.getParameter("viewType"));
    if (value == null) {
        value = "firsttime";
    }
%>
<form action="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>">
    View Type: <select name="viewType" onchange="this.form.submit()">
                    <option disabled value="firsttime" <%= (value.equals("firsttime")?"selected='selected'":"") %>> -- select an option -- </option>
                    <option id="posttime" value="posttime"  <%= (value.equals("posttime")?"selected='selected'":"") %>>Day</option>
                    <option id="wk" value="wk" <%= (value.equals("wk")?"selected='selected'":"") %> >Week</option>
                    <option id="mo" value="mo" <%= (value.equals("mo")?"selected='selected'":"") %> >Month</option>
                    <option id="yr" value="yr"  <%= (value.equals("yr")?"selected='selected'":"") %>>Year</option>
                    <option id="at" value="at"  <%= (value.equals("at")?"selected='selected'":"") %>>The Beginning of Time</option>
                </select>
    <select name="failedTest" style="display:none;">
        <option id="<%=h(failedTest)%>" value="<%=h(failedTest)%>"></option>
    </select>
    <input type="hidden" name="end" value="<%=dfEnd.format(data.getEndDate())%>" />
</form>
<!-- main content of page -->
<%if(data.getStatRuns().length > 0) { %>
<div style="float:left;">
    <h2><%=h(failedTest)%></h2>
    <h4>Viewing data for: <%=h(df.format(data.getStartDate()))%> - <%=h(df.format(data.getEndDate()))%></h4>
    <h4>Total failures: <%=h(runs.length)%></h4>
</div>
<br />
<!-- Bar & Pie chart containers -->
<div style="display: flex; flex-direction: row;">
    <div id="failGraph" class="c3chart" style="width:1600px; height:400px;"></div>
    <div id="piechart" class="c3chart" style="width:250px; height:250px;"></div>
</div>

<table class="tablesorter-default tablesorter" id="failurestatstable" style="float:left; width: 100%;">
    <thead>
    <tr>
        <th class="header headerSortDown">User</th>
        <th class="header">Post Time</th>
        <th class="header">Duration</th>
        <th class="header">OS</th>
        <th class="header">Rev</th>
        <th class="header">Total Run Failures</th>
        <th class="header">Hangs</th>
        <th>StackTrace</th>
    </tr>
    </thead>
    <tbody>
        <%
            Map<Date, Integer> dates = new TreeMap<Date, Integer>();  // maps dates to count of failures per run
            for(RunDetail run: runs) { %>
        <tr>
            <td>
                <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(run.getId())%>">
                    <%=h(run.getUserName())%>
                </a>
            </td>
            <td><%=h(df.format(run.getPostTime()))%></td>
            <td><%=h(run.getDuration())%></td>
            <td><%=h(run.getOs())%></td>
            <td><%=h(run.getRevisionFull())%></td>
            <td><%=h(run.getFailedtests())%></td>
            <td><%=h(run.hasHang())%></td>
            <td>
                <%  int failCounter = 0;
                    for(TestFailDetail fail: run.getFailures()) {
                        if(fail.getTestName().equals(failedTest)) {
                            failCounter++; %>
                <pre style="text-align: left; padding:10px;"
                     class="pass-<%=h(fail.getPass())%>">Pass: <%=h(fail.getPass())%> Language: <%=h(fail.getLanguage())%> --- <%=h(fail.getStacktrace())%>
                            </pre>
                <%}
                }%>
                <%
                    if(!dates.containsKey(run.getPostTime()))
                        dates.put(run.getPostTime(), 0);
                    int currentCount = dates.get(run.getPostTime());
                    dates.put(run.getPostTime(),currentCount+failCounter);
                %>
            </td>
        </tr>
    <%}%>
   </tbody>
</table>


<!-- Pie Chart -->
<script type="text/javascript">
    var piechart = c3.generate({
        bindto: '#piechart',
        data: {
            columns: [
                <%
                    Map<String, Double> lang =languageBreakdown.get(failedTest);
                   for(String l: lang.keySet()) {
                    Double percent = lang.get(l) * 100;
                %>

                ['<%=h(l)%>', <%=h(percent.intValue())%>],
                <%}%>  ],
            type : 'pie'
        },
        color: {
            pattern: ['#FFB82E', '#A078A0', '#20B2AA', '#F08080', '#FF8B2E']
        } ,
        pie: {
            label: {
                format: function (value, ratio, id) {
                    return "";
                }
            }
        }
    });
</script>
<!-- Failure/Day bar chart -->
<%
    JSONObject failureTrends = new JSONObject();
    // populate json with failure count for each date
    var first = true;
    for(Map.Entry<Date, Integer> entry : dates.entrySet()) {
        if (!first) {
            var contains = false;

            int i = failureTrends.getJSONArray("dates").length() - 1;
            String stringDate = failureTrends.getJSONArray("dates").get(i).toString();
            Calendar calendar1 = Calendar.getInstance();
            long milliseconds = Long.parseLong(stringDate);
            calendar1.setTimeInMillis(milliseconds);

            Calendar calendar2 = Calendar.getInstance();
            calendar2.setTimeInMillis(entry.getKey().getTime());

            pageContext.setAttribute("test", calendar2.get(Calendar.YEAR));


            if (calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                    calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH) &&
                    calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH)) {
                contains = true;
            }
            if (contains) {
                int count = (int)failureTrends.getJSONArray("avgFailures").get(i) + 1;
                failureTrends.remove(failureTrends.getJSONArray("avgFailures").get(i));
                failureTrends.append("avgFailures", count);
            }
            else
            {
                failureTrends.append("avgFailures", entry.getValue());
                failureTrends.append("dates", entry.getKey().getTime());
            }
        }
        else {
            failureTrends.append("avgFailures", entry.getValue());
            failureTrends.append("dates", entry.getKey().getTime());
            first = false;
        }
    }
%>
<% if(failureTrends.size() > 0) {%>
    <script type="text/javascript">
        var failureJSON = jQuery.parseJSON( <%= q(failureTrends.toString()) %> );
        console.log(failureJSON)
        var dates = failureJSON.dates;
        for (var i = 0; i < dates.length; i++) {
            var d = new Date(dates[i]);
            dates[i] = d;
            console.log(d);
        }
        var avgFailures = failureJSON.avgFailures;
        if(dates.length >= 1)
            dates.unshift('x');
        console.log(dates);
        avgFailures.unshift("<%=h(failedTest)%> failures");

            var failTrendChart = c3.generate({
                bindto: '#failGraph',
                data: {
                    x: 'x',
                    columns: [
                        dates,
                        avgFailures
                    ],
                    type: 'bar',
                    onclick: function(d, i) {
                        console.log("onclick", d.x, i);
                    }
                },
                size : {
                  width: 1500
                },

                bar: {
                  width: {
                      <%if (value.equals("posttime")) {%>
                        ratio: 1
                      <%}%>
                      <%if (value.equals("wk")) {%>
                        ratio: .6
                      <%}%>
                      <%if (value.equals("mo")) {%>
                        ratio: .4
                      <%}%>
                      <%if (value.equals("yr")) {%>
                        ratio: .026
                      <%}%>
                      <%if (value.equals("at")) {%>
                           ratio: .00004
                      <%}%>
                  }
                },
                subchart: {
                    show: false,
                    size: {
                        height: 20
                    }
                },
                axis: {
                    x: {
                        type: 'timeseries',
                        localtime: false,
                        tick: {
                            culling: true,
                            rotate: 0,
                            fit:false,
                            culling: {
                                max: 8
                            },
                            format: '%m/%d'
                        }
                    }
                }
            });
    </script>
<%}%>
<%}%>

<script type="text/javascript">
    /* Initialize sortable table */
    $(document).ready(function()
            {
                $("#failurestatstable").tablesorter({
                    widthFixed : true,
                    resizable: true,
                    widgets: ['zebra'],
                    headers : {
                        0: { sorter: "text" },
                        1: { sorter: "shortDate" , dateFormat: "dd/mm/yyyy hh:mm"},
                        2: { sorter: "digit" },
                        3: { sorter: "text" },
                        4: { sorter: "digit" },
                        5: { sorter: "digit" },
                        6: {sorter: "text"},
                        7: {sorter: false}
                    },
                    cssAsc        : "headerSortUp",
                    cssDesc       : "headerSortDown",
                    ignoreCase: true,
                    sortList: [
                        [1, 1]
//                        [2, 0]
                    ],
                    sortAppend: {
                        0 : [[ 1, 'a' ]] // secondary sort by date ascending
                    },
                    theme: 'default'



                });
            }
    );
</script>