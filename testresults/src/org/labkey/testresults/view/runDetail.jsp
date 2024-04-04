<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.TestFailDetail" %>
<%@ page import="org.labkey.testresults.model.TestLeakDetail" %>
<%@ page import="org.labkey.testresults.model.TestHandleLeakDetail" %>
<%@ page import="org.labkey.testresults.model.TestMemoryLeakDetail" %>
<%@ page import="org.labkey.testresults.model.TestPassDetail" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("TestResults/js/d3.min.js");
        dependencies.add("TestResults/js/c3.min.js");
        dependencies.add("TestResults/css/c3.min.css");

    }
%>

<%
    /*
      User: Yuval Boss, yuval(at)uw.edu
      Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    Container c = getContainer();
    final String contextPath = AppProps.getInstance().getContextPath();
    String runId = getViewContext().getRequest().getParameter("runId");
%>

<%@include file="menu.jsp" %>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.requiresCss("/TestResults/css/style.css");
    var csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};
    var popupData = function(data) {
        var win = window.open("", "Log file",
                "toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=yes,resizable=yes," +
                "width=800,height=600");
        win.document.write('<pre>' + data + '</pre>');
    };
    var showLog = function() {
        $.get('<%=h(new ActionURL(TestResultsController.ViewLogAction.class, c).addParameter("runid", runId))%>', csrf_header,
            function(data) { popupData(data.log); }, "json");
    };
    var showXml = function() {
        $.get('<%=h(new ActionURL(TestResultsController.ViewXmlAction.class, c).addParameter("runid", runId))%>', csrf_header,
            function(data) { popupData(data.xml); }, "json");
    };
</script>

<!--Content to display if data is not null-->
<% if (data != null) {
    RunDetail run = data.getRuns()[0];
    TestFailDetail[] failures = run.getFailures();
    Arrays.sort(failures); // sorts by timestamp
    TestLeakDetail[] leaks = run.getLeaks();
    TestPassDetail[] passes = run.getPasses();
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    DateFormat dfMDHM = new SimpleDateFormat("MM/dd HH:mm");
    String hang = null;
    if (run.getHang() != null)
        hang = run.getHang().getTestName();
%>

<div>
    <!--Header content, username run Id, and other information about the run-->
    <h2>
        <%=h(run.getUserName() + " : " + df.format(run.getPostTime()))%> &nbsp;
        <img id="flagged" src="<%=getWebappURL("/TestResults/img/" + (run.isFlagged() ? "flagon" : "flagoff") + ".png")%>"
            title="Click to <% if (run.isFlagged()) { %>unflag<% } else { %>flag<% } %> run"
            style="width: 30px; height: 30px; cursor: pointer;"> &nbsp;
        <button id="deleteRun">Delete Run</button>
        <br>
        <% if (hang != null) { %><span style="color: red;">(POSSIBLE HANG: <%=h(hang)%>)</span><% } %>
    </h2>
    <p>
        Run Id: <%=run.getId()%><br>
        User : <a href="<%=h(urlFor(TestResultsController.ShowUserAction.class).addParameter("user", run.getUserName()))%>"><%=h(run.getUserName())%></a><br>
        OS: <%=h(run.getOs())%><br>
        Revision: <%=h(run.getRevisionFull())%><br>
        Passed Tests : <%=run.getPasses().length%><br>
        Memory : <%=h(run.getAverageMemory())%><br>
        Failures : <%=failures.length%><br>
        Leaks : <%=leaks.length%><br>
        Timestamp:  <%=h((run.getTimestamp() == null) ? "N/A" : run.getTimestamp())%><br>
        <a id="trainset" style="cursor: pointer;"><%=h((run.isTrainRun()) ? "Remove from training set" : "Add to training set")%></a>
    </p>
    <%=button("View Log").onClick("showLog()")%>

    <!--Script to handle deleting of run-->
    <script type="text/javascript" nonce="<%=getScriptNonce()%>">
        <% if (run.isFlagged()) { %>
        var current = "<%=h(contextPath)%>/TestResults/img/flagon.png";
        var opposite = "<%=h(contextPath)%>/TestResults/img/flagoff.png";
        <% } else { %>
        var current = "<%=h(contextPath)%>/TestResults/img/flagoff.png";
        var opposite = "<%=h(contextPath)%>/TestResults/img/flagon.png";
        <% } %>
        $("#flagged").on({
            "mouseover": function() { this.src = opposite; },
            "mouseout": function() { this.src= current; },
            "click": function() {
                if (!confirm("<%= h(run.isFlagged()
                    ? "Press Ok to unflag this run so that it will be used in analyses across the module."
                    : "Press Ok to flag this run.  You will be able to unflag the run but while the run remains flagged it will not be used in analyses across the module.")%>"))
                    return;

                let postData = {
                    "X-LABKEY-CSRF": LABKEY.CSRF,
                    "runId": <%=h(run.getId())%>,
                    "flag": <%=h(!run.isFlagged())%>
                };
                $.post(<%=jsURL(new ActionURL(TestResultsController.FlagRunAction.class, c))%>, postData, function(data) {
                    if (data.error) {
                        alert("error: " + data.error);
                        return;
                    }
                    location.reload();
                }, "json");
            }
        });
        $('#deleteRun').click(function() {
            if (!confirm("Press 'Ok' to delete this run and all associated passes, leaks, failures, and hangs."))
                return;

            let postData = {
                "X-LABKEY-CSRF": LABKEY.CSRF,
                "runId": <%=h(run.getId())%>
            };
            $.post(<%=jsURL(new ActionURL(TestResultsController.DeleteRunAction.class, c))%>, postData, function(data) {
                if (data.error) {
                    alert("error: " + data.error);
                    return;
                }
                location.reload();
            }, "json");
        });
    </script>
</div>
<!--Graph of memory usage using TestDataBean.getMemoryJson(int runId)-->
<div id="memoryGraph"></div>
<!--Table containing all failed tests & testmemoryleaks-->
<% if (failures.length > 0) { %>
<table class="decoratedtable" style="float: left;">
    <tr>
        <td>Failed Tests</td>
        <td style="max-width: 500px;">Stack Trace</td>
    </tr>
    <% for (TestFailDetail f: failures) { %>
    <tr>
        <td><a href="<%=h(new ActionURL(TestResultsController.ShowFailures.class, c).addParameter("failedTest", f.getTestName()).addParameter("viewType", "wk"))%>"><%=h(f.getTestName())%></a> <br />
            Language: <%=h(f.getLanguage())%><br/>
            TimeStamp: <%=h((f.getTimestamp() == null) ? "N/A" : dfMDHM.format(f.getTimestamp()))%>
        </td>
        <td style="font-size: 10px; width: 500px; text-align: left;"><pre><%=h(f.getStacktrace())%></pre></td>
    </tr>
    <% } %>
</table>
<% }
if (leaks.length > 0) { %>
<table class="decoratedtable" style="float: left;">
    <tr><td>Leaks</td><td>Bytes</td><td>Handles</td></tr>
    <% for (TestLeakDetail l: leaks) { %>
    <tr>
        <td><%=h(l.getTestName())%></td>
        <td><% if (l instanceof TestMemoryLeakDetail) { %><%= h(((TestMemoryLeakDetail)l).getBytes()/1000) %> kb<% } %></td>
        <td><% if (l instanceof TestHandleLeakDetail) { %><%= h(((TestHandleLeakDetail)l).getHandles()) %><% } %></td>
    </tr>
    <% } %>
</table>
<% } %>

<table class="decoratedtable" style="float:left;">
    <tr>
        <td>Test | Sort by:
            <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c).addParameter("runId", runId))%>">None</a>,
            <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c).addParameter("runId", runId).addParameter("filter", "duration"))%>">Duration</a>,
            <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c).addParameter("runId", runId).addParameter("filter", "managed"))%>">Managed Memory</a>,
            <a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, c).addParameter("runId", runId).addParameter("filter", "total"))%>">Total Memory</a></td>
        <td>Pass</td>
        <td>Language</td>
        <td>Duration</td>
        <td>Managed Memory</td>
        <td>Total Memory</td>
        <td>Timestamp (HH/mm)</td>
    </tr>
    <% for (TestPassDetail detail: passes) { %>
       <tr>
           <td><%=h(detail.getTestName())%></td>
           <td><%=detail.getPass()%></td>
           <td><%=h(detail.getLanguage())%></td>
           <td><%=detail.getDuration()%></td>
           <td><%=data.round(detail.getManagedMemory(), 2)%></td>
           <td><%=data.round(detail.getTotalMemory(), 2)%></td>
           <td><%=h((detail.getTimestamp() == null) ? "N/A" : dfMDHM.format(detail.getTimestamp()))%></td>
       </tr>
    <% } %>
    <tr><td<% if (hang != null) { %> style="background: yellow;"<% } %>>Posted: <%=h(formatDateTime(run.getPostTime()))%></td></tr>
    </table>
<!--Handles add/remove from training data set-->
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    $('#trainset').click(function() {
        var csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};
        $(this).off().text("Please wait...");
        $.post(<%=q(new ActionURL(TestResultsController.TrainRunAction.class, c).addParameter("runId", run.getId()).addParameter("train", run.isTrainRun() ? "false" : "true"))%>, csrf_header, function(data){
            if (data.Success) {
                location.reload();
            } else {
                alert(data);
            }
        }, "json");
    });
</script>
<!--Script using c3 & d3js which generates memory graph-->
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var jsonObject = jQuery.parseJSON( <%=q(data.getMemoryJson(run.getId(), false).toString())%>);
    var passes = [];
    var pass = 0;
    if (jsonObject.json[0].testName.length == 0) {
        $('#memoryGraph').hide();
    }
    for (var i = 0; i < jsonObject.json[0].testName.length; i++) {
        if (jsonObject.json[0].testName[i].split(":")[1] != pass) {
            passes[pass] = i;
            pass = jsonObject.json[0].testName[i].split(":")[1];
        }
    }
    passes[pass] = jsonObject.json[0].testName.length-1;
    var chart5 = c3.generate({
        bindto: '#memoryGraph',
        size: { height:250, width: 700 },
        data: {
            json: {
                total:jsonObject.json[0].totalMemory,
                managed:jsonObject.json[0].managedMemory
            }
        },
        point: { show: false },
        tooltip: {
            format: {
                title: function (d) {
                    return (jsonObject.json[0].testName.length > d)
                        ? jsonObject.json[0].testName[d]
                        : "NaN";
                }
            }
        },
        regions: getPassBlocks() // so that each pass has a different background color (region)
    });

    // returns array of region objects [{start:x, end:x}, etc..]
    function getPassBlocks() {
        var result = [];
        for (var i = 0; i < passes.length; i++) {
            if (i == 0) {
                result.push({start:0, end:passes[0]});
            } else {
                result.push({start:passes[i-1], end:passes[i]});
            }
        }
        return result;
    }
</script>

<% } else { %>
    <!--Content to display if data is null-->
<form action="<%=h(urlFor(TestResultsController.ShowRunAction.class))%>">
    <br />
    Run Id:<br>
    <input type="text" name="runId">
    <input type="submit" value="Submit">
</form>
<% } %>
