<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.testresults.TestResultsController.ShowRunAction" %>
<%@ page import="org.labkey.testresults.model.RunDetail" %>
<%@ page import="org.labkey.testresults.view.TestsDataBean" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
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
    JspView<?> me = (JspView<?>) HttpView.currentView();
    TestsDataBean data = (TestsDataBean)me.getModelBean();
%>

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