<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.data.statistics.StatsService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.RunDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.TestResultsSchema" %>
<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="org.labkey.testresults.User" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.NavigableSet" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();

    String startDate = getViewContext().getRequest().getParameter("start");
    String endDate = getViewContext().getRequest().getParameter("end");
    boolean showSingleUser = true;
    String user = getViewContext().getRequest().getParameter("user");
    if(user == null || user.equals(""))
        showSingleUser = false;
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    Date today = new Date();
    if(startDate == null)
        startDate = df.format(today);
    if(endDate == null)
        endDate = df.format(today);

    StatsService service = ServiceRegistry.get().getService(StatsService.class);
    JSONObject trendsJson = null;
    if(data != null && data.getRuns().length > 0) {
        trendsJson = data.getTrends();
    }
    Container c = getViewContext().getContainer();
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<script src="<%=h(contextPath)%>/TestResults/js/d3.v3.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/multiselect.datepicker.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<div id="content">
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
    <br />
    <!--If data is not null (runs exist for user in selected date range)-->
    <%if(data != null && data.getRuns().length >= 0) {%>
        <div id="datePickers">
            <strong>Select date range</strong> <br />
                <div id="jrange" class="dates">
                    <input />
                    <div></div>
                </div>
        </div>
    <%}%>
      <%if(!showSingleUser) {
           User[] users = new TableSelector(TestResultsSchema.getInstance().getTableInfoUser(), null, null).getArray(User.class);
           List<User> uniqueUsers = new ArrayList<>();
           for(User u: users)
               uniqueUsers.add(u);
           Collections.sort(uniqueUsers, new Comparator<User>(){ public int compare(User o1, User o2)
               {
                   return o1.getUsername().compareTo(o2.getUsername());
               }
           }); %>
       <p>No user selected, please select from the following list:</p>
       <select id="users">
           <option disabled selected> -- select an option -- </option>
           <%for(User u: uniqueUsers) {%>
               <option value="<%=h(u.getUsername())%>"><%=h(u.getUsername())%></option>
           <%}%>
       </select>


       <script type="text/javascript">
           $('#users').on('change', function (e) {
               var valueSelected = this.value;
               window.location.href = "<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=" + valueSelected;
           });
       </script>
       <%} else if(data == null) {%>
       <p>User, <%=h(user)%> was selected but there are no runs in the current date selection matching the specified user.</p>
       <%}%>

    <%if(data != null && data.getRuns().length > 0) {%>
    <div id="headerContent">
        <h2><%=h(user)%></h2>
        <% String headerDate = startDate;
        if(getViewContext().getRequest().getParameter("end") != null)
            headerDate += " - " + endDate; %>
        <p><%=h(headerDate)%></p>
        <p><a id="dayView">DAY</a> | <a id="weekView">WEEK</a> | <a id="monthView">MONTH</a></p>
        <!--click functions for Day, Week, and Month views-->
        <script type="text/javascript">
            $( "#dayView" ).click(function() {
                var endDate = new Date();
                var endDateFormatted = endDate.getUTCMonth() + 1 + "/" + endDate.getUTCDate() + "/" + endDate.getUTCFullYear();
                var startDate = new Date();
                var startDateFormatted = startDate.getUTCMonth() + 1 + "/" + startDate.getUTCDate() + "/" + startDate.getUTCFullYear();
                window.location.href = "<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=" + "<%=h(user)%>" +
                        "&start=" + startDateFormatted + "&end=" + endDateFormatted;
            });
            $( "#weekView" ).click(function() { // no need for parameters because week is default view
                window.location.href = "<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=" + "<%=h(user)%>";
            });
            $( "#monthView" ).click(function() {
                var endDate = new Date();
                var endDateFormatted = endDate.getUTCMonth() + 1 + "/" + endDate.getUTCDate() + "/" + endDate.getUTCFullYear();
                var startDate = new Date();
                startDate.setMonth(startDate.getMonth() - 1);
                var startDateFormatted = startDate.getUTCMonth() + 1 + "/" + startDate.getUTCDate() + "/" + startDate.getUTCFullYear();
                window.location.href = "<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=" + "<%=h(user)%>" +
                        "&start=" + startDateFormatted + "&end=" + endDateFormatted;
            });
        </script>

    </div>
    <%if(showSingleUser){%>
        <div class="centeredContent">
            <div id="duration" class="c3chart"></div>
            <div id="passes" class="c3chart"></div>
            <div id="memory" class="c3chart"></div>
            <div id="failGraph" class="c3chart"></div>
        </div>
    <%}%>
    <div class="centeredContent">
        <table  class="decoratedtable">
            <tr>
                <td></td>
                <%if(!showSingleUser){%><td>User<td/><%}%>
                <td>Date</td>
                <td>Duration</td>
                <td>Passes</td>
                <td>Failures</td>
                <td>Leaks</td>
                <td>OS</td>
                <td>Revision</td>
            </tr>
            <%  RunDetail[] allRuns = data.getRuns();
                TreeSet<RunDetail> sortedRuns = new TreeSet<>();
                TreeMap<String, Integer> userFailCount = new TreeMap<>();
                for(RunDetail r: allRuns)
                    sortedRuns.add(r);
                NavigableSet<RunDetail> resort = sortedRuns.descendingSet();
                for(RunDetail run: resort) {
                    String key = run.getUserName();
                    if(!userFailCount.containsKey(key))
                        userFailCount.put(key, 0);
                    userFailCount.put(key, userFailCount.get(key) + run.getFailedtests());
            %>
            <tr>
                <td><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(run.getId())%>">run details</a></td>
                <%if(!showSingleUser){%><td><%=h(run.getUsername())%></td><%}%>
                <td><%=h(df.format(run.getPostTime()))%></td>
                <td><%=h(run.getDuration())%></td>
                <td><%=h(run.getPassedtests())%></td>
                <td><%=h(run.getFailedtests())%></td>
                <td><%=h(run.getLeakedtests())%></td>
                <td><%=h(run.getOs())%></td>
                <td><%=h(run.getRevision())%></td>
                <td><a class="trainset" runId="<%=h(run.getId())%>" runTrained="<%=h(run.isTrainRun())%>">
                    <%=h((run.isTrainRun()) ? "Remove from training set" : "Add to training set")%>
                </a></td>
            </tr>
            <%}%>
        </table>
        <table>
            <tr><td>Username</td><td>Failures</td></tr>
            <%  Iterator it = userFailCount.entrySet().iterator();
                 while (it.hasNext()) {
                        Map.Entry pair = (Map.Entry)it.next();
                        System.out.println(pair.getKey() + " = " + pair.getValue());
                        it.remove(); // avoids a ConcurrentModificationException   %>
                    <tr><td><%=h(pair.getKey())%></td><td><%=h(pair.getValue())%></td>       </tr>
                     <%}%>
        </table>
    </div>


<% }%>
    </div>
