<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestsDataBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.testresults.User" %>

<%@ page import="org.labkey.testresults.RunDetail" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.testresults.SendTestResultsEmail" %>
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
    List<User> noRunsForUser = new ArrayList<User>();
%>
<script type="text/javascript">
    LABKEY.requiresCss("/TestResults/css/style.css");
</script>
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
    <table>
        <tr>
            <td style="vertical-align: top; padding-right: 20px;">
                <div style="font-weight:600;" title="testresults-setEmailCron.view?action={status|start|stop}">Email cron active: <span id="emailstatus" style="color:#247BA0;"></span>
                    <input type='button' value='' id='email-cron-button' style="margin-left:10px;">
                    <div id="email-cron"></div>
                </div>
            </td>
            <td style="vertical-align: top; padding-right: 20px;">
                <form autocomplete="off">
                    <table style="margin-top: 0px;">
                        <tr>
                            <td style="text-align: left;">From:</td>
                            <td><input type="text" name="from" id="emailFrom" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.ADMIN_EMAIL)%>" autocomplete="off"></td>
                        </tr>
                        <tr>
                            <td style="text-align: left;">To:</td>
                            <td><input type="text" name="to" id="emailTo" value="<%=h(SendTestResultsEmail.DEFAULT_EMAIL.RECIPIENT)%>" autocomplete="off"></td>
                        </tr>
                    </table>

                    <input type='button' value='Send' id='send-button' style="margin-left:20px;">
                    <div id="send-email-msg"></div>
                </form>
            </td>
            <td style="vertical-align: top; padding-right: 20px;">
                <input type='button' value='Generate Email' id='html-button' style="margin-left:20px;">
            </td>
        </tr>
    </table>


    <div id="msg-container"></div>

    <table id="trainingdata">
        <tr style="border:none; font-weight:800;">
            <td style="border-left:1px solid #000; padding-left:5px;">Date</td>
            <td style="border-left:1px solid #000; padding-left:5px;">Duration</td>
            <td style="border-left:1px solid #000; padding-left:5px;">Tests Run</td>
            <td style="border-left:1px solid #000; padding-left:5px;">Failure Count</td>
            <td style="border-left:1px solid #000; padding-left:5px;">Mean Memory</td>
        </tr>
    <%for(User user : users) {
        if(user.getMeanmemory() == 0d && user.getMeantestsrun() == 0d){
            noRunsForUser.add(user);
            continue;
        }
        String color = "red";
        if(user.isActive())
            color = "green";
    %>
        <tr  style="border:none;"><td></td></tr>
        <tr style="border:none;">
            <th colspan="6"  style="float:left; padding-top:5px; font-size:14px; width:200px; color:#000; background-color:<%=h(color)%>;"><%=h(user.getUsername())%></th>
            <th>
                <%if(user.isActive()){%>
                    <input type='button' value='Deactivate user' class='deactivate-user' style="margin-left:5px;" userid="<%=user.getId()%>">
                <%} else {%>
                    <input type='button' value='Activate user' class='activate-user' style="margin-left:5px;" userid="<%=user.getId()%>">
                <%}%>
            </th>
        </tr>
        <%
            for(RunDetail run : runs) {
            if(run.getUserid() == user.getId()) {%>
                <tr>
                    <td style="border-left:1px solid #000; padding-left:5px;"><%=h(run.getPostTime())%></td>
                    <td style="border-left:1px solid #000; padding-left:5px;"><%=h(run.getDuration())%></td>
                    <td style="border-left:1px solid #000; padding-left:5px;"><%=h(run.getPassedtests())%></td>
                    <td style="border-left:1px solid #000; padding-left:5px;"><%=h(run.getFailedtests())%></td>
                    <td style="border-left:1px solid #000; padding-left:5px;"><%=h(run.getAverageMemory())%></td>
                    <td style="border-left:1px solid #000; padding-left:5px;"><a runid="<%=h(run.getId())%>" class="removedata">Remove</a></td>
                </tr>
            <%}
        }%>
        <tr style="font-weight:600; font-size:10px;">
            <td></td>
            <td></td>
            <td style="padding-left:0px;  color:#50514F;">
                MeanTotalMem:<%=h(data.round(user.getMeanmemory(),2))%> mb&nbsp;||&nbsp;
            </td>
            <td style="color:#50514F;">
                1StdDevMem:<%=h(data.round(user.getStddevmemory(),2))%> mb&nbsp;||&nbsp;
            </td>
            <td style="color:#50514F;">

            MeanTestsRun:<%=h(data.round(user.getMeantestsrun(),2))%>&nbsp;||&nbsp;
            </td>
            <td style="color:#50514F;">
            1StdDevTestsRun:<%=h(data.round(user.getStddevtestsrun(),2))%>
            </td>
        </tr>
        <%}%>
        <tr  style="border:none;">
            <th colspan="6" style="float:left; padding-top:5px; font-size:14px; width:200px;">No Training Data --</th>
        </tr>
        <%for(User user : noRunsForUser) {%>
        <tr  style="border:none;">
                <th colspan="6" style="float:left; padding-top:5px; font-size:11px; width:200px; color:#247BA0;">
                    <a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, c))%>user=<%=h(user.getUsername())%>">
                        <%=h(user.getUsername())%>
                    </a>
                </th>
        </tr>
        <%}%>
    </table>
</div>
<script type="text/javascript">
    $('.removedata').click(function() {
        var runId = this.getAttribute('runid');
        $.getJSON('<%=h(new ActionURL(TestResultsController.TrainRunAction.class, c))%>runId='+runId+'&train=false', function(data){
            if(data.Success) {
                location.reload();
            } else {
                alert("Failure removing run. Contact Yuval")
            }
        });
    })

    $.getJSON('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>', function(data){
            $('#emailstatus').text(data.Response);
        if(data.Response == "false") {
            $("#email-cron-button").attr('value', 'Start');
            $("#email-cron-button").click(function() {
                $.getJSON('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>'+'action=start', function(data){
                    console.log(data);
                    $('#cron-message').text(data.Message);
                    location.reload();
                })
            });
        } else {
            $("#email-cron-button").attr('value', 'Stop');
            $("#email-cron-button").click(function() {
                $.getJSON('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>'+'action=stop', function(data){
                    console.log(data);
                    $('#cron-message').text(data.Message);
                    location.reload();
                })
            });
        }
    });
    $("#html-button").click(function() {
        $.getJSON('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>'+'action=<%=h(SendTestResultsEmail.TEST_GET_HTML_EMAIL)%>', function(data){
            console.log(data);
            $('#msg-container').html(data.HTML);
        })
    });
    $("#send-button").click(function() {
        $.getJSON('<%=h(new ActionURL(TestResultsController.SetEmailCronAction.class, c))%>' + 'action=<%=h(SendTestResultsEmail.TEST_CUSTOM)%>&emailF='+$('#emailFrom').val()+'&emailT='+$('#emailTo').val(), function (data) {
            console.log(data);
            $('#send-email-msg').text(data.Message);
        });
    });

    $('.deactivate-user').click(function(obj) {
        var userId = this.getAttribute("userid");
        $.getJSON('<%=h(new ActionURL(TestResultsController.SetUserActive.class, c))%>'+'active=false&userId='+userId, function(data){
            console.log(data);
            location.reload();
        })
    });

    $('.activate-user').click(function(obj) {
        var userId = this.getAttribute("userid");
        $.getJSON('<%=h(new ActionURL(TestResultsController.SetUserActive.class, c))%>'+'active=true&userId='+userId, function(data){
            console.log(data);
            location.reload();
        })
    });
</script>