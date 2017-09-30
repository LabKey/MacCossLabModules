<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.RunDetail" %>
<%@ page import="org.labkey.testresults.TestFailDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="org.json.JSONObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 10/05/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    String viewType = getViewContext().getRequest().getParameter("viewType");
    String failedTest = getViewContext().getRequest().getParameter("failedTest");
    Container c = getContainer();
    RunDetail[] runs = data.getStatRuns();
    Arrays.sort(runs, new Comparator<RunDetail>()
    {
        @Override
        public int compare(RunDetail o1, RunDetail o2)
        {
            return o2.getRevision() - o1.getRevision();
        }
    });
    Map<String, List<TestFailDetail>> languageFailures = new TreeMap<>();
    languageFailures.put(failedTest, Arrays.asList(data.getFailuresByName(failedTest)));
    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(languageFailures);
    List<String> users = new ArrayList<>();
%>

<div id="menu">
    <ul>
        <li><a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>" style="color:#fff;">-Overview</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>" style="color:#fff;">-User</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>" style="color:#fff;">-Run</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.LongTermAction.class, c))%>" style="color:#fff;">-Long Term</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowFlaggedAction.class, c))%>" style="color:#fff;">-Flags</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, c))%>" style="color:#fff;">-Training Data</a></li>
        <li><a href="https://skyline.gs.washington.edu/labkey/project/home/issues/begin.view?" target="_blank" title="Report bugs/Request features.  Use 'TestResults' as area when creating new issue" style="color:#fff;">-Issues</a></li>
        <img src="<%=h(contextPath)%>/TestResults/img/uw.png" id="uw">
    </ul>
</div>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<script src="<%=h(contextPath)%>/TestResults/js/d3.v3.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<br />
<form action="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>">
    View Type: <select name="viewType">
    <option disabled selected> -- select an option -- </option>
    <option id="posttime" value="posttime">Day</option>
    <option id="wk" value="wk">Week</option>
    <option id="mo" value="mo">Month</option>
    <option id="yr" value="yr">Year</option>
    <option id="at" value="at">The Beginning of Time</option>
    </select>
    <select name="failedTest" style="display:none;">
        <option id="<%=h(failedTest)%>" value="<%=h(failedTest)%>"></option>
        </select>
    <input type="submit" value="Submit">
</form>
<!-- Selects the View Type in the combobox -->
<script type="text/javascript">
    document.getElementById("<%=h(viewType)%>").selected = "true";
</script>

<!-- main content of page -->
<%if(data != null && (viewType.equals("posttime") || viewType.equals("wk") || viewType.equals("mo") || viewType.equals("yr")|| viewType.equals("at"))) { %>
<div style="float:left;">
    <h2><%=h(failedTest)%></h2>
    <h4>Viewing data for: <%=h(viewType)%></h4>
    <h4>Total failures: <%=h(runs.length)%></h4>
    <h4>Unique users: <%=h(users.size())%></h4>
</div>
<br />
<!-- Bar & Pie chart containers -->
<div id="failGraph" class="c3chart" style="width:700px; height:400px;"></div>
<div id="piechart" class="c3chart" style="width:250px; height:250px;"></div>

<table class="decoratedtable" style="float:left;">
    <tr style="height:20px;">
    <th style="height:20px; padding:0;">Sorted by revision</th>
    </tr>
    <%
    Collections.reverse(Arrays.asList(runs)); // so most recent is on top
    Map<Date, Integer> dates = new TreeMap<Date, Integer>();  // maps dates to count of failures per run
    for(RunDetail run: runs) { %>
        <tr>
            <td>
                <p style="width:200px;"><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(run.getId())%>">
                <%=h(run.getUsername())%> <br />
                Duration: <%=h(run.getDuration())%> <br />
                OS: <%=h(run.getOs())%> <br />
                Post Time: <%=h(run.getPostTime())%> <br />
                Rev: <%=h(run.getRevision())%> <br />
                Run Failures: <%=h(run.getFailures().length)%>
                </a></p>
            </td>
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
    for(Map.Entry<Date, Integer> entry : dates.entrySet()) {
        failureTrends.append("avgFailures", entry.getValue());
        failureTrends.append("dates", entry.getKey().getTime());
    }
%>
<% if(failureTrends != null && failureTrends.size() > 0) {%>
    <script type="text/javascript">
        var failureJSON = jQuery.parseJSON( <%= q(failureTrends.toString()) %> );
        var dates = failureJSON.dates;
        for (var i = 0; i < dates.length; i++) {
            var d = new Date(dates[i]);
            dates[i] = d;
        }
        var avgFailures = failureJSON.avgFailures;
        if(dates.length >= 1)
            dates.unshift('x');
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
                subchart: {
                    show: true,
                    size: {
                        height: 20
                    }
                },
                axis: {
                    x: {
                        type: 'timeseries',
                        localtime: false,
                        tick: {
                            rotate: 90,
                            fit:true,
                            culling: {
                                max: 8
                            },
                            format: '%d/%m'
                        }
                    }
                }
            });
    </script>
<%}%>
<%}%>