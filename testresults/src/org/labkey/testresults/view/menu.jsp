<%
/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.testresults.TestResultsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    final String menuContextPath = AppProps.getInstance().getContextPath();
    Container menuContainer = getViewContext().getContainer();
    // activeTab is set by parent JSP via request attribute before including menu.jsp
    String activeTab = (String) request.getAttribute(TestResultsController.TabNames.ACTIVE_TAB_ATTR);
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
        <li><a href="<%=h(new ActionURL(TestResultsController.BeginAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.OVERVIEW, activeTab))%>">Overview</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowUserAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.USER, activeTab))%>">User</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowRunAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.RUN, activeTab))%>">Run</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.LongTermAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.LONGTERM, activeTab))%>">Long Term</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ShowFlaggedAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.FLAGS, activeTab))%>">Flags</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.TrainingDataViewAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.TRAINING_DATA, activeTab))%>">Training Data</a></li>
        <li><a href="<%=h(new ActionURL(TestResultsController.ErrorFilesAction.class, menuContainer))%>" class="<%=h(TestResultsController.TabNames.getTabClass(TestResultsController.TabNames.ERRORS, activeTab))%>">Posting Errors</a></li>
        <li><a href="/home/issues/project-begin.view" target="_blank" title="Report bugs/Request features. Use 'TestResults' as area when creating new issue" class="nav-tab">Issues</a></li>
    </ul>
</div>
