<%
/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.testresults.SendTestResultsEmail" %>
<%@ page import="org.labkey.testresults.model.BackgroundColor" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.model.User" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("TestResults/css/style.css");
    }
%>
<script src="https://code.jquery.com/ui/1.13.2/jquery-ui.min.js" nonce="<%=getScriptNonce()%>"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.13.2/themes/smoothness/jquery-ui.min.css">

<%
    /**
     * User: Yuval Boss, yuval(at)uw.edu
     * Date: 1/14/2015
     */
    JspView<?> me = HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
    User[] users = data.getUsers();
    RunDetail[] runs = data.getRuns();
    Container c = getViewContext().getContainer();
    List<User> noRunsForUser = new ArrayList<>();
%>

<div id="content">
    <% request.setAttribute(TestResultsController.TabNames.ACTIVE_TAB_ATTR, TestResultsController.TabNames.TRAINING_DATA); %>
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
        <option id="retrain" value="retrain" <%= h(value.equals("retrain") ? "selected='selected'" : "") %>>Retrain</option>
    </select>

    <table id="emailform" style="margin: 10px 0 10px 5px;">
        <tr>
            <td style="vertical-align: top; padding-right: 20px;">
                <div style="font-weight:600;" title="testresults-setEmailCron.view?action={status|start|stop}">Email cron active: <span id="emailstatus" style="color:#247BA0;"></span>
                    <input type="button" id="email-cron-button" style="margin-left:10px;">
                    <div id="email-cron"></div>
                </div>
            </td>
            <td style="vertical-align: top; padding-right: 12px;">
                <form autocomplete="off" style="margin: 0; padding: 0;">
                    <table style="border-spacing: 0; border-collapse: separate; margin: 0;">
                        <tr>
                            <td style="text-align: right; padding: 0 12px 4px 0;">From:</td>
                            <td style="padding: 0 0 4px 0;"><input type="text" name="from" id="emailFrom" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL)%>" autocomplete="off"></td>
                        </tr>
                        <tr>
                            <td style="text-align: right; padding: 4px 12px 4px 0;">To:</td>
                            <td style="padding: 4px 0;"><input type="text" name="to" id="emailTo" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.RECIPIENT)%>" autocomplete="off"></td>
                        </tr>
                    </table>
                    <div id="send-email-msg"></div>
                </form>
            </td>
            <td style="vertical-align: top;">
                <table style="border-spacing: 0; border-collapse: separate; margin: 0;">
                    <tr>
                        <td style="padding: 0 8px 4px 0;"><input type="button" value="Generate Email" id="html-button"></td>
                        <td style="padding: 0 0 4px 0;"><input style="width: 100px;" type="text" id="generate-email-datepicker"></td>
                    </tr>
                    <tr>
                        <td style="padding: 4px 0;"><input type="button" value="Send Now" id="send-button"></td>
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>

    <table id="errorform" style="margin: 10px 0 10px 5px;">
        <tr>
            <td style="vertical-align: top; padding-right: 12px;">
                <form autocomplete="off" style="margin: 0; padding: 0;">
                    <table style="border-spacing: 0; border-collapse: separate; margin: 0;">
                        <tr>
                            <td style="text-align: right; padding: 0 12px 4px 0;">Warning boundary:</td>
                            <td style="padding: 0 0 4px 0;"><input type="text" name="warningb" id="warningb" autocomplete="off" value="<%= data.getWarningBoundary() %>" style="width: 50px;"></td>
                        </tr>
                        <tr>
                            <td style="text-align: right; padding: 0 12px 0 0;">Error boundary:</td>
                            <td><input type="text" name="errorb" id="errorb" autocomplete="off" value="<%= data.getErrorBoundary() %>" style="width: 50px;"></td>
                        </tr>
                    </table>
                    <div id="send-boundaries-msg"></div>
                </form>
            </td>
            <td style="vertical-align: top;">
                <input type="button" value="Submit" id="submit-button">
            </td>
        </tr>
    </table>

    <table id="retrainform" style="margin: 10px 0 10px 5px;">
        <tr>
            <td style="vertical-align: top; padding-right: 12px;">
                <form autocomplete="off" style="margin: 0; padding: 0;">
                    <table style="border-spacing: 0; border-collapse: separate; margin: 0;">
                        <tr>
                            <td style="text-align: right; vertical-align: top; padding: 0 12px 4px 0;">Mode:</td>
                            <td style="padding: 0 0 4px 0;">
                                <label style="display: block; margin-bottom: 4px;"><input type="radio" name="retrainMode" value="reset" checked> Reset (delete all and rebuild)</label>
                                <label style="display: block;"><input type="radio" name="retrainMode" value="incremental"> Incremental (add to existing)</label>
                            </td>
                        </tr>
                        <tr>
                            <td style="text-align: right; padding: 4px 12px 4px 0;">Max runs:</td>
                            <td style="padding: 4px 0;"><input type="text" name="maxRuns" id="maxRuns" autocomplete="off" value="20" style="width: 50px;"></td>
                        </tr>
                        <tr>
                            <td style="text-align: right; padding: 0 12px 0 0;">Min runs:</td>
                            <td><input type="text" name="minRuns" id="minRuns" autocomplete="off" value="5" style="width: 50px;"></td>
                        </tr>
                    </table>
                </form>
            </td>
            <td style="vertical-align: top;">
                <input type="button" value="Retrain All" id="retrain-all-button">
            </td>
            <td style="vertical-align: middle; padding-left: 10px;">
                <span id="retrain-all-status"></span>
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
                    <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c).addParameter("username", user.getUsername()).addParameter("datainclude", "train"))%>"><%=h(user.getUsername())%></a>
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
                int runCount = 0;
                for (RunDetail run : runs) {
                    if (run.getUserid() == user.getId()) {
                        if (firstRunId == -1) { firstRunId = run.getId(); }
                        runCount++;
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
                <td style="padding-left: 5px; color:#50514F;">RunCount:<%=runCount%>&nbsp;||&nbsp;</td>
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
                <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c).addParameter("username", user.getUsername()))%>">
                    <%=h(user.getUsername())%>
                </a>
            </th>
        </tr>
        <% } %>
    </table>
