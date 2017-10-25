<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.testresults.RunDetail" %>
<%@ page import="org.labkey.testresults.TestFailDetail" %>
<%@ page import="org.labkey.testresults.TestLeakDetail" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.testresults.TestPassDetail" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    Container c = getContainer();
    final String contextPath = AppProps.getInstance().getContextPath();
    String runId = getViewContext().getRequest().getParameter("runId");
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
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<script src="https://code.jquery.com/jquery-2.1.3.min.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>

<!--Content to display if data is not null-->
<%if(data != null) {
    RunDetail run = data.getRuns()[0];
    TestFailDetail[] failures = run.getFailures();
    Arrays.sort(failures); // sorts by timestamp
    TestLeakDetail[] leaks = run.getLeaks();
    TestPassDetail[] passes = run.getPasses();
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    DateFormat dfMDHM = new SimpleDateFormat("MM/dd HH:mm");
    String log = run.getDecodedLog();
    boolean hasHang = run.hasHang();
%>

<div>
    <!--Header content, username run Id, and other information about the run-->
    <h2><%=h(run.getUserName() + " : " + df.format(run.getPostTime()))%> &nbsp;&nbsp;<img id="flagged" flagged="false" src="
    <%if(run.isFlagged()){%>
        <%=h(contextPath)%>/TestResults/img/flagon.png
    <%} else { %>
        <%=h(contextPath)%>/TestResults/img/flagoff.png
    <%}%>
    " title="
     <%if(run.isFlagged()){%>
        Click to unflag run
    <%} else { %>
        Click to flag run
    <%}%>
    " style="width:30px; height:30px;">
        &nbsp;&nbsp;<button id="deleteRun">Delete Run</button>
    <br/><%if(hasHang){%> <span style="color:red;">(POSSIBLE HANG)</span> <%}%></h2>
    <p>Run Id: <%=h(run.getId())%> <br />
    User : <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=<%=h(run.getUsername())%>"><%=h(run.getUserName())%></a> <br />
    OS: <%=h(run.getOs())%>   <br />
    Revision: <%=h(run.getRevision())%>  <br />
    Passed Tests : <%=h(run.getPasses().length)%> <br />
    Failures : <%=h(failures.length)%> <br />
    Leaks : <%=h(leaks.length)%> <br />
        TimeStamp:  <%=h((run.getTimestamp() == null) ? "N/A" : run.getTimestamp())%><br >
        <a id="trainset">
            <%=h((run.isTrainRun()) ? "Remove from training set" : "Add to training set")%>
        </a>
    </p>
    <%if(log.length() > 0){%>
        <button onclick="showLog()">View Log</button>
        <pre id="log" style="font-family: monospace; display:none;"><%=h(log)%></pre>
    <%}%>

    <!--Script to handle deleting of run-->
    <script>
        var current = "";
        var opposite = "";
        <%if(run.isFlagged()){%>
        current = "<%=h(contextPath)%>/TestResults/img/flagon.png"
        opposite = "<%=h(contextPath)%>/TestResults/img/flagoff.png"
        <%} else { %>
        current = "<%=h(contextPath)%>/TestResults/img/flagoff.png"
        opposite = "<%=h(contextPath)%>/TestResults/img/flagon.png"
        <%}%>
        $("#flagged").on({
            "mouseover" : function() {
                this.src = opposite;
            },
            "mouseout" : function() {
                this.src= current;
            }
        });
        $("#flagged").click(function(){
            var c = confirm(""
            <%if(run.isFlagged()){%>
            +"Press 'Ok' to unflag this run so that it will be used in analyses across the module."
            <%} else { %>
            +"Press 'Ok' to flag this run.  You will be able to unflag the run but while the run " +
                    "remains flagged it will not be used in analyses across the module."
            <%}%>);
            if (c == true) {
                window.location.href = "<%=h(new ActionURL(TestResultsController.FlagRunAction.class, c))%>" + "runId=<%=h(run.getId())%>" + "&flag=" + <%=h(!run.isFlagged())%>;
            }
        });
        $('#deleteRun').click(function(){
            var c = confirm("Press 'Ok' to delete this run and all associated passes, leaks, and test failures.");
            if (c == true) {
                window.location.href = "<%=h(new ActionURL(TestResultsController.DeleteRunAction.class, c))%>" + "runId=<%=h(run.getId())%>";
            }
        });
    </script>
</div>
<!--Graph of memory usage using TestDataBean.getMemoryJson(int runId)-->
<div id="memoryGraph"></div>
<!--Table containing all failed tests & leaks-->
<table class="decoratedtable" style="float:left;">
    <tr>
        <td>Failed Tests</td>
        <td style="max-width:500px;">Stack Trace</td>
    </tr>
    <%for(TestFailDetail f: failures) {%>
    <tr>
        <td><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c))%>failedTest=<%=h(f.getTestName())%>&viewType=wk"><%=h(f.getTestName())%></a> <br />
            Language: <%=h(f.getLanguage())%><br/>
            TimeStamp: <%=h((f.getTimestamp() == null) ? "N/A" : dfMDHM.format(f.getTimestamp()))%>
        </td>
        <td style="font-size: 10px; width:500px; text-align: left;"><pre><%=h(f.getStacktrace())%></pre></td>
    </tr>
    <%}%>
