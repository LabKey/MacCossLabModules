<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    LincsController.CromwellConfigForm form = ((JspView<LincsController.CromwellConfigForm>) HttpView.currentView()).getModelBean();
 %>
<p>
    Manage Cromwell Settings
</p>
<br>
<labkey:errors/>
<labkey:form method="post">
    <table>
        <tr>
            <td>Cromwell Server URL:</td>
            <td><input type="text" name="cromwellServerUrl" value="<%=h(form.getCromwellServerUrl())%>"></td>
        </tr>
        <tr>
            <td>Cromwell Server Port:</td>
            <td><input type="text" name="cromwellServerPort" value="<%=h(form.getCromwellServerPort())%>"></td>
        </tr>
        <tr>
            <td>Panoama API Key:</td>
            <td><input type="text" name="apiKey" value="<%=h(form.getApiKey())%>"></td>
        </tr>
        <tr>
            <td>LINCS Assay Type:</td>
            <td><input type="text" name="assayType" value="<%=h(form.getAssayType())%>"></td>
        </tr>
    </table>
    <labkey:button text="Save"></labkey:button>
</labkey:form>
