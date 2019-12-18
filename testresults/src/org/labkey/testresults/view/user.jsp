<%@ page import="org.json.JSONObject" %>
<%@ page import="org.json.*" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.data.statistics.StatsService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.TestResultsSchema" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="org.labkey.testresults.model.User" %>
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="org.labkey.testresults.view.RunDownBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

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

    StatsService service = StatsService.get();
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
            <li><a href="<%=h(new ActionURL(TestResultsController.ErrorFilesAction.class, c))%>" style="color:#fff;">-Posting Errors</a></li>
            <li><a href="https://skyline.gs.washington.edu/labkey/project/home/issues/begin.view?" target="_blank" title="Report bugs/Request features.  Use 'TestResults' as area when creating new issue" style="color:#fff;">-Issues</a></li>
            <img src="<%=h(contextPath)%>/TestResults/img/uw.png" id="uw">
        </ul>
    </div>
    <form action="<%=h(new ActionURL(TestResultsController.PostAction.class, c))%>" method="post" enctype="multipart/form-data">
        <labkey:csrf/>
        <input type="file" name="xml_file">
        <input type="submit" value="submit">
    </form>

    <button type="button" class="postBtn">Re-Post All</button>
    <div id="loading" style="display: none;">
        Loading...
    </div>
    <p class="text"></p>

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
        <p><a id="dayView" style="cursor:pointer;">DAY</a> | <a id="weekView" style="cursor:pointer;">WEEK</a> | <a id="monthView" style="cursor:pointer;">MONTH</a></p>
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
                <%if(!showSingleUser){%><td>User</td><%}%>
                <td>Date</td>
                <td>Duration</td>
                <td>Passes</td>
                <td>Memory</td>
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
                    <%if(!showSingleUser){%><td><%=h(run.getUserName())%></td><%}%>
                    <td><%=h(df.format(run.getPostTime()))%></td>
                    <td><%=h(run.getDuration())%></td>
                    <td><%=h(run.getPassedtests())%></td>
                    <td><%=h(run.getAveragemem())%></td>
                    <td><%=h(run.getFailedtests())%></td>
                    <td><%=h(run.getLeakedtests())%></td>
                    <td><%=h(run.getOs())%></td>
                    <td><%=h(run.getRevisionFull())%></td>
                    <td><a class="trainset" runId="<%=h(run.getId())%>" runTrained="<%=h(run.isTrainRun())%>" style="cursor:pointer;">
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
        trainObj.innerHTML = "Loading...";
        var csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};
        $.post('<%=h(new ActionURL(TestResultsController.TrainRunAction.class, c))%>runId='+runId+'&train='+!isTrainRun, csrf_header, function(data){
            if(data.Success) {
                trainObj.setAttribute("runTrained", !isTrainRun);
                if(!isTrainRun)
                    trainObj.innerHTML = "Remove from training set";
                else
                    trainObj.innerHTML = "Add to training set";
            } else {
                alert(data);
            }
        }, "json");
    })
</script>
<!--Javascript which uses c3 & d3js to paint charts with given trendJson data-->
<%if(trendsJson != null) {%>
<script src="<%=h(contextPath)%>/TestResults/js/generateTrendCharts.js"></script>
<script type="text/javascript">
    var trendsJson = jQuery.parseJSON( <%= q(trendsJson.toString()) %> );
    //post request
    console.log(trendsJson);
    generateTrendCharts(trendsJson, false);
</script>
<%}%>