</div>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

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
            alert("Failed to update training set." + (data.error ? " " + data.error : ""));
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
            // data.HTML is a rendered email containing stored run/user/test names. Render it in a sandboxed iframe
            // (no allow-scripts, opaque origin) so any markup it contains is displayed but cannot execute as script.
            var iframe = win.document.createElement('iframe');
            iframe.setAttribute('sandbox', '');
            // Use viewport height: a percentage height would resolve against the popup body
            // (which has no defined height) and collapse the iframe to zero, showing nothing.
            iframe.style.cssText = 'border:0;width:100%;height:100vh;display:block;';
            iframe.srcdoc = data.HTML;
            win.document.body.style.margin = '0';
            win.document.body.appendChild(iframe);
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

    $("#retrain-all-button").click(function() {
        var mode = $('input[name="retrainMode"]:checked').val();
        var maxRuns = parseInt($('#maxRuns').val()) || 20;
        var minRuns = parseInt($('#minRuns').val()) || 5;
        var confirmMsg = mode === 'reset'
            ? "This will DELETE all existing training data and rebuild with " + minRuns + "-" + maxRuns + " clean runs per computer. Continue?"
            : "This will ADD clean runs to computers with fewer than " + maxRuns + " training runs. Continue?";
        if (!confirm(confirmMsg))
            return;
        var btn = $(this);
        btn.prop('disabled', true);
        $('#retrain-all-status').text('Retraining...');
        let url = <%=jsURL(new ActionURL(TestResultsController.RetrainAllAction.class, c))%>;
        url.searchParams.set('mode', mode);
        url.searchParams.set('maxRuns', maxRuns);
        url.searchParams.set('minRuns', minRuns);
        $.post(url.toString(), csrf_header, function(data) {
            if (data.Success) {
                $('#retrain-all-status').text('Retrained ' + data.usersRetrained + ' computers with ' + data.totalTrainRuns + ' runs. Reloading...');
                location.reload();
            } else {
                $('#retrain-all-status').text('Error: ' + (data.error || 'Unknown error'));
                btn.prop('disabled', false);
            }
        }, "json").fail(function() {
            $('#retrain-all-status').text('Request failed');
            btn.prop('disabled', false);
        });
    });

    $("#actionform").change(function() {
        $("#errorform").hide();
        $("#emailform").hide();
        $("#retrainform").hide();
        if ($(this).val() == "email") {
            $("#emailform").show();
        } else if ($(this).val() == "error") {
            $("#errorform").show();
        } else if ($(this).val() == "retrain") {
            $("#retrainform").show();
        }
    }).trigger("change");

</script>
