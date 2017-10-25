<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.statistics.MathStat" %>
<%@ page import="org.labkey.api.data.statistics.StatsService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.RunDetail" %>
<%@ page import="org.labkey.testresults.TestFailDetail" %>
<%@ page import="org.labkey.testresults.TestLeakDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="org.labkey.testresults.User" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.labkey.testresults.TestResultsModule.ViewType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>)HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");


    String viewType = data.getViewType();
    if(viewType == null || viewType.equals(""))
        viewType = ViewType.MONTH;
    String viewTypeWord = "Month";
    if(viewType.equals(ViewType.WEEK))
        viewTypeWord = "Week";
    if(viewType.equals(ViewType.YEAR))
        viewTypeWord = "Year";

    Date selectedDate = data.getEndDate();
    Date yesterday = new Date(selectedDate.getTime() - (1000 * 60 * 60 * 24));
    Date tomorrow = new Date(selectedDate.getTime() + (1000 * 60 * 60 * 24));

    Map<User, List<RunDetail>> statRunUserMap = data.getUserToRunsMap(selectedDate);
    User[] missingUsers = data.getMissingUsers(data.getRunsByDate(selectedDate, false));
    // Calculates Mean, Min, Max table
    Map<String, List<TestFailDetail>> topFailures = data.getTopFailures(10, true); // top 10 failures
    Map<String, List<TestLeakDetail>> topLeaks = data.getTopLeaks(10, true); // top 10 leaks
    StatsService service = ServiceRegistry.get().getService(StatsService.class);

    Map<String, Map<String, Double>> languageBreakdown = data.getLanguageBreakdown(topFailures); // test name mapped to language and percents
    Map<String, List<TestFailDetail>> todaysFailures = data.getFailedTestsByDate(selectedDate, true);
    Map<String, List<TestLeakDetail>> todaysLeaks = data.getLeaksByDate(selectedDate, true);
    JSONObject memoryChartData = data.getTodaysCompactMemoryJson(selectedDate);
    JSONObject trendsJson = data.getTrends();
    Container c = getViewContext().getContainer();
    DateFormat dfMDHM = new SimpleDateFormat("MM/dd HH:mm:ss");
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<script src="<%=h(contextPath)%>/TestResults/js/d3.v3.js"></script>
<script src="<%=h(contextPath)%>/TestResults/js/c3.min.js"></script>
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script> <!--for datpicker-->
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css"><!--for datpicker-->
<script src="<%=h(contextPath)%>/TestResults/js/jquery.tablesorter.js"></script>


<div id="container">
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
        <span id="stats">0 runs to show</span>
    </ul>