</div>

<%if(data != null) { %>
<script type="text/javascript">
    function refreshPage() {
        var dateRange = $('#jrange input').val();
        var dates = dateRange.split(" - ");
        var startDate = dates[0];
        var endDate = dates[1];
        if (startDate != null && endDate != null) {
            window.location.href = "<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>start=" + startDate + "&end=" + endDate + "&user=<%=h(user)%>";
        }
    }
   initDatePicker(refreshPage);
</script>
<% } %>
<!--Handels add/remove from training  data set-->
<script>
    $('.trainset').click(function() {
        var runId = this.getAttribute("runId");
        var isTrainRun = this.getAttribute("runTrained") == "true";
        var trainObj = this;
        $.getJSON('<%=h(new ActionURL(TestResultsController.TrainRunAction.class, c))%>runId='+runId+'&train='+!isTrainRun, function(data){
            if(data.Success) {
                trainObj.setAttribute("runTrained", !isTrainRun);
                if(!isTrainRun)
                    trainObj.innerHTML = "Remove from training set";
                else
                    trainObj.innerHTML = "Add to training set";
            } else {
                alert(data);
            }
        });
    })
</script>
<!--Javascript which uses c3 & d3js to paint charts with given trendJson data-->
<%if(trendsJson != null) {%>
<script src="<%=h(contextPath)%>/TestResults/js/generateTrendCharts.js"></script>
<script type="text/javascript">
    var trendsJson = jQuery.parseJSON( <%= q(trendsJson.toString()) %> );
    generateTrendCharts(trendsJson, false);
</script>
<%}%>