<%--<%--%>
<%--    if(trendsJson != null) {%>--%>
<%--<script>--%>
<%--    var pointRatio = 30;--%>
<%--    var jsonObject = jQuery.parseJSON( <%=q(trendsJson.toString())%>);--%>
<%--    var sortedRunDetails = {};--%>
<%--    for(var key in jsonObject["runs"]) {--%>
<%--        sortedRunDetails[key] = jsonObject["runs"][key];--%>
<%--    }--%>
<%--    // start c3 chart generation--%>
<%--    var memoryUsageChart = c3.generate({--%>
<%--        bindto: '#memoryGraph',--%>
<%--        size: {--%>
<%--            height:350,--%>
<%--            width: 1024--%>
<%--        },--%>
<%--        data: {--%>
<%--            json: sortedRunDetails--%>
<%--        },--%>
<%--        point: {--%>
<%--            show: false--%>
<%--        },--%>
<%--        axis : {--%>
<%--            x : {--%>
<%--                tick: {--%>
<%--                    format: function (x) { return x*pointRatio; }--%>
<%--                }--%>
<%--            },--%>
<%--            y : {--%>
<%--                label: {--%>
<%--                    text: 'Memory (MB)',--%>
<%--                    position: 'outer-middle'--%>
<%--                }--%>
<%--            }--%>
<%--        },--%>

<%--        regions:--%>
<%--                function(d) {--%>
<%--                    var regions = [];--%>
<%--                    var last = 0;--%>
<%--                    for(key in jsonObject["passes"]) {--%>
<%--                        regions.push({axis: 'x', start: last/pointRatio, end: jsonObject["passes"][key]/pointRatio, class: 'pass' + key});--%>
<%--                        last = jsonObject["passes"][key] + 1;--%>
<%--                    }--%>
<%--                    regions.push({axis: 'x', start: last/pointRatio, class: 'pass' + regions.length});--%>
<%--                    return regions;--%>
<%--                }--%>
<%--        ,--%>
<%--        legend: {--%>
<%--            item: {--%>
<%--                onmouseout: function(d) {--%>
<%--                    $('.highlightrun').children('td:not(:first-child), th').css('background','#fff');--%>
<%--                    $('.highlightrun').css('background','#fff'); // for td (no children)--%>

<%--                    // line hover effects--%>
<%--                    d3.selectAll(".c3-line").style("opacity",1);--%>
<%--                },--%>
<%--                onmouseover: function (d) {--%>
<%--                    var re = /.*id.(\d*)\)/;--%>
<%--                    var m;--%>

<%--                    if ((m = re.exec(d)) !== null) {--%>
<%--                        if (m.index === re.lastIndex) {--%>
<%--                            re.lastIndex++;--%>
<%--                        }--%>
<%--                    }--%>
<%--                    var runId = m[1];--%>

<%--                    // line hover effects--%>
<%--                    d3.selectAll(".c3-line").style("opacity",0.2);--%>
<%--                    var k = ".c3-line-"+ d.replace("(","-").replace(".","-").replace(")","-");--%>
<%--                    //make the clicked bar opacity 1--%>
<%--                    d3.selectAll(k).style("opacity",1)--%>

<%--                    // highlight row/column with run--%>
<%--                    $('.highlightrun').children('td:not(:first-child), th').css('background','#fff');--%>
<%--                    $('.highlightrun').css('background','#fff'); // for td (no children)--%>
<%--                    $('.highlighttr-' + runId).children('td:not(:first-child), th').css('background','#99ccff');--%>
<%--                    $('.highlighttd-' + runId).css('background','#99ccff');--%>

<%--                    var row = $('.highlighttr-' + runId);--%>
<%--                    var parent = row.parent();--%>
<%--                    var childPos = row.offset();--%>
<%--                    var parentPos = parent.offset();--%>
<%--                    var childOffset = {--%>
<%--                        top: childPos.top - parent.offset().top,--%>
<%--                        left: childPos.left - parentPos.left--%>
<%--                    }--%>
<%--                    if ($('.highlighttr-' + runId).length){--%>
<%--                        parent.scrollTop(0);--%>
<%--                        parent.scrollTop( childOffset.top - (parent.height()/2) );--%>
<%--                    }--%>
<%--                },--%>
<%--                // click on legend item and get redirected to user page on the date of that run--%>
<%--                onclick: function (d) {--%>
<%--                    var re = /.*id.(\d*)\)/;--%>
<%--                    var m;--%>

<%--                    if ((m = re.exec(d)) !== null) {--%>
<%--                        if (m.index === re.lastIndex) {--%>
<%--                            re.lastIndex++;--%>
<%--                        }--%>
<%--                    }--%>
<%--                    var id = m[1];--%>
<%--                    window.open(--%>
<%--                            '<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId='+ id,--%>
<%--                            '_blank' // <- This is what makes it open in a new window.--%>
<%--                    );--%>
<%--                }--%>
<%--            }--%>
<%--        },--%>
<%--        tooltip: {--%>
<%--            format: {--%>
<%--                title: function (d) { return 'Test #: ' + d*pointRatio; },--%>
<%--                value: function (value, ratio, id) {--%>
<%--                    return value + "MB";--%>
<%--                }--%>
<%--            }--%>
<%--        }--%>

<%--    });--%>
<%--</script>--%>
<%--<%}%>--%>