</div>
<div id="content">
    <p>
        <a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>end=<%=h(df.format(yesterday))%>"><<<</a>
        Date: <input type="text" id="datepicker" size="30">
        <a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>end=<%=h(df.format(tomorrow))%>">>>></a>
    </p>
    <div id="headerContent">
    </div>
    <%if(statRunUserMap.keySet().size() != 0 || missingUsers.length > 0) {%>
    <div id="todaysContent">
    <center><h4>Today</h4></center>
        <div class="centeredContent">
            <%if(statRunUserMap.keySet().size() != 0){%>
                <div id="memoryGraph" style="margin:auto;  height:350px; width: 1024px;"></div>
            <%}%>

            <table class="decoratedtable tablesorter" id="generalstatstable" style="float:left;">
                <thead style="display:block">
                <tr>
                    <th>User</th>
                    <th>Post Time</th>
                    <th>Duration</th>
                    <th>Passed Tests</th>
                    <th>Failures</th>
                    <th>Leaks</th>
                    <th></th>
                </tr>
                </thead>
                <tbody  style="max-height:300px; overflow: scroll; display:block;">
                <%
                    int errorRuns = 0;
                    int warningRuns = 0;
                    int goodRuns = 0;
                    for(User u : statRunUserMap.keySet()){
                        for(RunDetail run: statRunUserMap.get(u)) {
                %>
                <tr class="highlightrun highlighttr-<%=h(run.getId())%>">
                    <%
                    int passes =  run.getPassedtests();
                    int failures = run.getFailedtests();
                    int leaks = run.getLeakedtests();
                    boolean isGoodRun = true;
                    boolean highlightPasses = false;
                    String color="#00cc00;";
                    String title= "Within 2 standard deviations of training data. ";
                    // ERROR: duration < 540, failures or leaks count > 0, more then 3 standard deviations away
                    // WARNING: Between 2 and 3 standard deviations away
                    // PASS: within 2 standard deviations away as well as not an ERROR or a WARNING
                    int errorCount = errorRuns;
                    if(isGoodRun && (u.getMeanmemory() == 0d || u.getMeantestsrun() == 0d)) {  // IF NO TRAINING DATA FOR USER
                        color="#cccccc;";
                        title = "No training data for " + u.getUsername();
                        isGoodRun = false;
                    }
                    if(isGoodRun && (!u.fitsMemoryTrainingData(run.getAverageMemory(), 3) || !u.fitsRunCountTrainingData(run.getPassedtests(), 3))) {
                        color="#ff0000;";
                        isGoodRun = false;
                        highlightPasses = true;
                        title = "Outside 3 standard deviations of training data";
                        errorRuns++;
                    }
                    if(isGoodRun && (!u.fitsMemoryTrainingData(run.getAverageMemory(), 2) || !u.fitsRunCountTrainingData(run.getPassedtests(), 2))) {
                        color="#ffa500";
                        title="Between 2 and 3 standard deviations of training data";
                        isGoodRun = false;
                        highlightPasses = true;
                        warningRuns++;
                    }
                    if(run.getDuration()< 540) {
                        color="#ff0000;";
                        isGoodRun = false;
                        title = "Duration not 540 minutes. ";
                        if(errorCount == errorRuns)
                            errorRuns++;
                    }
                    if(run.getFailures().length > 0) {
                        color="#ff0000;";
                        isGoodRun = false;
                        title += "One or more tests failed. ";
                        if(errorCount == errorRuns)
                            errorRuns++;
                    }
                    if( run.getLeaks().length > 0) {
                        color="#ff0000;";
                        isGoodRun = false;
                        title += "Leaks were detected.";
                        if(errorCount == errorRuns)
                            errorRuns++;
                    }

                    if(isGoodRun)
                        goodRuns++;
                    %>
                    <td style="background: <%=h(color)%> !important;"  data-sort-value="<%=h(run.getUserName())%>">
                        <a title="<%=h(title)%>" style="color: #000 !important; font-weight:400;" href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(run.getId())%>" target="_blank">
                            <%=h(run.getUserName()) + "("+h(run.getId())+")"%>
                        <a/>
                    </td>
                    <td style="font-size:11px;" data-sort-value="<%=h(run.getPostTime().getTime())%>"><%=h(dfMDHM.format(run.getPostTime()))%></td>
                    <td ><%=h(run.getDuration())%><%if(run.hasHang()){%> <img style="width:16px; height:16px;" src='<%=h(contextPath)%>/TestResults/img/hangicon.png'><%}%>
                        <%if(run.getDuration()< 540) {%><img src='<%=h(contextPath)%>/TestResults/img/fail.png'><%}%></td>
                    <td><%=h(passes)%>
                        <%if(highlightPasses) {%><img src='<%=h(contextPath)%>/TestResults/img/fail.png'><%}%></td>
                    <td><%=h(failures)%>
                        <%if(run.getFailures().length > 0) {%><img src='<%=h(contextPath)%>/TestResults/img/fail.png'><%}%></td>
                    <td><%=h(leaks)%>
                        <%if(run.getLeaks().length > 0) {%><img src='<%=h(contextPath)%>/TestResults/img/fail.png'><%}%></td>
                    <td><a runid="<%=h(run.getId())%>" train="<%=h((run.isTrainRun()) ? "false" : "true")%>" class="traindata"><%=h((run.isTrainRun()) ? "Untrain" : "Train")%></a></td>
                </tr>
                <%}}
                if(missingUsers.length > 0) {%>
                <%
                    for(User user: missingUsers) {%>
                        <tr>
                            <td style="background:red;"><%=h(user.getUsername())%></td>
                            <td style="background:red;">MISSING RUN</td>
                            <td style="background:red;"></td>
                            <td style="background:red;"></td>
                            <td style="background:red;"></td>
                            <td style="background:red;"></td>
                            <td style="background:red;"></td>
                        </tr>
                    <%}
                    }%>
                </tbody>
            </table>

            <table class="decoratedtable" id="today-fail-leak-table" style="float:left;">
                <thead style="display:block">
                <tr >
                    <td style="width:200px; overflow:hidden; padding:0px;">Fail: <img src='<%=h(contextPath)%>/TestResults/img/fail.png'> | Leak: <img src='<%=h(contextPath)%>/TestResults/img/leak.png'></td>
                    <%for(User user: statRunUserMap.keySet()){
                        for(RunDetail run: statRunUserMap.get(user)) {
                            if(run.getFailures().length == 0 && run.getLeaks().length ==0) continue;%>
                    <td style="max-width:60px; width:60px; overflow:hidden; text-overflow: ellipsis; padding:0px;" title="<%=h(user.getUsername())%>"><%=h(user.getUsername()) + "("+run.getId()+")"%></td>
                    <%}
                    }%>
                </tr>
                </thead>
                <tbody  style="max-height:300px; overflow: scroll; display:block;">
                <!--Adds Failures to grid-->
                <%for(Map.Entry<String, List<TestFailDetail>> entry : todaysFailures.entrySet()) {%>
                <tr>
                    <td style="width:200px; max-width:200px;  overflow:hidden; text-overflow: ellipsis; padding:0px;"><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>viewType=&failedTest=<%=h(entry.getKey())%>" target="_blank"><%=h(entry.getKey())%></a></td>
                    <%for(User user: statRunUserMap.keySet()){
                        for(RunDetail run: statRunUserMap.get(user)){
                            if(run.getFailures().length == 0 && run.getLeaks().length ==0) continue;%>
                    <td class="highlightrun highlighttd-<%=h(run.getId())%>" style="width:60px; overflow:hidden; padding:0px;"><%
                    TestFailDetail matchingFail = null;

                    for(TestFailDetail fail: entry.getValue()){
                        if(fail.getTestRunId()==run.getId()) {
                            matchingFail = fail; }
                    }
                    if (matchingFail != null) {
                %>
                    <img src='<%=h(contextPath)%>/TestResults/img/fail.png'>
                        <%}%>

                </td>
                    <%}
                    }%>

                </tr>
                <%}%>
                <!--Adds Leaks to grid-->
                <%for(Map.Entry<String, List<TestLeakDetail>> entry : todaysLeaks.entrySet()) {%>
                <tr>
                    <td style="max-width:200px; overflow:hidden; padding:0px;"><%=h(entry.getKey())%></td>
                    <%for(User user: statRunUserMap.keySet()){
                        for(RunDetail run: statRunUserMap.get(user)){
                            if(run.getFailures().length == 0 && run.getLeaks().length ==0) continue;%>
                    <td  class="highlightrun highlighttd-<%=h(run.getId())%>"  style="width:60px; padding:0px;"><%
                        TestLeakDetail matchingLeak = null;

                        for(TestLeakDetail leak: entry.getValue()){
                            if(leak.getTestRunId() ==run.getId()) {
                                matchingLeak = leak; }
                        }
                        if (matchingLeak != null) {
                    %>
                    <img src='<%=h(contextPath)%>/TestResults/img/leak.png'><%}%>

                    </td>
                    <%}
                    }%>

                </tr>
                <%}%>
                </tbody>
            </table>
        </div>
    </div>
    <script type="text/javascript">
        $('#stats').text("<%=h(errorRuns)%> Errors | <%=h(warningRuns)%> Warnings | <%=h(goodRuns)%> Passes | <%=h(missingUsers.length)%> Missing");
    </script>
    <%}%>

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
                    window.location.href = "<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>&viewType=" + this.value;
                });
            </script>
            <!--Bar Graphs for average over past week-->
            <div class="centeredContent">
                <div id="duration" class="c3chart"></div>
                <div id="passes" class="c3chart"></div>
                <div id="memory" class="c3chart"></div>
                <div id="failGraph" class="c3chart"></div>
            </div>
        </div>
        <br />
        <br />

        <!--Basic test run information about duration, # of passses, # of failurs, and # of leaks-->
        <div class="centeredContent">
        <%if(topFailures.size() > 0){%>
            <!--Top Failures, occurences, and language pie chart table-->
            <table class="decoratedtable" style="float:left;">
                <tr>
                    <td><h4>Top Failures</h4></td>
                    <td><h4>Occurences</h4></td>
                    <td><h4>Language Summary</h4></td>
                </tr>
                <%

                for(String key: topFailures.keySet()) {
                %>
                <tr>
                    <td><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>failedTest=<%=h(key)%>&viewType=<%=viewType%>" target="_blank"><%=h(key)%></a></td>
                    <td><%=h(topFailures.get(key).size())%></td>
                    <td>
                        <div id="<%=key%>" class="c3chart" style="width:120px; height:120px;"></div>
                        <script>

                            var <%=key%> = c3.generate({
                                bindto: '#<%=key%>',
                                data: {
                                    columns: [
                        <%
                            Map<String, Double> lang =languageBreakdown.get(key);
                           for(String l: lang.keySet()) {
                            Double percent = lang.get(l) * 100;
                        %>

                                ['<%=h(l)%>', <%=h(percent.intValue())%>],
                        <%}%>  ],
                            type : 'pie',
                                onclick: function (d, i) { console.log("onclick", d, i); },
                                onmouseover: function (d, i) { console.log("onmouseover", d, i); },
                                onmouseout: function (d, i) { console.log("onmouseout", d, i); }
                                    ,colors: {
                                        unknown: '#A078A0',
                                        ja: '#FFB82E',
                                        ch: '#20B2AA',
                                        fr: '#F08080',
                                        en: '#FF8B2E'
                                    } },

                                pie: {

                                    label: {
                                        format: function (value, ratio, id) {
                                            return "";
                                        }
                                    }
                                }
                            });
                        </script>

                    </td>
                </tr>
                <%}%>
            </table>
            <%}%>

            <%if(topLeaks.size() > 0){%>
            <table class="decoratedtable" style="float:left;">
                <tr>
                    <td><h4>Top Leaks</h4></td>
                    <td><h4>Occurences</h4></td>
                    <td><h4>Mean Leak</h4></td>
                </tr>
                <%for(String key: topLeaks.keySet()) {%>
                <tr>
                    <td><%=h(key)%></td>
                    <td><%=h(topLeaks.get(key).size())%></td>
                    <% double[] leakbytes = new double[topLeaks.get(key).size()];
                        for(int i = 0; i < topLeaks.get(key).size(); i++) {
                            leakbytes[i] = topLeaks.get(key).get(i).getBytes();
                        }
                        MathStat l1 = service.getStats(leakbytes);%>
                    <td>
                        <%=h((int) (l1.getMean()/1000)+"kb")%> <!--converts bytes to kb-->
                    </td>
                </tr>
                <%}%>
            </table>
            <%}%>
        </div>
    </div>