</table>
<table class="decoratedtable" style="float:left;">
    <tr><td>Leaks</td><td>Bytes</td></tr>
    <%for(TestLeakDetail l: leaks) {%>
    <tr>
        <td><%=h(l.getTestName())%></td>
        <td><%=h(l.getBytes()/1000 + "kb")%></td>
    </tr>
    <%}%>
</table>

<table class="decoratedtable" style="float:left;">
          <tr>
              <td>Test | Sort by:
                  <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(runId)%>">None</a>,
                  <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(runId)%>&filter=duration">Duration</a>,
                  <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(runId)%>&filter=managed">Managed Memory</a>,
                  <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>runId=<%=h(runId)%>&filter=total">Total Memory</a></td>
              <td>Pass</td>
              <td>Language</td>
              <td>Duration</td>
              <td>Managed Memory</td>
              <td>Total Memory</td>
              <td>Timestamp (HH/mm)</td>
          </tr>
    <%for(TestPassDetail detail: passes) {%>
       <tr>
           <td><%=h(detail.getTestName())%></td>
           <td><%=h(detail.getPass())%></td>
           <td><%=h(detail.getLanguage())%></td>
           <td><%=h(detail.getDuration())%></td>
           <td><%=h(data.round(detail.getManagedMemory(), 2))%></td>
           <td><%=h(data.round(detail.getTotalMemory(),2))%></td>
           <td><%=h((detail.getTimestamp() == null) ? "N/A" : dfMDHM.format(detail.getTimestamp()))%></td>
       </tr>
    <%}%>
    <tr><td <%if(hasHang) {%>style="background:yellow;"<%}%>>Posted: <%=h(run.getPostTime())%></td></tr>
    </table>
<!--Handels add/remove from training  data set-->
<script>
    $('#trainset').click(function() {
        $.getJSON('<%=h(new ActionURL(TestResultsController.TrainRunAction.class, c))%>runId=<%=h(run.getId())%>&train=<%=h((run.isTrainRun()) ? "false" : "true")%>', function(data){
            if(data.Success) {
                <%run.setTrainRun(!run.isTrainRun());%>
                location.reload();
            } else {
                alert(data);
            }
        });
    })

</script>
<!--Script using c3 & d3js which generates memory graph-->
<script>
    var jsonObject = jQuery.parseJSON( <%=q(data.getMemoryJson(run.getId(), false).toString())%>);
    var passes = [];
    var pass = 0;
    if(jsonObject.json[0].testName.length == 0) {
        $('#memoryGraph').hide();
    }
    for(var i = 0; i < jsonObject.json[0].testName.length; i++) {
        if(jsonObject.json[0].testName[i].split(":")[1] != pass) {
            passes[pass] = i;
            pass = jsonObject.json[0].testName[i].split(":")[1];
        }
    }
    passes[pass] = jsonObject.json[0].testName.length-1;
    var chart5 = c3.generate({
        bindto: '#memoryGraph',
        size: {
            height:250,
            width: 700
        },
        data: {
            json: {
                total:jsonObject.json[0].totalMemory,
                managed:jsonObject.json[0].managedMemory
            }
        },
        point: {
            show: false
        },
        tooltip: {
            format: {
                title: function (d) {
                    if(jsonObject.json[0].testName.length > d)
                        return jsonObject.json[0].testName[d];
                    else
                        return "NaN";
                }
            }
        },
        regions:  getPassBlocks() // so that each pass has a different background color (region)
    });

    // returns array of region objects [{start:x, end:x}, etc..]
    function getPassBlocks() {
        var result = [];
        for (var i = 0; i < passes.length; i++) {
            if (i == 0) {
                result.push({start:0, end:passes[0]});
            } else {
                result.push({start:passes[i-1], end:passes[i]})
            }
        }
        return result;
    }
    function showLog() {
        var wnd = window.open("about:blank", "", "_blank");
        wnd.document.write("<pre>" + $("#log").text() + "</pre>");
    }

</script>

<%} else {%>
    <!--Content to display if data is null-->
<form action="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c))%>">
    <br />
    Run Id:<br>
    <input type="text" name="runId">
    <input type="submit" value="Submit">
</form>
<%}%>
