<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    final String menuContextPath = AppProps.getInstance().getContextPath();
    Container menuContainer = getViewContext().getContainer();
%>

<div id="menu">
    <ul>
        <li><a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, menuContainer))%>" style="color:#fff;">-Overview</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, menuContainer))%>" style="color:#fff;">-User</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, menuContainer))%>" style="color:#fff;">-Run</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.LongTermAction.class, menuContainer))%>" style="color:#fff;">-Long Term</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowFlaggedAction.class, menuContainer))%>" style="color:#fff;">-Flags</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, menuContainer))%>" style="color:#fff;">-Training Data</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ErrorFilesAction.class, menuContainer))%>" style="color:#fff;">-Posting Errors</a></li>
        <li><a href="https://skyline.gs.washington.edu/labkey/project/home/issues/begin.view?" target="_blank" title="Report bugs/Request features.  Use 'TestResults' as area when creating new issue" style="color:#fff;">-Issues</a></li>
        <img src="<%=getWebappURL("TestResults/img/uw.png")%>" id="uw">
        <span id="stats"></span>
    </ul>
</div>