<%if(todaysFailures.size() == 0 && todaysLeaks.size() == 0) {%>
    <script type="text/javascript">
        $('#today-fail-leak-table').hide();
    </script>
<%}%>
<%if(memoryChartData != null) {%>
    <script>
        var pointRatio = 30;
        var jsonObject = jQuery.parseJSON( <%=q(memoryChartData.toString())%>);
        var sortedRunDetails = {};
        for(var key in jsonObject["runs"]) {
            sortedRunDetails[key] = jsonObject["runs"][key];
        }
        // start c3 chart generation
        var memoryUsageChart = c3.generate({
            bindto: '#memoryGraph',
            size: {
                height:350,
                width: 1024
            },
            data: {
                json: sortedRunDetails
            },
            point: {
                show: false
            },
            axis : {
                x : {
                    tick: {
                        format: function (x) { return x*pointRatio; }
                    }
                },
                y : {
                    label: {
                        text: 'Memory (MB)',
                        position: 'outer-middle'
                    }
                }
            },

            regions:
                    function(d) {
                        var regions = [];
                        var last = 0;
                        for(key in jsonObject["passes"]) {
                            regions.push({axis: 'x', start: last/pointRatio, end: jsonObject["passes"][key]/pointRatio, class: 'pass' + key});
                            last = jsonObject["passes"][key] + 1;
                        }
                        regions.push({axis: 'x', start: last/pointRatio, class: 'pass' + regions.length});
                        return regions;
                    }
            ,
            legend: {
                item: {
                    onmouseout: function(d) {
                        $('.highlightrun').children('td:not(:first-child), th').css('background','#fff');
                        $('.highlightrun').css('background','#fff'); // for td (no children)

                        // line hover effects
                        d3.selectAll(".c3-line").style("opacity",1);
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
                        d3.selectAll(".c3-line").style("opacity",0.2);
                        var k = ".c3-line-"+ d.replace("(","-").replace(".","-").replace(")","-");
                        //make the clicked bar opacity 1
                        d3.selectAll(k).style("opacity",1)

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
                        }
                        if ($('.highlighttr-' + runId).length){
                            parent.scrollTop(0);
                            parent.scrollTop( childOffset.top - (parent.height()/2) );
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
                        window.open(
                                '<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId='+ id,
                                '_blank' // <- This is what makes it open in a new window.
                        );
                    }
                }
            },
            tooltip: {
                format: {
                    title: function (d) { return 'Test #: ' + d*pointRatio; },
                    value: function (value, ratio, id) {
                        return value + "MB";
                    }
                }
            }

        });
    </script>
<%}%>
</div>
</div>

