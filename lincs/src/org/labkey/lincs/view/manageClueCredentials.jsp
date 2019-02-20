<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Manage LINCS PSP Endpoint Config
</p>
<br>
<%
    LincsController.ClueCredentialsForm form = ((JspView<LincsController.ClueCredentialsForm>) HttpView.currentView()).getModelBean();
 %>

<labkey:form method="post">
    <table>
        <tr>
            <td>URL:</td>
            <td><input type="text" name="serverUri" value="<%=form.getServerUri()%>"></td>
        </tr>
        <tr>
            <td>API Key:</td>
            <td><input type="text" name="apiKey" value="<%=form.getApiKey()%>"></td>
        </tr>
    </table>
    <labkey:button text="Save"></labkey:button>
</labkey:form>
