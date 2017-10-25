<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.statistics.StatsService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="static org.labkey.testresults.TestResultsModule.ViewType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    String viewType = data.getViewType();
    Container c = getContainer();
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
<form action="<%=h(new ActionURL(TestResultsController.LongTermAction.class, c))%>">
    View Type: <select name="viewType">
        <option disabled selected> -- select an option -- </option>
        <option id="<%=h(ViewType.WEEK)%>" value="<%=h(ViewType.WEEK)%>">Week</option>
        <option id="<%=h(ViewType.MONTH)%>" value="<%=h(ViewType.MONTH)%>">Month</option>
    <option id="<%=h(ViewType.YEAR)%>" value="<%=h(ViewType.YEAR)%>">Year</option>
    <option id="<%=h(ViewType.ALLTIME)%>" value="<%=h(ViewType.ALLTIME)%>">The Beginning of Time</option>
</select>
    <input type="submit" value="Submit">
</form>
<!--If parameter "viewType" exists, will select that option in the dropdown-->
<%if(viewType != null) {%>
<script type="text/javascript">
    document.getElementById("<%=h(viewType)%>").selected = "true";
</script>
<%}%>
<%if(data != null) {%>
<%
    JSONObject trendsJson = data.getTrends();
    JSONObject failureJson = data.getFailuresJson();
    JSONObject runCountPerDayJson = data.getRunsPerDayJson();
    //TODO figure out run count for date range selected...

    StatsService service = ServiceRegistry.get().getService(StatsService.class);
%>
<div id="duration" class="c3chart" style="width:700px; height:400px"></div>
<div id="passes" class="c3chart" style="width:700px; height:400px"></div>
<div id="memory" class="c3chart" style="width:700px; height:400px"></div>
<div style="float:left; width:700px;">
    <div id="failGraph" class="c3chart" style="width:700px; height:400px"></div>
    <table id="failureTable"></table>
</div>

    <%if(trendsJson != null) {%>
        <script src="<%=h(contextPath)%>/TestResults/js/generateTrendCharts.js"></script>
        <script type="text/javascript">
            var trendsJson = jQuery.parseJSON( <%= q(trendsJson.toString()) %> );
            var failureJson = jQuery.parseJSON( <%= q(failureJson.toString()) %> );
            var runCountPerDayJson = jQuery.parseJSON( <%= q(runCountPerDayJson.toString()) %> );
            generateTrendCharts(trendsJson, <%=h(viewType.equals(ViewType.YEAR) || viewType.equals(ViewType.ALLTIME))%>);

            function subchartDomainUpdated(domain) { changeData(domain); }
            function changeData(domain) {
                if(failureJson == null)
                    return;
                var start = domain[0].mmddyyyy();
                var end = domain[1];

                var currDate = new Date(domain[0]);
                var testFailCount = {};
                var totalRuns = 0;
                while(currDate < end) {
                    var currDateStr = currDate.mmddyyyy();
                    var failures = failureJson[currDateStr];
                    var runCount = runCountPerDayJson[currDateStr];
                    if(Number.isInteger(runCount))
                        totalRuns += runCount;
                    if(failures != null) {
                        for(var i = 0; i < failures.length; i++) {
                            var fail = failures[i];
                            var testname = ""+ fail.testname;
                            if(!(testname in testFailCount)) {
                                testFailCount[testname] = 0;
                            }
                            testFailCount[testname]++;
                        }
                    }
                    currDate.setDate(currDate.getDate() + 1);
                }
                var failuresByFailToRunRatio = [];
                for (var key in testFailCount)
                {
                    if (testFailCount.hasOwnProperty(key))
                    {
                        failuresByFailToRunRatio.push([key,((testFailCount[key]/totalRuns)*100).toFixed(6)]);
                    }
                }
                // list of pairs [[testname,failures/total runs %],...]
                // sort by highest value - arr[1]
                failuresByFailToRunRatio.sort(function(a, b) {
                    a = a[1];
                    b = b[1];
                    return a < b ? 1 : (a > b ? -1 : 0);
                });
                $('#failureTable').empty();
                var failureTableHTML = "<tr><td>Test</td><td>Failures per Run(%)</td></tr>";
                for(var i = 0; i < failuresByFailToRunRatio.length; i++) {
                    var failure = failuresByFailToRunRatio[i];
                    failureTableHTML += '<tr><td><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>viewType=yr&failedTest='+failure[0]+'">'+failure[0]+'</a></td><td>'+failure[1]+'</td></tr>';
                }
                $('#failureTable').append(failureTableHTML);
            }
            Date.prototype.mmddyyyy = function() {
                var mm = this.getMonth() + 1; // getMonth() is zero-based
                var dd = this.getDate();

                return [
                    (mm>9 ? '' : '0') + mm + "/",
                    (dd>9 ? '' : '0') + dd + "/",
                    this.getFullYear()
                ].join('');
            };
        </script>
    <%}%>
<%}%>

