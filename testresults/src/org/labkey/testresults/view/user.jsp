<%@ page import="org.json.old.JSONObject" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestResultsController.ShowRunAction" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.User" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
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

    User userObj = data.getUsers().length == 1 ? data.getUsers()[0] : null;

    HttpServletRequest req = getViewContext().getRequest();
    String startDate = req.getParameter("start");
    String endDate = req.getParameter("end");
    String user = req.getParameter("user");
    boolean showSingleUser = user != null && !user.equals("");
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    Date today = new Date();
    if (startDate == null)
        startDate = df.format(today);
    if (endDate == null)
        endDate = df.format(today);
    String dataInclude = req.getParameter("datainclude");
    if (dataInclude == null ||
        (!dataInclude.equalsIgnoreCase("date") && !dataInclude.equalsIgnoreCase("train") && !dataInclude.equalsIgnoreCase("both")))
        dataInclude = "date";

    JSONObject trendsJson = null;
    if (data.getRuns().length > 0) {
        trendsJson = data.getTrends();
    }
    Container c = getViewContext().getContainer();
    HashSet<Long> trainRuns = new HashSet<>();

    TreeSet<RunDetail> sortedRuns = new TreeSet<>();
    TreeMap<String, Integer> userFailCount = new TreeMap<>();
    Calendar cal = Calendar.getInstance();
    for (RunDetail r: data.getRuns()) {
        sortedRuns.add(r);
        if (r.isTrainRun())
        {
            trainRuns.add(TestsDataBean.getGroupDate(r.getPostTime()).getTime());
        }
    }
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<link rel="stylesheet" href="<%=h(contextPath)%>/TestResults/css/c3.min.css">
<script src="<%=h(contextPath)%>/TestResults/js/d3.min.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/multiselect.datepicker.js"></script>
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<div id="content">
    <%@include file="menu.jsp" %>
