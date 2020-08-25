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
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
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
    String failedTest = getViewContext().getRequest().getParameter("failedTest");
    Container c = getContainer();
    RunDetail[] runs = data.getStatRuns();
    if (runs.length > 1) {
        Arrays.sort(runs, (o1, o2) -> o2.getRevision() - o1.getRevision());
    }
    Map<String, List<TestFailDetail>> languageFailures = new TreeMap<>();
    languageFailures.put(failedTest, Arrays.asList(data.getFailures()));
    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(languageFailures);

    DateFormat df = new SimpleDateFormat("MM/dd/YYYY HH:mm");
    DateFormat dfEnd = new SimpleDateFormat("MM/dd/YYYY");
    DateFormat jsDf = new SimpleDateFormat("yyyy-MM-dd");
%>

<%@include file="menu.jsp" %>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
    LABKEY.requiresCss("/TestResults/css/tablesorter-default.css");
</script>
<link rel="stylesheet" href="<%=h(contextPath)%>/TestResults/css/c3.min.css">
<script src="<%=h(contextPath)%>/TestResults/js/d3.min.js"></script>
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
<% if (data.getStatRuns().length > 0) { %>
<div style="float: left;">
    <h2><%=h(failedTest)%></h2>
    <h4>Viewing data for: <%=h(df.format(data.getStartDate()))%> - <%=h(df.format(data.getEndDate()))%></h4>
    <h4>Total failures: <%=h(runs.length)%></h4>
</div>
<br />
<!-- Bar & Pie chart containers -->
<div style="display: flex; flex-direction: row;">
    <div id="failGraph" style="width:1600px; height:400px;"></div>
    <div id="piechart" style="width:250px; height:250px;"></div>
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
            Map<String, Integer> dates = new TreeMap<>(); // maps dates to count of failures per run
            Calendar cal = Calendar.getInstance();
            cal.setTime(data.getStartDate());
            cal.add(Calendar.DATE, 1); // posted next day

            // get some graph data
            String graphXMin = jsDf.format(cal.getTime());
            String graphXMax = jsDf.format(data.getEndDate());

            int cutoffMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            String endDateString = jsDf.format(data.getEndDate());
            for (String dateString = jsDf.format(cal.getTime()); ; dateString = jsDf.format(cal.getTime())) {
                dates.put(dateString, 0);
                if (dateString.equals(endDateString))
                    break;
                cal.add(Calendar.DATE, 1);
            }
            for (RunDetail run : runs) { %>
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
            <td><%=h(run.getHang() != null ? run.getHang().getTestName() : "-")%></td>
            <td>
                <% for (TestFailDetail fail: run.getFailures()) {
                        if (fail.getTestName().equals(failedTest)) {
                            cal.setTime(run.getPostTime());
                            if (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) >= cutoffMinute) {
                                cal.add(Calendar.DATE, 1);
                            }
                            String dateString = jsDf.format(cal.getTime());
                            dates.put(dateString, dates.get(dateString) + 1); %>
                <pre style="text-align: left; padding:10px;"
                     class="pass-<%=h(fail.getPass())%>">Pass: <%=h(fail.getPass())%> Language: <%=h(fail.getLanguage())%> --- <%=h(fail.getStacktrace())%>
                            </pre>
                        <% }
                    } %>
            </td>
        </tr>
        <% } %>
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
                    for (String l: lang.keySet()) {
                        Double percent = lang.get(l) * 100;
                %>
                ['<%=h(l)%>', <%=h(percent.intValue())%>],
                <% } %>  ],
            type: 'pie'
        },
        color: { pattern: ['#FFB82E', '#A078A0', '#20B2AA', '#F08080', '#FF8B2E'] },
        pie: { label: { format: function (value, ratio, id) { return ""; } } }
    });
</script>
<!-- Failure/Day bar chart -->
<%
    // populate json with failure count for each date
    JSONObject failureTrends = new JSONObject();
    int graphYMax = 1;
    for (String key : dates.keySet()) {
        int failCountValue = dates.get(key);
        if (failCountValue > graphYMax) {
            graphYMax = failCountValue;
        }
        failureTrends.append("avgFailures", failCountValue);
        failureTrends.append("dates", key);
    }
    StringBuilder graphYTicks = new StringBuilder();
    graphYTicks.append('[');
    for (int i = 0; i <= graphYMax; i++) {
        if (i > 0)
            graphYTicks.append(", ");
        graphYTicks.append(i);
    }
    graphYTicks.append(']');

    if (failureTrends.size() > 0) { %>
    <script type="text/javascript">
        var failureJSON = jQuery.parseJSON( <%= q(failureTrends.toString()) %> );
        var dates = failureJSON.dates;
        if (dates.length >= 1)
            dates.unshift('x');
        var avgFailures = failureJSON.avgFailures;
        avgFailures.unshift("<%=h(failedTest)%> failures");

        c3.generate({
            bindto: '#failGraph',
            data: {
                x: 'x',
                columns: [dates, avgFailures],
                type: 'bar',
                onclick: function(d, i) {
                    console.log("onclick", d.x, i);
                }
            },
            size: { width: 1500 },
            bar: { width: { ratio: 0.3 } },
            subchart: { show: false, size: { height: 20 } },
            axis: {
                x: {
                    min: '<%=h(graphXMin)%>',
                    max: '<%=h(graphXMax)%>',
                    type: 'timeseries',
                    localtime: false,
                    tick: { fit: true, format: '%m/%d' }
                },
                y: { tick: { values: <%=h(graphYTicks)%> } }
            }
        });
    </script>
<%}%>
<%}%>

<script type="text/javascript">
/* Initialize sortable table */
$(document).ready(function() {
    $("#failurestatstable").tablesorter({
        widthFixed : true,
        resizable: true,
        widgets: ['zebra'],
        headers : {
            0: { sorter: "text" },
            1: { sorter: "shortDate", dateFormat: "dd/mm/yyyy hh:mm"},
            2: { sorter: "digit" },
            3: { sorter: "text" },
            4: { sorter: "digit" },
            5: { sorter: "digit" },
            6: { sorter: "text" },
            7: { sorter: false }
        },
        cssAsc: "headerSortUp",
        cssDesc: "headerSortDown",
        ignoreCase: true,
        sortList: [
            [1, 1]
//                        [2, 0]
        ],
        sortAppend: {
            0: [[ 1, 'a' ]] // secondary sort by date ascending
        },
        theme: 'default'
    });
});
</script>
