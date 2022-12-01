<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestResultsController.ShowRunAction" %>
<%@ page import="org.labkey.testresults.model.BackgroundColor" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.RunProblems" %>
<%@ page import="org.labkey.testresults.model.TestFailDetail" %>
<%@ page import="org.labkey.testresults.model.User" %>
<%@ page import="org.labkey.testresults.view.RunDownBean" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="static org.labkey.testresults.TestResultsModule.ViewType" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.testresults.model.TestLeakDetail" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    /*
      User: Yuval Boss, yuval(at)uw.edu
      Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>)HttpView.currentView();
    RunDownBean data = (RunDownBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");

    String viewType = data.getViewType();
    if (viewType == null || viewType.equals(""))
        viewType = ViewType.MONTH;
    String viewTypeWord = "Month";
    if (viewType.equals(ViewType.WEEK))
        viewTypeWord = "Week";
    if (viewType.equals(ViewType.YEAR))
        viewTypeWord = "Year";

    Date selectedDate = data.getEndDate();
    Date yesterday = new Date(selectedDate.getTime() - (1000 * 60 * 60 * 24));
    Date tomorrow = new Date(selectedDate.getTime() + (1000 * 60 * 60 * 24));

    RunDetail[] dayRuns = data.getRunsByDate(selectedDate);
    Arrays.sort(dayRuns);
    Map<User, List<RunDetail>> statRunUserMap = data.groupRunsByUser(dayRuns);
    User[] missingUsers = data.getMissingUsers(dayRuns);
    // Calculates Mean, Min, Max table
    Map<String, List<TestFailDetail>> topFailures = data.getTopFailures(10, true); // top 10 failures
    Map<String, List<TestLeakDetail>> topLeaks = data.getTopLeaks(10, true); // top 10 leaks

    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(topFailures); // test name mapped to language and percents

    RunProblems problems = new RunProblems(dayRuns);
    RunDetail[] problemRuns = problems.getRuns();

    JSONObject memoryChartData = data.getTodaysCompactMemoryJson(selectedDate);
    JSONObject trendsJson = data.getTrends();
    Container c = getViewContext().getContainer();
    DateFormat dfMDHM = new SimpleDateFormat("MM/dd HH:mm:ss");
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<link rel="stylesheet" href="<%=h(contextPath)%>/TestResults/css/c3.min.css">
<script src="<%=h(contextPath)%>/TestResults/js/d3.min.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/jquery.tablesorter.js"></script>
<style>
    .rundown-unknown { background: <%= h(BackgroundColor.unknown) %> !important; }
    .rundown-pass { background: <%= h(BackgroundColor.pass) %> !important; }
    .rundown-warn { background: <%= h(BackgroundColor.warn) %> !important; }
    .rundown-error { background: <%= h(BackgroundColor.error) %> !important; }
    .ui-tooltip { white-space: pre-line; } /* use \n as line break in tooltip */
</style>

<div id="container">
<%@include file="menu.jsp" %>
<div id="content">
    <p>
        <a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c).addParameter("end", df.format(yesterday)))%>"><<<</a>
        Date: <input type="text" id="datepicker" size="30">
        <a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c).addParameter("end", df.format(tomorrow)))%>">>>></a>
    </p>
    <div id="headerContent">
    </div>
    <% if (!statRunUserMap.keySet().isEmpty() || missingUsers.length > 0) { %>
    <div id="todaysContent">
        <center><h4>Today</h4></center>
        <div class="centeredContent">
            <%
                int errorRuns = 0;
                int warningRuns = 0;
                int goodRuns = 0;
                int warningBoundary = data.getWarningBoundary();
                int errorBoundary = data.getErrorBoundary();
                if (!statRunUserMap.keySet().isEmpty()) {
            %>
                <div id="memoryGraph" style="margin: auto; height: 350px; width: 1024px;"></div>
            <% } %>
            <div style="display: flex; flex-direction: column;">
                <div>
                    <p style="font-weight: 400; font-size: large;">Note: the warning limit boundary is <%=warningBoundary%> and the error limit boundary is <%=errorBoundary%></p>
                </div>
                <div style="display: flex; flex-direction: row;">
                    <table class="decoratedtable tablesorter" id="generalstatstable" style="float: left;">
                        <thead style="display: block;">
                        <tr>
                            <th>User</th>
                            <th>Post Time</th>
                            <th>Duration</th>
                            <th>Passed Tests</th>
                            <th>Average Memory</th>
                            <th>Failures</th>
                            <th>Leaks</th>
                            <th>Git Hash</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody style="max-height: 300px; overflow: scroll; display: block;">
                        <%
                            for (User u : statRunUserMap.keySet()) {
                                for (RunDetail run : statRunUserMap.get(u)) {
                        %>
                        <tr class="highlightrun highlighttr-<%=run.getId()%>">
                            <%
                                int passes =  run.getPassedtests();
                                int failures = run.getFailedtests();
                                int testmemoryleaks = run.getLeaks().length;
                                int runStatus = 0; // -1 = unknown, 0 = good, 1 = warn, 2 = error
                                int durationStatus = 0;
                                int passStatus = 0;
                                int memStatus = 0;
                                int failStatus = 0;
                                int leakStatus = 0;

                                String title = "";
                                if (u.getMeanmemory() == 0d || u.getMeantestsrun() == 0d || !u.isActive()) // IF NO TRAINING DATA FOR USER or INACTIVE
                                {
                                    title = (u.getMeanmemory() == 0d || u.getMeantestsrun() == 0d)
                                            ? "No training data for " + u.getUsername()
                                            : "Deactivated " + u.getUsername();
                                    runStatus = -1;
                                }
                                else
                                {
                                    if (!u.fitsRunCountTrainingData(run.getPassedtests(), errorBoundary))
                                        passStatus = 2;
                                    else if (!u.fitsRunCountTrainingData(run.getPassedtests(), warningBoundary))
                                        passStatus = 1;
                                    if (!u.fitsMemoryTrainingData(run.getAverageMemory(), errorBoundary))
                                        memStatus = 2;
                                    else if (!u.fitsMemoryTrainingData(run.getAverageMemory(), warningBoundary))
                                        memStatus = 1;
                                    if (passStatus == 2 || memStatus == 2)
                                        runStatus = 2;
                                    else if (passStatus == 1 || memStatus == 1)
                                        runStatus = 1;

                                    if (run.getDuration() < 539) {   // 1 minute tolerance
                                        durationStatus = 2;
                                        runStatus = 2;
                                    } else if (run.getHang() != null) {
                                        durationStatus = 2;
                                        runStatus = 2;
                                    }

                                    if (run.getFailures().length > 0) {
                                        failStatus = 2;
                                        runStatus = 2;
                                    }
                                    if (run.getLeaks().length > 0) {
                                        leakStatus = 2;
                                        runStatus = 2;
                                    }
                                }

                                switch (runStatus)
                                {
                                    case 0: goodRuns++; break;
                                    case 1: warningRuns++; break;
                                    case 2: errorRuns++; break;
                                }
                            %>

                            <td class="<% if (runStatus == -1) { %>rundown-unknown
                                <% } else if (runStatus == 0) { %>rundown-pass
                                <% } else if (runStatus == 1) { %>rundown-warn
                                <% } else if (runStatus == 2) { %>rundown-error
                                <% } %>"
                                data-sort-value="<%=h(run.getUserName())%>">
                                <a title="<%=h(title)%>" style="color: #000 !important; font-weight: 400;" href="<%=h(new ActionURL(ShowRunAction.class, c).addParameter("runId", run.getId()))%>" target="_blank">
                                    <%=h(run.getUserName() + "(" + run.getId() + ")")%>
                                </a>
                            </td>
                            <td style="font-size: 11px;" data-sort-value="<%=run.getPostTime().getTime()%>"><%=h(dfMDHM.format(run.getPostTime()))%></td>
                            <td class="<% if (durationStatus > 0) { %>rundown-error<% } %>">
                                <%=run.getDuration()%>
                                <% if (run.getHang() != null) { %><img src='<%=h(contextPath)%>/TestResults/img/hangicon.png'><% } %>
                            <td title="<%=h(u.runBoundHtmlString(warningBoundary, errorBoundary))%>"
                                class="rundown-user-passes <% if (passStatus == 1) { %>rundown-warn<% } else if (passStatus == 2) { %>rundown-error<% } %>">
                                <%=passes%>
                            </td>
                            <td title="<%=h(u.memBoundHtmlString(warningBoundary, errorBoundary))%>"
                                class="rundown-user-mem <% if (memStatus == 1) { %>rundown-warn<% } else if (memStatus == 2) { %>rundown-error<% } %>">
                                <%=h(run.getAverageMemory()) %>
                            </td>
                            <td class="<% if (failStatus > 0) { %>rundown-error<% } %>">
                                <%=failures%>
                                <% if (failStatus > 0) { %><img src='<%=h(contextPath)%>/TestResults/img/fail.png'><% } %>
                            </td>
                            <td class="<% if (leakStatus > 0) { %>rundown-error<% } %>">
                                <%=testmemoryleaks%>
                                <% if (leakStatus > 0) { %><img src='<%=h(contextPath)%>/TestResults/img/leak.png'><% } %>
                            </td>
                            <td>
                                <%=h(run.getGitHash())%>
                            </td>
                            <td>
                                <a style="cursor: pointer;" runid="<%=run.getId()%>" train="<%=h(run.isTrainRun() ? "false" : "true")%>" class="traindata"><%=h(run.isTrainRun() ? "Untrain" : "Train")%></a>
                            </td>
                        </tr>
                        <% }
                        }
                        for (User user: missingUsers) { %>
                        <tr>
                            <td style="background: <%=h(BackgroundColor.error)%>;"><%=h(user.getUsername())%></td>
                            <td style="background: <%=h(BackgroundColor.error)%>;">MISSING RUN</td>
                            <td style="background: <%=h(BackgroundColor.error)%>;" colspan="6"></td>
                            <td style="background: <%=h(BackgroundColor.error)%>;">
                                <a style="cursor: pointer;" data-user="<%=user.getId()%>" data-active="true" class="activate-toggle">Deactivate user</a>
                            </td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                    <% if (problems.any()) { %>
                    <table class="decoratedtable" style="float: left;">
                        <thead style="display: block;">
                        <tr>
                            <td style="width: 200px; overflow: hidden; padding: 0;">
                                Fail: <img src="<%=h(contextPath)%>/TestResults/img/fail.png">
                                | Leak: <img src="<%=h(contextPath)%>/TestResults/img/leak.png">
                                | Hang: <img src="<%=h(contextPath)%>/TestResults/img/hangicon.png">
                            </td>
                            <% for (RunDetail run : problemRuns) { %>
                            <td style="max-width: 60px; width: 60px; overflow: hidden; text-overflow: ellipsis; padding: 0;" title="<%=h(run.getUserName())%>">
                                <a href="<%=h(urlFor(ShowRunAction.class).addParameter("runId", run.getId()))%>" target="_blank">
                                    <%=h(run.getUserName())%>(<%=run.getId()%>)
                                </a>
                            </td>
                            <% } %>
                        </tr>
                        </thead>
                        <tbody style="max-height: 300px; overflow: scroll; display: block;">
                        <% for (String test : problems.getTestNames()) { %>
                        <tr>
                            <td style="width: 200px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; padding: 0;">
                                <%=link(test).href(new ActionURL(TestResultsController.ShowFailures.class, c).addParameter("end", df.format(selectedDate)).addParameter("failedTest", test)).target("_blank").clearClasses()%>
                            </td>
                            <% for (RunDetail run : problemRuns) { %>
                            <td class="highlightrun highlighttd-<%=run.getId()%>" style="width: 60px; overflow: hidden; padding: 0;">
                                <% if (problems.hasFailure(run, test)) { %><img src="<%=h(contextPath)%>/TestResults/img/fail.png"><% } %>
                                <%
                                    boolean leakMem = problems.hasMemoryLeak(run, test);
                                    boolean leakHandle = problems.hasHandleLeak(run, test);
                                    String leakCssClass = "";
                                    if (leakMem && leakHandle) leakCssClass = "matrix-leak-both";
                                    else if (leakMem) leakCssClass = "matrix-leak-mem";
                                    else if (leakHandle) leakCssClass = "matrix-leak-handle";
                                    if (!leakCssClass.isEmpty()) {
                                %>
                                <img src="<%=h(contextPath)%>/TestResults/img/leak.png" class="<%=h(leakCssClass)%>">
                                <% } %>
                                <% if (problems.hasHang(run, test)) { %><img src="<%=h(contextPath)%>/TestResults/img/hangicon.png"><% } %>
                            </td>
                            <% } %>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                    <% } %>
                </div>
            </div>
        </div>
    </div>
    <script type="text/javascript">
        $('#stats').text("<%=errorRuns%> Errors | <%=warningRuns%> Warnings | <%=goodRuns%> Passes | <%=missingUsers.length%> Missing");
    </script>
    <% } %>
    <br />
    <div id="weekContent">
        <center><h4><%=h(viewTypeWord)%></h4></center>
        <div style="width:100%; ">
            <center>( Trends for previous <%=h(viewTypeWord.toLowerCase())%> )</center>
            <center>
                <select id="viewType">
                    <option value="<%=h(ViewType.WEEK)%>" id="<%=h(ViewType.WEEK)%>">Week</option>
                    <option value="<%=h(ViewType.MONTH)%>" id="<%=h(ViewType.MONTH)%>">Month</option>
                    <option value="<%=h(ViewType.YEAR)%>" id="<%=h(ViewType.YEAR)%>">Year</option>
                </select>
            </center>
            <script type="text/javascript">
                $('#viewType').on('change', function() {
                    let url = <%=jsURL(new ActionURL(TestResultsController.BeginAction.class, c).addParameter("end", df.format(selectedDate)))%>;
                    url.searchParams.set('viewType', this.value);
                    window.location.href = url.toString();
                });
            </script>
            <!--Bar Graphs for average over past week-->
            <div style="width: 100%; display: flex;">
                <div id="memory" style="flex: 50%;"></div>
                <div id="passes" style="flex: 50%;"></div>
            </div>
            <div style="width: 100%; display: flex;">
                <div id="duration" style="flex: 50%;"></div>
                <div id="failGraph" style="flex: 50%;"></div>
            </div>
        </div>
        <br />
        <br />

        <!--Basic test run information about duration, # of passses, # of failures, and # of testmemoryleaks-->
        <div class="centeredContent">
        <% if (!topFailures.isEmpty()) { %>
            <!--Top Failures, occurrences, and language pie chart table-->
            <table class="decoratedtable" style="float:left;">
                <tr>
                    <td><h4>Top Failures</h4></td>
                    <td><h4>Occurrences</h4></td>
                    <td><h4>Language Summary</h4></td>
                </tr>
                <% for (String key: topFailures.keySet()) { %>
                <tr>
                    <td><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c).addParameter("failedTest", key).addParameter("end",df.format(selectedDate)).addParameter("viewType", viewType))%>" target="_blank"><%=h(key)%></a></td>
                    <td><%=topFailures.get(key).size()%></td>
                    <td>
                        <div id="<%=h(key)%>" class="c3chart" style="width: 120px; height: 120px;"></div>
                        <script>
                            var <%=h(key)%> = c3.generate({
                                bindto: '#<%=h(key)%>',
                                data: {
                                    columns: [
                        <%
                            Map<String, Double> lang = languageBreakdown.get(key);
                            for (String l: lang.keySet()) {
                                double percent = lang.get(l) * 100;
                        %>
                                ['<%=h(l)%>', <%=(int)percent%>],
                        <% } %> ],
                            type: 'pie',
                                onclick: function (d, i) { console.log("onclick", d, i); },
                                onmouseover: function (d, i) { console.log("onmouseover", d, i); },
                                onmouseout: function (d, i) { console.log("onmouseout", d, i); },
                                colors: {
                                    unknown: '#A078A0',
                                    ja: '#FFB82E',
                                    ch: '#20B2AA',
                                    fr: '#F08080',
                                    en: '#FF8B2E'
                                    }
                                },
                                pie: { label: { format: function (value, ratio, id) { return ""; } } }
                            });
                        </script>
                    </td>
                </tr>
                <% } %>
            </table>
            <% } %>

            <% if (!topLeaks.isEmpty()) { %>
            <table class="decoratedtable" style="float: left;">
                <tr>
                    <td><h4>Top Leaks</h4></td>
                    <td><h4>Occurrences</h4></td>
                    <td><h4>Mean Leak</h4></td>
                </tr>
                <% for (Map.Entry<String, List<TestLeakDetail>> entry: topLeaks.entrySet()) { %>
                <tr>
                    <td>
                        <%=
                            link(entry.getKey()).href(new ActionURL(TestResultsController.ShowFailures.class, c)
                                    .addParameter("viewType", viewType)
                                    .addParameter("end", df.format(selectedDate))
                                    .addParameter("failedTest", entry.getKey())
                                    .addParameter("problemType", "leaks")
                                ).target("_blank").clearClasses()
                        %>
                    </td>
                    <td><%=entry.getValue().size()%></td>
                    <td>
                        <ul style="list-style-type: none; margin: 0; padding: 0;">
                        <%
                            double leakMem = data.getLeakMemoryAverage(entry.getValue());
                            if (leakMem > 0) {
                        %>
                            <%=h(Math.round(leakMem/1000))%> kb
                        <%
                            }
                            double leakHandle = data.getLeakHandleAverage(entry.getValue());
                            if (leakHandle > 0) {
                        %>
                            <%=h(Math.round(leakHandle))%> handles
                        <% } %>
                        </ul>
                    </td>
                </tr>
                <%}%>
            </table>
            <%}%>
        </div>
    </div>

