<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PanoramaPublicAdminViewAction" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.DataCiteCredentialsForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    DataCiteCredentialsForm form = ((JspView<DataCiteCredentialsForm>) HttpView.currentView()).getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(PanoramaPublicAdminViewAction.class);
%>
<p>
<labkey:form method="post">
    <table>
        <tr>
            <td  class='labkey-form-label'>User:</td>
            <td><input size="50" type="text" name="prodUser" value="<%=h(form.getProdUser())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Password:</td>
            <td><input size="50" type="text" name="password" value="<%=h(form.getPassword())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>DOI Prefix:</td>
            <td><input size="50" type="text" name="doiPrefix" value="<%=h(form.getDoiPrefix())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Test User:</td>
            <td><input size="50" type="text" name="testUser" value="<%=h(form.getTestUser())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Password:</td>
            <td><input size="50" type="text" name="testPassword" value="<%=h(form.getTestPassword())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Test DOI Prefix:</td>
            <td><input size="50" type="text" name="testDoiPrefix" value="<%=h(form.getTestDoiPrefix())%>"></td>
        </tr>
        <tr>
            <td style="padding-top: 10px; padding-right: 5px;"><%=button("Save Credentials").submit(true)%></td>
            <td style="padding-top: 10px; padding-left: 5px;"><%=button("Cancel").href(panoramaPublicAdminUrl)%></td>
        </tr>
    </table>

</labkey:form>
</p>