<%--    <form action="<%=h(new ActionURL(TestResultsController.PostAction.class, c))%>" method="post" enctype="multipart/form-data">--%>
<%--        <labkey:csrf/>--%>
<%--        <input type="file" name="xml_file"><input type="submit" value="submit">This form is meant to parse and store xml files into the database--%>
<%--    </form>--%>
    <%
        User[] users = TestResultsController.getUsers(null, null);
        Arrays.sort(users, Comparator.comparing(User::getUsername)); %>
    <div style="margin: 12px 0;">
        <select id="users">
            <option disabled selected> -- select an option -- </option>
            <% for (User u: users) { %>
            <option value="<%=h(u.getUsername())%>"<% if (u.getUsername().equals(user)) { %> selected<% } %>><%=h(u.getUsername())%></option>
            <% } %>
        </select>
    </div>
    <!--If data is not null (runs exist for user in selected date range)-->
    <% if (data != null && data.getRuns().length >= 0) { %>
    <div id="datePickers" style="margin: 12px 0;">
        <strong>Date range</strong><br>
        <div id="jrange" class="dates">
            <input value="<% if (startDate.equals(endDate)) { %><%=h(startDate)%><% } else { %><%=h(startDate + " - " + endDate) %><% } %>">
            <div></div>
        </div>
    </div>
    <div style="margin: 12px 0;">
        <strong>Data to include</strong><br>
        <select id="data-include">
            <option value="date"<% if (dataInclude.equalsIgnoreCase("date")) { %> selected<% } %>>Date range</option>
            <option value="train"<% if (dataInclude.equalsIgnoreCase("train")) { %> selected<% } %>>Training data</option>
            <option value="both"<% if (dataInclude.equalsIgnoreCase("both")) { %> selected<% } %>>Both</option>
        </select>
    </div>
    <div style="margin: 12px 0;">
        <input id="fill-missing" type="checkbox">
        <label for="fill-missing" style="font-weight: bold;">Include missing dates</label>
    </div>
    <% } %>
    <% if (data == null) { %>
    <p>User, <%=h(user)%> was selected but there are no runs in the current date selection matching the specified user.</p>
    <% } %>

    <%if (data != null && data.getRuns().length > 0) {%>
    <div id="headerContent">
        <h2><%=h(user)%></h2>
        <%
            String headerDate = startDate;
            if (getViewContext().getRequest().getParameter("end") != null)
                headerDate += " - " + endDate;
        %>
        <p><%=h(headerDate)%></p>
        <p>
            <a id="quickview-day" style="cursor: pointer;">DAY</a> |
            <a id="quickview-week" style="cursor: pointer;">WEEK</a> |
            <a id="quickview-month" style="cursor: pointer;">MONTH</a> |
            <a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, c).setFragment("user-anchor-" + user))%>">training data</a>
        </p>
    </div>

    <% if (showSingleUser) { %>
        <div style="width: 100%; display: flex;">
            <div id="memory" style="flex: 50%;"></div>
            <div id="passes" style="flex: 50%;"></div>
        </div>
        <div style="width: 100%; display: flex;">
            <div id="duration" style="flex: 50%;"></div>
            <div id="failGraph" style="flex: 50%;"></div>
        </div>
    <% } %>
    <div class="centeredContent">
        <table class="decoratedtable">
            <tr>
                <td></td>
                <%if (!showSingleUser) { %><td>User</td><% } %>
                <td>Date</td>
                <td>Duration</td>
                <td>Passes</td>
                <td>Memory</td>
                <td>Failures</td>
                <td>Leaks</td>
                <td>OS</td>
                <td>Revision</td>
            </tr>
            <% for (RunDetail run: sortedRuns.descendingSet()) {
                    String key = run.getUserName();
                    if (!userFailCount.containsKey(key))
                        userFailCount.put(key, 0);
                    userFailCount.put(key, userFailCount.get(key) + run.getFailedtests()); %>
                <tr class="run-row" data-run-id="<%=run.getId()%>" data-timestamp="<%=run.getPostTime().getTime()%>">
                    <td><a href="<%=h(urlFor(ShowRunAction.class).addParameter("runId", run.getId()))%>">run details</a></td>
                    <% if (!showSingleUser) { %><td><%=h(run.getUserName())%></td><% } %>
                    <td><%=h(df.format(run.getPostTime()))%></td>
                    <td><%=run.getDuration()%></td>
                    <td><%=run.getPassedtests()%></td>
                    <td><%=run.getAveragemem()%></td>
                    <td><%=run.getFailedtests()%></td>
                    <td><%=run.getLeakedtests()%></td>
                    <td><%=h(run.getOs())%></td>
                    <td><%=h(run.getRevisionFull())%></td>
                    <td><a class="trainset" runId="<%=run.getId()%>" runTrained="<%=run.isTrainRun()%>" style="cursor:pointer;">
                        <%=h((run.isTrainRun()) ? "Remove from training set" : "Add to training set")%>
                    </a></td>
                </tr>
                <% } %>
        </table>
        <table>
            <tr><td>Username</td><td>Failures</td></tr>
            <%
                Iterator<Map.Entry<String, Integer>> it = userFailCount.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Integer> pair = it.next();
                    it.remove(); // avoids a ConcurrentModificationException %>
            <tr><td><%=h(pair.getKey())%></td><td><%=h(pair.getValue())%></td></tr>
                <% } %>
        </table>
    </div>


<% } %>
    </div>