<% if (memoryChartData != null) { %>
    <script>
        var pointRatio = 30;
        var jsonObject = <%=memoryChartData.getJavaScriptFragment(0)%>;
        // start c3 chart generation
        var memoryUsageChart = c3.generate({
            bindto: '#memoryGraph',
            size: { height:350, width: 1024 },
            data: { json: jsonObject.runs },
            point: { show: false },
            axis : {
                x: { tick: { format: function (x) { return x*pointRatio; } } },
                y: { label: { text: 'Memory (MB)', position: 'outer-middle' } }
            },
            regions: function(d) {
                var regions = [];
                var last = 0;
                for(key in jsonObject["passes"]) {
                    regions.push({axis: 'x', start: last/pointRatio, end: jsonObject["passes"][key]/pointRatio, class: 'pass' + key});
                    last = jsonObject["passes"][key] + 1;
                }
                regions.push({axis: 'x', start: last/pointRatio, class: 'pass' + regions.length});
                return regions;
            },
            legend: {
                item: {
                    onmouseout: function(d) {
                        $('.highlightrun').children('td:not(:first-child), th').css('background','#fff');
                        $('.highlightrun').css('background','#fff'); // for td (no children)

                        // line hover effects
                        d3.selectAll(".c3-line").style("opacity", 1);
                    },
                    onmouseover: function (d) {
                        var re = /.*id.(\d*)\)/;
                        var m;
                        if ((m = re.exec(d)) !== null) {
                            if (m.index === re.lastIndex) {
                                re.lastIndex++;
                            }
                        }
                        var runId = m[1];

                        // line hover effects
                        d3.selectAll(".c3-line").style("opacity", 0.125);
                        $(".c3-line-"+ d.replace("(" , "\\(").replace("." , "\\.").replace(")" , "\\)")).css("opacity", 1);

                        // highlight row/column with run
                        $('.highlightrun').children('td:not(:first-child), th').css('background','#fff');
                        $('.highlightrun').css('background','#fff'); // for td (no children)
                        $('.highlighttr-' + runId).children('td:not(:first-child), th').css('background','#99ccff');
                        $('.highlighttd-' + runId).css('background','#99ccff');

                        var row = $('.highlighttr-' + runId);
                        var parent = row.parent();
                        var childPos = row.offset();
                        var parentPos = parent.offset();
                        var childOffset = {
                            top: childPos.top - parent.offset().top,
                            left: childPos.left - parentPos.left
                        };
                        if ($('.highlighttr-' + runId).length) {
                            parent.scrollTop(0);
                            parent.scrollTop(childOffset.top - (parent.height()/2));
                        }
                    },
                    // click on legend item and get redirected to user page on the date of that run
                    onclick: function (d) {
                        var re = /.*id.(\d*)\)/;
                        var m;
                        if ((m = re.exec(d)) !== null) {
                            if (m.index === re.lastIndex) {
                                re.lastIndex++;
                            }
                        }
                        var id = m[1];
                        var url = <%=jsURL(new ActionURL(ShowRunAction.class, c))%>;
                        url.searchParams.set('runId', id);
                        window.open(
                            url,
                            '_blank' // <- This is what makes it open in a new window.
                        );
                    }
                }
            },
            tooltip: {
                format: {
                    title: function (d) { return 'Test #: ' + d*pointRatio; },
                    value: function (value, ratio, id) { return value + "MB"; }
                }
            }
        });
    </script>
