<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.testresults.model.User" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.testresults.SendTestResultsEmail" %>
<%@ page import="org.labkey.testresults.model.BackgroundColor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    final String contextPath = AppProps.getInstance().getContextPath();
    User[] users = data.getUsers();
    RunDetail[] runs = data.getRuns();
    Container c = getViewContext().getContainer();
    List<User> noRunsForUser = new ArrayList<>();
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<script src="//code.jquery.com/jquery-1.10.2.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<div id="content">
    <%@include file="menu.jsp" %>
    <%
        String value = (request.getParameter("action"));
        if (value == null) {
            value = "firsttime";
        }
    %>
    Actions:
    <select id="actionform" name="action" >
        <option disabled value="firsttime" <%= h(value.equals("firsttime") ? "selected='selected'" : "") %>> -- select an option -- </option>
        <option id="email" value="email"  <%= h(value.equals("email") ? "selected='selected'" : "") %>>Email Form</option>
        <option id="error" value="error" <%= h(value.equals("error") ? "selected='selected'" : "") %>>Error/Warning edits</option>
    </select>

    <table id="emailform">
        <tr>
            <td style="vertical-align: top; padding-right: 20px;">
                <div style="font-weight:600;" title="testresults-setEmailCron.view?action={status|start|stop}">Email cron active: <span id="emailstatus" style="color:#247BA0;"></span>
                    <input type="button" id="email-cron-button" style="margin-left:10px;">
                    <div id="email-cron"></div>
                </div>
            </td>
            <td style="vertical-align: top; padding-right: 20px;">
                <form autocomplete="off">
                    <table style="margin-top: 0;">
                        <tr>
                            <td style="text-align: left;">From:</td>
                            <td><input type="text" name="from" id="emailFrom" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL)%>" autocomplete="off"></td>
                        </tr>
                        <tr>
                            <td style="text-align: left;">To:</td>
                            <td><input type="text" name="to" id="emailTo" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.RECIPIENT)%>" autocomplete="off"></td>
                        </tr>
                    </table>
                    <div id="send-email-msg"></div>
                </form>
            </td>
            <td style="vertical-align: top; padding-right: 20px;">
                <div style="display: flex; flex-direction: column;">
                    <input type="button" value="Generate Email" id="html-button" style="margin-left: 20px;">
                    <input type="button" value="Send" id="send-button" style="margin-left: 20px;">
                </div>
            </td>
            <td style="vertical-align: top; padding-right: 20px;">
                <div style="display: flex; flex-direction: column;">
                    <input style="width: 100px;" type="text" id="generate-email-datepicker">
                </div>
            </td>
        </tr>
    </table>

    <table id="errorform">
        <tr>
            <td style="vertical-align: top; padding-right: 20px;">
                <form autocomplete="off">
                    <table style="margin-top: 0;">
                        <tr>
                            <td style="text-align: left;">Warning Boundary:</td>
                            <td><input type="text" name="warningb" id="warningb" autocomplete="off" value="<%= data.getWarningBoundary() %>"></td>
                        </tr>
                        <tr>
                            <td style="text-align: left;">Error Boundary:</td>
                            <td><input type="text" name="errorb" id="errorb" autocomplete="off" value="<%= data.getErrorBoundary() %>"></td>
                        </tr>
                    </table>
                    <div id="send-boundaries-msg"></div>
                </form>
            </td>
            <td style="vertical-align: top; padding-right: 20px">
                <div style="display: flex; flex-direction: column">
                    <input type="button" value="submit" id="submit-button" style="margin-left: 20px;">
                </div>
            </td>
        </tr>
    </table>


    <div id="msg-container"></div>
    <table id="trainingdata">
        <tr style="border: none; font-weight: 800;">
            <td style="border-left: 1px solid #000; padding-left: 5px;" id="header-cell-date">Date</td>
            <td style="border-left: 1px solid #000; padding-left: 5px;">Duration</td>
            <td style="border-left: 1px solid #000; padding-left: 5px;" id="header-cell-tests">Tests Run</td>
            <td style="border-left: 1px solid #000; padding-left: 5px;">Failure Count</td>
            <td style="border-left: 1px solid #000; padding-left: 5px;" id="header-cell-mem">Mean Memory</td>
        </tr>
        <% for (User user : users) { %>
            <%
                if (user.getMeanmemory() == 0d && user.getMeantestsrun() == 0d) {
                    noRunsForUser.add(user);
                    continue;
                }
                String color = BackgroundColor.unknown.toString();
                if (user.isActive())
                    color = BackgroundColor.pass.toString();
            %>

            <tr id="user-anchor-<%= h(user.getUsername()) %>" style="border:none;"><td></td></tr>
            <tr style="border: none;">
                <th colspan="6"  style="float: left; padding-top: 5px; font-size: 14px; width: 200px; color: #000; background: <%=h(color)%>;">
                    <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c).addParameter("user", user.getUsername()).addParameter("datainclude", "train"))%>"><%=h(user.getUsername())%></a>
                </th>
                <th>
                    <% if (user.isActive()) { %>
                    <input type='button' value='Deactivate user' class='deactivate-user' style="margin-left: 5px;" data-userid="<%=user.getId()%>">
                    <% } else { %>
                    <input type='button' value='Activate user' class='activate-user' style="margin-left: 5px;" data-userid="<%=user.getId()%>">
                    <% } %>
                </th>
            </tr>
            <%
                int firstRunId = -1;
                for (RunDetail run : runs) {
                    if (run.getUserid() == user.getId()) {
                        if (firstRunId == -1) { firstRunId = run.getId(); }
            %>
            <tr>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><%=h(formatDateTime(run.getPostTime()))%></td>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><%=run.getDuration()%></td>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><%=run.getPassedtests()%></td>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><%=run.getFailedtests()%></td>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><%=h(run.getAverageMemory())%></td>
                <td style="border-left: 1px solid #000; padding-left: 5px;"><a style="cursor: pointer;" runid="<%=run.getId()%>" class="removedata">Remove</a></td>
            </tr>
            <% }
            } %>
            <tr class="stats-row" style="font-weight: 600; font-size: 10px;" data-runid="<%=h(firstRunId != -1 ? String.valueOf(firstRunId) : "")%>">
                <td style="padding-left: 5px;"></td>
                <td></td>
                <td class="stats-row-mem-mean" style="padding-left: 0; color:#50514F;">
                    MeanTotalMem:<%=h(data.round(user.getMeanmemory(), 2))%> mb&nbsp;||&nbsp;
                </td>
                <td class="stats-row-mem-stddev" style="color: #50514F;">
                    1StdDevMem:<%=h(data.round(user.getStddevmemory(), 2))%> mb&nbsp;||&nbsp;
                </td>
                <td class="stats-row-run-mean" style="color: #50514F;">
                    MeanTestsRun:<%=h(data.round(user.getMeantestsrun(), 2))%>&nbsp;||&nbsp;
                </td>
                <td class="stats-row-run-stddev" style="color: #50514F;">
                    1StdDevTestsRun:<%=h(data.round(user.getStddevtestsrun(), 2))%>
                </td>
            </tr>
            <% } %>
            <tr style="border: none;">
                <th colspan="6" style="float: left; padding-top: 5px; font-size: 14px; width: 200px;">No Training Data --</th>
            </tr>
        </table>
    <table>
        <% for (User user : noRunsForUser) { %>
        <tr style="border: none;">
            <th colspan="6" style="float: left; padding-top: 5px; font-size: 11px; width: 200px; color: #247BA0;">
                <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c).addParameter("user", user.getUsername()))%>">
                    <%=h(user.getUsername())%>
                </a>
            </th>
        </tr>
        <% } %>
    </table>