<script>
$(function() {
    /* Initialize datepicker */
    $( "#datepicker" ).datepicker({
        onSelect: function(date) {
            window.location.href = "<%=h(new ActionURL(TestResultsController.BeginAction.class, c))%>end=" + date;
        }
    });
    $( "#anim" ).change(function() {
        $( "#datepicker" ).datepicker( "option", "showAnim", "fadeIn" );
    })
    $("#datepicker").datepicker( "setDate" , "<%=h(df.format(selectedDate))%>" ); // set to selected date

    /* Click event for training runs */
    $('.traindata').click(function() {
        var runId = this.getAttribute('runid');
        var train = this.getAttribute('train');
        $.getJSON('<%=h(new ActionURL(TestResultsController.TrainRunAction.class, c))%>runId='+runId+'&train='+train, function(data){
            if(data.Success) {
                location.reload();
            } else {
                alert("Failure removing run. Contact Yuval")
            }
        });
    })
});
</script>
<script type="text/javascript">
    document.getElementById("<%=h(viewType)%>").selected = "true";
</script>

<%if(trendsJson != null) {%>
    <script src="<%=h(contextPath)%>/TestResults/js/generateTrendCharts.js"></script>
    <script type="text/javascript">
        var trendsJson = jQuery.parseJSON( <%= q(trendsJson.toString()) %> );
        generateTrendCharts(trendsJson, false);
    </script>
<%}%>

<script type="text/javascript">
    /* Initialize sortable table */
    $(document).ready(function()
            {
                $("#generalstatstable").tablesorter({
                    widthFixed : true,
                    headers : {
                        0: { sorter: "text" },
                        1: { sorter: "digit" },
                        2: { sorter: "digit" },
                        3: { sorter: "digit" },
                        4: { sorter: "digit" },
                        5: { sorter: "digit" },
                        6: {sorter: false}
                    },
                    cssAsc        : "headerSortUp",
                    cssDesc       : "headerSortDown"
                });
                $("#generalstatstable").trigger("sorton",[[[1,1]]]);
            }
    );
</script>