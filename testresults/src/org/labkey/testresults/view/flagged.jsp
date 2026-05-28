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
<%@ page import="org.labkey.testresults.TestResultsController.ShowRunAction" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("TestResults/css/style.css");
    }
%>

<%
    /*
      User: Yuval Boss, yuval(at)uw.edu
      Date: 10/05/2015
     */
    JspView<?> me = HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
%>

<% request.setAttribute(TestResultsController.TabNames.ACTIVE_TAB_ATTR, TestResultsController.TabNames.FLAGS); %>
<%@include file="menu.jsp" %>

<p>Runs which are flagged will not show up in the Overview breakdown, Long Term, and Failure pages.  This includes graphs, charts, and any other sort of data visualization.</p>
<%if(data.getRuns().length == 0){    %>
    <p>There are currently no flagged runs.</p>
<%} else {%>
    <table class="decoratedtable" style="float:left;">
        <tr><td>Flagged Runs</td></tr>
        <%  RunDetail[] runs = data.getRuns();
            Arrays.sort(runs);
            Collections.reverse(Arrays.asList(runs));
            for(RunDetail run: runs) {%>
        <tr>
            <td><a href="<%=h(urlFor(ShowRunAction.class).addParameter("runId", run.getId()))%>">  id: <%=run.getId()%> / <%=run.getUserid()%> / <%=formatDateTime(run.getPostTime())%></a></td>
        </tr>
        <%}%>
    </table>
<%}%>