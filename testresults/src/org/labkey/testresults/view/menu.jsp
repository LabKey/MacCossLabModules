<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    final String menuContextPath = AppProps.getInstance().getContextPath();
    Container menuContainer = getViewContext().getContainer();
    // activeTab is set by parent JSP via request attribute before including menu.jsp
    String activeTab = (String) request.getAttribute("activeTab");
    if (activeTab == null) activeTab = "";
%>

<style>
    #menu.testresults-nav {
        background: #4b2e83;
        padding: 0 !important;
        padding-top: 6px !important;
        margin: 0 0 12px 0 !important;
        height: 40px !important;
        overflow: visible;
    }
    #menu.testresults-nav ul {
        list-style: none !important;
        margin: 0 !important;
        padding: 0 0 0 10px !important;
        display: inline;
    }
    #menu.testresults-nav li {
        display: inline;
        margin: 0;
        padding: 0;
    }
    #menu.testresults-nav li:hover {
        background-color: transparent !important;
    }
    .testresults-nav .nav-tab {
        display: inline-block;
        padding: 4px 10px;
        color: #fff;
        text-decoration: none;
        border-radius: 4px;
        transition: background 0.2s;
    }
    .testresults-nav .nav-tab:hover {
        background: #B8A506;
    }
    .testresults-nav .nav-tab.active {
        background: rgba(255, 255, 255, 0.25);
        font-weight: bold;
    }
    #menu.testresults-nav #stats {
        float: right;
        margin-right: 40px;
        margin-top: 6px;
        color: #fff;
        font-weight: 600;
    }
    #menu.testresults-nav #uw {
        float: right;
        margin-top: 2px;
    }
</style>

<div id="menu" class="testresults-nav">
    <img src="<%=getWebappURL("TestResults/img/uw.png")%>" id="uw" alt="UW">
    <span id="stats"></span>
    <ul>
        <li><a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("overview") ? " active" : ""))%>">Overview</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("user") ? " active" : ""))%>">User</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("run") ? " active" : ""))%>">Run</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.LongTermAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("longterm") ? " active" : ""))%>">Long Term</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowFlaggedAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("flags") ? " active" : ""))%>">Flags</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("trainingdata") ? " active" : ""))%>">Training Data</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ErrorFilesAction.class, menuContainer))%>" class="<%=h("nav-tab" + (activeTab.equals("errors") ? " active" : ""))%>">Posting Errors</a></li>
        <li><a href="/home/issues/project-begin.view" target="_blank" title="Report bugs/Request features. Use 'TestResults' as area when creating new issue" class="nav-tab">Issues</a></li>
    </ul>
</div>