<script>
    function paramRedirect(startDate, endDate) {
        if (!startDate) {
            var dateRange = $('#jrange input').val();
            var dates = dateRange.split("-").map(s => s.trim()).filter(s => s != "");
            switch (dates.length) {
                case 1:
                    startDate = endDate = dates[0];
                    break;
                case 2:
                    startDate = dates[0];
                    endDate = dates[1];
                    break;
                default:
                    return;
            }
        }
        if (!endDate) {
            endDate = <%=q(df.format(today))%>;
        }

        let url = <%=jsURL(new ActionURL(TestResultsController.ShowUserAction.class, c))%>;
        url.searchParams.set('user', $("#users").val() || "");
        url.searchParams.set('start', startDate);
        url.searchParams.set('end', endDate);
        url.searchParams.set('datainclude', $("#data-include").val());
        window.location.href = url;
    }

    $('#users').change(function() { paramRedirect(); });
    $('#data-include').change(function() { paramRedirect(); });
    initDatePicker(paramRedirect);

    $('#quickview-day').click(function() {
        paramRedirect(<%=q(df.format(today))%>);
    });
    $('#quickview-week').click(function() {
        <% cal.setTime(today);
        cal.add(Calendar.DATE, -7); %>
        paramRedirect(<%=q(df.format(cal.getTime()))%>);
    });
    $('#quickview-month').click(function() {
        <% cal.setTime(today);
        cal.add(Calendar.MONTH, -1); %>
        paramRedirect(<%=q(df.format(cal.getTime()))%>);
    });

    <!--Handles add/remove from training data set-->
    $('.trainset').click(function() {
        var runId = this.getAttribute("runId");
        var isTrainRun = this.getAttribute("runTrained") == "true";
        var trainObj = this;
        trainObj.innerHTML = "Loading...";
        var csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};
        let url = <%=jsURL(new ActionURL(TestResultsController.TrainRunAction.class, c))%>;
        url.searchParams.set('runId', runId);
        url.searchParams.set('train', !isTrainRun);
        $.post(url.toString(), csrf_header, function(data){
            if (data.Success) {
                trainObj.setAttribute("runTrained", !isTrainRun);
                trainObj.innerHTML = !isTrainRun ? "Remove from training set" : "Add to training set";
            } else {
                alert(data);
            }
        }, "json");
    });
</script>
<!--Javascript which uses c3 & d3js to paint charts with given trendJson data-->
<% if (trendsJson != null) { %>
<script src="<%= h(contextPath) %>/TestResults/js/generateTrendCharts.js"></script>
<script type="text/javascript">
    function generateCharts() {
        var trendsJson = jQuery.parseJSON( <%= q(trendsJson.toString()) %> );
        var memData = null;
        var runData = null;
        var chartClickPoint = null;

        <% if (userObj != null) { %>
        memData = {
            mean: <%= userObj.getMeanmemory() %>,
            stddev: <%= userObj.getStddevmemory() %>,
            warnBound: <%= data.getWarningBoundary() %>,
            errorBound: <%= data.getErrorBoundary() %>
        };
        runData = {
            mean: <%= userObj.getMeantestsrun() %>,
            stddev: <%= userObj.getStddevtestsrun() %>,
            warnBound: <%= data.getWarningBoundary() %>,
            errorBound: <%= data.getErrorBoundary() %>
        };
        chartClickPoint = function(obj) {
            var timestamp = typeof obj.x === 'number' ? trendsJson.dates[obj.x + 1] : obj.x.getTime();
            var minRow = null;
            var minDiff = 4102444799999;
            $(".run-row").each(function() {
                var diff = Math.abs(timestamp - $(this).data("timestamp"));
                if (diff < minDiff) {
                    minRow = $(this);
                    minDiff = diff;
                }
            });
            if (minRow) {
                let url = <%=jsURL(new ActionURL(ShowRunAction.class, c))%>;
                url.searchParams.set('runId', minRow.data('run-id'));
                location.href = url.toString();
            }
        };
        <% } %>

        generateTrendCharts(trendsJson, {
            trainRuns: new Set(<%= h(trainRuns) %>),
            showSubChart: false,
            fillMissing: $("#fill-missing").prop("checked"),
            memData: memData,
            runData: runData,
            clickHandler: chartClickPoint
        });
    }
    generateCharts();
    $('#fill-missing').change(generateCharts);
</script>
<% } %>