<% } %>
</div>
</div>

<script>
$(function() {
    const csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};

    /* Initialize datepicker */
    $("#datepicker").datepicker({
        onSelect: function(date) {
            let url = <%=jsURL(new ActionURL(TestResultsController.BeginAction.class, c))%>;
            url.searchParams.set('end', date);
            window.location.href = url;
        }
    });
    $("#anim").change(function() {
        $("#datepicker").datepicker("option", "showAnim", "fadeIn");
    });
    $("#datepicker").datepicker("setDate", "<%=h(df.format(selectedDate))%>"); // set to selected date

    /* Click event for training runs */
    $('.traindata').click(function() {
        var self = this;
        var curText = $(this).text();
        if (curText != 'Train' && curText != 'Untrain')
            return;
        var runId = self.getAttribute('runid');
        var train = self.getAttribute('train');
        var isTrain = curText == 'Train';
        $(this).text(isTrain ? 'Training...' : 'Untraining...');
        let url = <%=jsURL(new ActionURL(TestResultsController.TrainRunAction.class, c))%>;
        url.searchParams.set('runId', runId);
        url.searchParams.set('train', train);
        $.post(url.toString(), csrf_header, function(data){
            if (data.Success) {
                self.setAttribute('train', isTrain ? 'false' : 'true');
                $(self).text(isTrain ? 'Untrain' : 'Train');
                return;
            }
            alert("Failure removing run. Contact Yuval");
        }, "json");
    });

    // Click event for activate/deactivate machines
    $(".activate-toggle").click(function() {
        const activateText = "Activate user";
        const deactivateText = "Deactivate user";

        let self = this;
        if (self.innerText !== activateText && self.innerText !== deactivateText)
            return;

        self.innerText = "working...";
        let url = <%=jsURL(new ActionURL(TestResultsController.SetUserActive.class, c))%>;
        url.searchParams.set("userId", self.getAttribute("data-user"));
        url.searchParams.set("active", self.getAttribute("data-active") === "true" ? "false" : "true");
        $.post(url.toString(), csrf_header, function(data) {
            if (data.Message.toLowerCase() !== "success") {
                alert("A problem occurred: " + data.Message);
                return;
            }

            if (self.getAttribute("data-active") === "true") {
                self.innerText = activateText;
                self.setAttribute("data-active", "false");
            } else {
                self.innerText = deactivateText;
                self.setAttribute("data-active", "true");
            }
        }, "json")
    });

    // tooltips for leaks in matrix
    $(".matrix-leak-both").each(function() { $(this).attr("title", "Memory and handle leak"); });
    $(".matrix-leak-both").tooltip();
    $(".matrix-leak-mem").each(function() { $(this).attr("title", "Memory leak"); });
    $(".matrix-leak-mem").tooltip();
    $(".matrix-leak-handle").each(function() { $(this).attr("title", "Handle leak"); });
    $(".matrix-leak-handle").tooltip();
});
</script>
<script type="text/javascript">
    document.getElementById("<%=h(viewType)%>").selected = "true";
</script>

<% if (trendsJson != null) { %>
    <script src="<%=h(contextPath)%>/TestResults/js/generateTrendCharts.js"></script>
    <script type="text/javascript">
        var trendsJson = <%=trendsJson.getJavaScriptFragment(0)%>;
        generateTrendCharts(trendsJson);
    </script>
<% } %>

<script>
/* Initialize sortable table */
$(function() {
    $(".rundown-user-passes").tooltip();
    $(".rundown-user-mem").tooltip();
    $("#generalstatstable").tablesorter({
        headers: {
            0: { sorter: "text" },
            1: { sorter: "digit" },
            2: { sorter: "digit" },
            3: { sorter: "digit" },
            4: { sorter: "digit" },
            5: { sorter: "digit" },
            6: { sorter: "digit" },
            7: { sorter: "text" },
            8: { sorter: false }
        },
        cssAsc: "headerSortUp",
        cssDesc: "headerSortDown"
    });
    $("#generalstatstable").trigger("sorton", [[[1,1]]]);
});
</script>
