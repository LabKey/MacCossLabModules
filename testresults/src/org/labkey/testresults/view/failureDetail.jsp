<%@ page import="org.json.old.JSONObject" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.TestFailDetail" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.testresults.model.TestLeakDetail" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.testresults.model.RunProblems" %>
<%@ page import="org.labkey.testresults.model.TestMemoryLeakDetail" %>
<%@ page import="org.labkey.testresults.model.TestHandleLeakDetail" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    /*
      User: Yuval Boss, yuval(at)uw.edu
      Date: 10/05/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    String failedTest = getViewContext().getRequest().getParameter("failedTest");

    Container c = getContainer();
    RunDetail[] runs = data.getStatRuns();
    RunProblems problems = new RunProblems(runs);
    Map<String, List<TestFailDetail>> languageFailures = new TreeMap<>();
    languageFailures.put(failedTest, Arrays.asList(data.getFailures()));
    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(languageFailures);

    DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    DateFormat dfEnd = new SimpleDateFormat("MM/dd/yyyy");
    DateFormat jsDf = new SimpleDateFormat("yyyy-MM-dd");

    // Generate JSON data.
    JSONObject problemData = new JSONObject();

    // get some graph data
    Map<String, JSONObject> dates = new TreeMap<>(); // maps dates to count of failures per run
    Calendar cal = Calendar.getInstance();
    cal.setTime(data.getStartDate());
    cal.add(Calendar.DATE, 1); // posted next day
    int cutoffMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

    JSONObject graphData = new JSONObject();
    String endDateString = jsDf.format(data.getEndDate());
    for (; ; cal.add(Calendar.DATE, 1)) {
        String dateString = jsDf.format(cal.getTime());
        dates.put(dateString, null);
        if (dateString.equals(endDateString))
            break;
    }
    graphData.put("dates", dates.keySet());

    // Generate runData JSON array.
    int numFailures = 0;
    int numLeaks = 0;
    int numHangs = 0;
    JSONArray runData = new JSONArray();
    for (RunDetail run : runs)
    {
        JSONObject runObj = new JSONObject();
        runObj.put("id", run.getId());
        runObj.put("user", run.getUserName());
        runObj.put("href", urlFor(TestResultsController.ShowRunAction.class).addParameter("runId", run.getId()));
        runObj.put("time", df.format(run.getPostTime()));
        runObj.put("duration", run.getDuration());
        runObj.put("os", run.getOs());
        runObj.put("revision", run.getRevisionFull());

        JSONArray failData = new JSONArray();
        for (TestFailDetail failure : problems.getFailures(run, failedTest))
        {
            numFailures++;
            JSONObject failObj = new JSONObject();
            failObj.put("pass", failure.getPass());
            failObj.put("language", failure.getLanguage());
            failObj.put("trace", failure.getStacktrace());
            failData.put(failObj);
        }
        runObj.put("failures", failData);

        JSONArray leakData = new JSONArray();
        for (TestLeakDetail leak : problems.getLeaks(run, failedTest))
        {
            numLeaks++;
            JSONObject leakObj = new JSONObject();
            if (leak instanceof TestHandleLeakDetail)
            {
                leakObj.put("handles", ((TestHandleLeakDetail)leak).getHandles());
                leakData.put(leakObj);
            }
            else if (leak instanceof TestMemoryLeakDetail)
            {
                leakObj.put("bytes", ((TestMemoryLeakDetail)leak).getBytes());
                leakData.put(leakObj);
            }
        }
        runObj.put("leaks", leakData);

        boolean hang = problems.hasHang(run, failedTest);
        if (hang)
        {
            numHangs++;
        }
        runObj.put("hang", hang);

        runData.put(runObj);

        // Update graph data.
        cal.setTime(run.getPostTime());
        if (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) >= cutoffMinute) {
            cal.add(Calendar.DATE, 1);
        }

        String dateKey = jsDf.format(cal.getTime());
        JSONObject dateObj = dates.get(dateKey);
        if (dateObj == null)
        {
            dateObj = new JSONObject();
            dates.put(dateKey, dateObj);
        }

        int thisFailures = problems.getFailures(run, failedTest).length;
        if (thisFailures > 0)
        {
            dateObj.put("failures", (dateObj.containsKey("failures") ? dateObj.getInt("failures") : 0) + thisFailures);
        }

        int thisLeaks = problems.getLeaks(run, failedTest).length;
        if (thisLeaks > 0)
        {
            dateObj.put("leaks", (dateObj.containsKey("leaks") ? dateObj.getInt("leaks") : 0) + thisLeaks);
        }

        if (hang)
        {
            dateObj.put("hangs", (dateObj.containsKey("hangs") ? dateObj.getInt("hangs") : 0) + 1);
        }
    }

    problemData.put("runData", runData);

    JSONObject dateData = new JSONObject();
    // Filter dates without problems.
    dates = dates.entrySet().stream().filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    dateData.putAll(dates);
    graphData.put("dateData", dateData);

    problemData.put("graphData", graphData);

    // Determine initial problem type.
    String problemType = getViewContext().getRequest().getParameter("problemType");
    if (problemType == null || (!problemType.equals("failures") && !problemType.equals("leaks") && !problemType.equals("hangs")))
    {
        if (numFailures > 0)
            problemType = "failures";
        else if (numLeaks > 0)
            problemType = "leaks";
        else if (numHangs > 0)
            problemType = "hangs";
        else
            problemType = "failures";
    }
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
<style>
    input:disabled+label { color: #aaa; }
    #failurestatstable td:not(:last-child) { width: 1px; white-space: nowrap; }
    #failurestatstable td:last-child { width: auto; }
    #failurestatstable ul { list-style-type: none; margin: 0; padding: 0; }
</style>
<br />
<%
    String value = (request.getParameter("viewType"));
    if (value == null) {
        value = "firsttime";
    }
%>
<form action="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>">
    View Type: <select name="viewType" onchange="this.form.submit()">
                    <option disabled value="firsttime"<%=selected(value.equals("firsttime"))%>> -- select an option -- </option>
                    <option id="posttime" value="posttime"<%=selected(value.equals("posttime"))%>>Day</option>
                    <option id="wk" value="wk"<%=selected(value.equals("wk"))%>>Week</option>
                    <option id="mo" value="mo"<%=selected(value.equals("mo"))%>>Month</option>
                    <option id="yr" value="yr"<%=selected(value.equals("yr"))%>>Year</option>
                </select>
    <select name="failedTest" style="display:none;">
        <option id="<%=h(failedTest)%>" value="<%=h(failedTest)%>"></option>
    </select>
    <input type="hidden" name="end" value="<%=h(dfEnd.format(data.getEndDate()))%>" />
</form>
<!-- main content of page -->
<div style="display: flex; flex-direction: row;">
    <div>
        <h2><%=h(failedTest)%></h2>
        <h4>Viewing data for: <%=h(df.format(data.getStartDate()))%> - <%=h(df.format(data.getEndDate()))%></h4>
        <div id="problem-type-selection">
            <input type="radio" id="problem-type-failure" name="problem_type" value="failures" <%=disabled(numFailures == 0)%>>
            <label for="problem-type-failure">Failures<% if (numFailures > 0) { %> (<%=numFailures%>)<% } %></label>
            <br>
            <input type="radio" id="problem-type-leak" name="problem_type" value="leaks" <%=disabled(numLeaks == 0)%>>
            <label for="problem-type-leak">Leaks<% if (numLeaks > 0) { %> (<%=numLeaks%>)<% } %></label>
            <br>
            <input type="radio" id="problem-type-hang" name="problem_type" value="hangs" <%=disabled(numHangs == 0)%>>
            <label for="problem-type-hang">Hangs<% if (numHangs > 0) { %> (<%=numHangs%>)<% } %></label>
        </div>
    </div>
    <div id="piechart" style="width: 200px; height: 200px;"></div>
    <div style="flex-grow: 1;"></div>
</div>

<div id="failGraph" style="width: 100%; height: 320px;"></div>

<div>
    <input type="checkbox" id="show-stack-traces" <%=checked(numFailures <= 10)%>>
    <label for="show-stack-traces">Show stack traces</label>
</div>

<table class="tablesorter-default tablesorter" id="failurestatstable" style="width: 100%;">
    <thead>
    <tr>
        <th>User</th>
        <th>Post Time</th>
        <th>Duration</th>
        <th>OS</th>
        <th>Rev</th>
        <th id="col-problem"></th>
    </tr>
    </thead>
    <tbody>
    </tbody>
</table>

<script type="text/javascript">
$(document).ready(function() {
    const problemData = <%=problemData.getJavaScriptFragment(0)%>;

    // Generate date chart.
    let dateChart = c3.generate({
        bindto: '#failGraph',
        data: {
            x: 'x',
            columns: [],
            type: 'bar'
        },
        size: { width: 1500 },
        bar: { width: { ratio: 0.3 } },
        subchart: { show: false, size: { height: 20 } },
        axis: {
            x: {
                min: problemData.graphData.dates[0],
                max: problemData.graphData.dates[problemData.graphData.dates.length - 1],
                type: 'timeseries',
                localtime: false,
                tick: { fit: true, format: '%m/%d' }
            },
            // y: { tick: { values: %=graphYTicks.getJavaScriptFragment(0)% } }
        }
    });

    // Generate pie chart.
    c3.generate({
        bindto: '#piechart',
        data: {
            columns: [
                <%
                    Map<String, Double> lang =languageBreakdown.get(failedTest);
                    for (String l: lang.keySet()) {
                        double percent = lang.get(l) * 100;
                %>
                ['<%=h(l)%>', <%=(int)percent%>],
                <% } %>  ],
            type: 'pie'
        },
        color: { pattern: ['#FFB82E', '#A078A0', '#20B2AA', '#F08080', '#FF8B2E'] },
        pie: { label: { format: function (value, ratio, id) { return ""; } } }
    });

    const stackTraceToggle = function() {
        if ($(this).is(":checked")) {
            $(".stack-trace").show();
        } else {
            $(".stack-trace").hide();
        }
    };
    $("#show-stack-traces").change(stackTraceToggle).trigger("change");

    const changeProblemType = function() {
        let filterFunc = null;
        let headerName = null;
        let displayFunc = null;
        let jsonKey = null;
        switch (this.value) {
            case "failures":
                filterFunc = run => run.failures.length > 0;
                headerName = "Failures";
                displayFunc = run => {
                    return "<ul>" + run.failures.map(f => "<li>" +
                            "Pass " + f.pass.toString() + " (" + f.language + ") ---" +
                            '<pre class="stack-trace">' + f.trace + "</pre>"+
                            "</li>").join() + "</ul>";
                };
                jsonKey = "failures";
                break;
            case "leaks":
                filterFunc = run => run.leaks.length > 0;
                headerName = "Leaks";
                displayFunc = run => {
                    return "<ul>" + run.leaks.map(l => "<li>" +
                            (l.handles ? l.handles.toString() + " handles" : l.bytes.toString() + " bytes") +
                            "</li>").join() + "</ul>";
                };
                jsonKey = "leaks";
                break;
            case "hangs":
                filterFunc = run => run.hang;
                headerName = "Hang";
                displayFunc = run => run.hang ? "&check;" : "-";
                jsonKey = "hangs";
                break;
            default:
                return;
        }

        document.getElementById("col-problem").innerText = headerName;
        let tbody = document.querySelector("#failurestatstable tbody");
        while (tbody.firstChild) tbody.removeChild(tbody.firstChild);
        for (let run of problemData.runData.filter(filterFunc)) {
            let row = tbody.insertRow();
            let link = document.createElement("a");
            link.href = run.href;
            link.innerText = run.user;
            row.insertCell().appendChild(link);
            row.insertCell().innerText = run.time;
            row.insertCell().innerText = run.duration;
            row.insertCell().innerText = run.os;
            row.insertCell().innerText = run.revision;
            row.insertCell().innerHTML = displayFunc(run);
        }

        if (this.value === "failures") {
            $("#show-stack-traces").prop("disabled", false).trigger("change");
        } else {
            $("#show-stack-traces").prop("disabled", true);
        }

        $("#failurestatstable").trigger("update");

        // Load graph data.
        let problemGraphData = problemData.graphData.dates.map(d => {
                let dateDataObj = problemData.graphData.dateData[d];
                return (dateDataObj && dateDataObj[jsonKey]) ? dateDataObj[jsonKey] : 0;
            });
        dateChart.load({
            columns: [
                ['x', ...problemData.graphData.dates],
                [<%=q(failedTest)%> + " " + headerName, ...problemGraphData]
            ],
            unload: true
        });
    };
    $("#problem-type-selection input").change(changeProblemType);
    $("#problem-type-selection input[value=" + <%=q(problemType)%> + "]").prop("checked", true).trigger("change");

    // Initialize sortable table.
    $("#failurestatstable").tablesorter({
        widthFixed : true,
        resizable: true,
        widgets: ['zebra'],
        headers : { "#col-problem": { sorter: false } },
        cssAsc: "headerSortUp",
        cssDesc: "headerSortDown",
        ignoreCase: true,
        sortList: [[1, 1]], // initial sort by post time descending
        sortAppend: {
            0: [[ 1, 'a' ]] // secondary sort by date ascending
        },
        theme: 'default'
    });
});
</script>