</div>
<script type="text/javascript">

    var csrf_header = {"X-LABKEY-CSRF": LABKEY.CSRF};

    $('.removedata').click(function() {
        var link = $(this);
        if (link.text() === 'Working...')
            return;
        var runId = this.getAttribute('runid');
        var row = link.closest("tr");
        var undo = link.text() === 'Undo';
        link.text("Working...");
        let url = <%=jsURL(new ActionURL(TestResultsController.TrainRunAction.class, c))%>;
        url.searchParams.set('runId', runId);
        url.searchParams.set('train', (!undo ? 'false' : 'true'));
        $.post(url.toString(), csrf_header, function(data) {
            if (data.Success) {
                if (!undo) {
                    row.children('td').css('background', '#aaa');
                    link.text('Undo');
                } else {
                    row.children('td').css('background', '');
                    link.text('Remove');
                }
                return;
            }
            alert("Failure removing run. Contact Yuval");
        }, "json");
    });

    $.post('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>', csrf_header, function(data) {
        $('#emailstatus').text(data.Response);
        if (data.Response == "false") {
            $("#email-cron-button").attr('value', 'Start');
            $("#email-cron-button").click(function() {
                $.post('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c).addParameter("action", "start"))%>', csrf_header, function(data){
                    $('#cron-message').text(data.Message);
                    location.reload();
                }, "json")
            });
        } else {
            $("#email-cron-button").attr('value', 'Stop');
            $("#email-cron-button").click(function() {
                $.post('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c).addParameter("action", "stop"))%>', csrf_header, function(data){
                    $('#cron-message').text(data.Message);
                    location.reload();
                }, "json")
            });
        }
    }, "json");

    $("#generate-email-datepicker").datepicker();
    $("#generate-email-datepicker").datepicker("setDate", new Date());
    $("#html-button").click(function() {
        let url = <%=jsURL(new ActionURL(TestResultsController.SetEmailCronAction.class, c).addParameter("action", SendTestResultsEmail.TEST_GET_HTML_EMAIL))%>;
        url.searchParams.set("generatedate", $("#generate-email-datepicker").val());
        $.post(url.toString(), csrf_header, function(data) {
            var win = window.open("", data.subject,
                "toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=yes,resizable=yes," +
                "width=800,height=600");
            win.document.write(data.HTML);
        }, "json")
    });

    $("#send-button").click(function() {
        let url = <%=jsURL(new ActionURL(TestResultsController.SetEmailCronAction.class, c).addParameter("action", SendTestResultsEmail.TEST_CUSTOM))%>;
        url.searchParams.set('emailF', $('#emailFrom').val());
        url.searchParams.set('emailT', $('#emailTo').val());
        url.searchParams.set('generatedate', $('#generate-email-datepicker').val());
        $.post(url.toString(), csrf_header, function (data) {
            $('#send-email-msg').text(data.Message);
        }, "json");
    });

    $("#submit-button").click(function () {
        //post to the backend
        let url = <%=jsURL(new ActionURL(TestResultsController.ChangeBoundaries.class, c))%>;
        url.searchParams.set('warningb', $('#warningb').val());
        url.searchParams.set('errorb', $('#errorb').val());
        $.post(url.toString(), csrf_header, function (data) {
            $('#send-boundaries-msg').text(data.Message);
        }, "json");
    });

    $('.deactivate-user').click(function(obj) {
        let url = <%=jsURL(new ActionURL(TestResultsController.SetUserActive.class, c).addParameter("active", false))%>;
        url.searchParams.set('userId', this.getAttribute("data-userid"));
        $.post(url.toString(), csrf_header, function(data) {
            location.reload();
        }, "json")
    });

    $('.activate-user').click(function(obj) {
        let url = <%=jsURL(new ActionURL(TestResultsController.SetUserActive.class, c).addParameter("active", true))%>;
        url.searchParams.set('userId', this.getAttribute("data-userid"));
        $.post(url.toString(), csrf_header, function(data) {
            location.reload();
        }, "json")
    });

    $("#actionform").change(function() {
        $("#errorform").hide();
        $("#emailform").hide();
        if ($(this).val() == "email") {
            $("#emailform").show();
        } else if ($(this).val() == "error") {
            $("#errorform").show();
        }
    }).trigger("change");

    function forceTrain(el) {
        var cell = el.closest("td");
        var runId = el.closest(".stats-row").data("runid");
        el.remove();
        cell.text("working...");
        let url = <%=jsURL(new ActionURL(TestResultsController.TrainRunAction.class, c).addParameter("train", "force"))%>;
        url.searchParams.set('runId', runId);
        $.post(url.toString(), csrf_header, function(data) {
            cell.text(data.Success ? "done" : "error");
        });
    }

    function integrity() {
        var parseStat = function(el) {
            var text = $(el).text();
            var colon = text.indexOf(":");
            if (colon >= 0)
                text = text.substring(colon + 1);
            var num = text.match(/\d+(\.\d+)/)[0];
            return num ? parseFloat(num) : null;
        };

        var idxDate = null;
        var idxTests = null;
        var idxMem = null;
        $("#trainingdata tr:first").children("td").each(function(index, element) {
            if (element.id === "header-cell-date") {
                idxDate = index;
            } else if (element.id === "header-cell-tests") {
                idxTests = index;
            } else if (element.id === "header-cell-mem") {
                idxMem = index;
            }
        });
        if (idxDate == null || idxTests == null || idxMem == null)
            return;
        var dataRuns = [];
        var dataMem = [];
        var userErrors = 0;
        var totalErrors = 0;
        $("#trainingdata tr:not(:first-child)").each(function(index, element) {
            element = $(element);
            var cells = element.children("td");
            if (!cells || !cells.length || !/[0-9]{4}-[0-9]{2}-[0-9]{2}/.test(cells.eq(idxDate).text())) {
                if (element.hasClass("stats-row") && dataRuns.length) {
                    var calcRunMean = 0;
                    var calcMemMean = 0;
                    for (var i = 0; i < dataRuns.length; i++) {
                        calcRunMean += dataRuns[i];
                        calcMemMean += dataMem[i];
                    }
                    calcRunMean /= dataRuns.length;
                    calcMemMean /= dataMem.length;
                    var calcRunStdDev = 0;
                    var calcMemStdDev = 0;
                    for (i = 0; i < dataRuns.length; i++) {
                        calcRunStdDev += Math.pow(calcRunMean - dataRuns[i], 2);
                        calcMemStdDev += Math.pow(calcMemMean - dataMem[i], 2);
                    }
                    calcRunStdDev = Math.sqrt(calcRunStdDev / dataRuns.length);
                    calcMemStdDev = Math.sqrt(calcMemStdDev / dataMem.length);

                    var elRunMean = element.find(".stats-row-run-mean");
                    var elRunStdDev = element.find(".stats-row-run-stddev");
                    var elMemMean = element.find(".stats-row-mem-mean");
                    var elMemStdDev = element.find(".stats-row-mem-stddev");
                    var runMean = parseStat(elRunMean);
                    var runStdDev = parseStat(elRunStdDev);
                    var memMean = parseStat(elMemMean);
                    var memStdDev = parseStat(elMemStdDev);

                    var errorCount = 0;
                    var errorColor = "#c00";
                    if (Math.abs(runMean - calcRunMean) >= 1) {
                        errorCount++;
                        elRunMean.css("color", errorColor)
                            .attr("title", "Expected: " + +calcRunMean.toFixed(2)).tooltip();
                    }
                    if (Math.abs(runStdDev - calcRunStdDev) >= 1) {
                        errorCount++;
                        elRunStdDev.css("color", errorColor)
                            .attr("title", "Expected: " + +calcRunStdDev.toFixed(2)).tooltip();
                    }
                    if (Math.abs(memMean - calcMemMean) >= 1) {
                        errorCount++;
                        elMemMean.css("color", errorColor)
                            .attr("title", "Expected: " + +calcMemMean.toFixed(2)).tooltip();
                    }
                    if (Math.abs(memStdDev - calcMemStdDev) >= 1) {
                        errorCount++;
                        elMemStdDev.css("color", errorColor)
                            .attr("title", "Expected: " + +calcMemStdDev.toFixed(2)).tooltip();
                    }
                    if (errorCount > 0) {
                        userErrors++;
                        totalErrors += errorCount;
                        element.children("td:first").html(
                            '<a onclick="forceTrain($(this));" style="cursor: pointer;">' +
                            '<img style="width: 16px; height: 16px;" src="<%=h(contextPath)%>/TestResults/img/reload.png">' +
                            '</a>');
                    }
                }
                dataRuns = [];
                dataMem = [];
                return;
            }
            dataRuns.push(parseFloat(cells.eq(idxTests).text()));
            dataMem.push(parseFloat(cells.eq(idxMem).text()));
        });
        console.log('Found ' + totalErrors + ' integrity issues across ' + userErrors + ' machines');
    }
</script>
