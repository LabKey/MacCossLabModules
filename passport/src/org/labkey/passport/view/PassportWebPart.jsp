<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.passport.model.IProtein" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.passport.PassportController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<?> me = (JspView<?>) HttpView.currentView();
    Set<IProtein> data = (Set<IProtein>)me.getModelBean();
    if(data.size() > 0) {%>
        <table>
        <%for(IProtein p: data) {
        %>
            <tr>
                <td>
                    <strong><%=h(p.getName())%></strong>
                    <br />
                    Preferred Name: <%=h(p.getPreferredname())%>
                    <br/>
                    Gene: <%=h(p.getGene())%>
                    <br />
                    <a href="<%=h(new ActionURL(PassportController.ProteinAction.class, getContainer()))%>accession=<%=h(p.getAccession())%>">Link</a>
            </tr>
            <tr style="height:10px;"></tr>
        <%}%>
        </table>
    <%}%>
