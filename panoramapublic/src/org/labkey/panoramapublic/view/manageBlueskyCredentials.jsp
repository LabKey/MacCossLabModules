<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PanoramaPublicAdminViewAction" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.BlueskyCredentialsForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    BlueskyCredentialsForm form = ((JspView<BlueskyCredentialsForm>) HttpView.currentView()).getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(PanoramaPublicAdminViewAction.class);
%>
<p>
<labkey:form method="post">
    <table>
        <tr>
            <td  class='labkey-form-label'>User:</td>
            <td><input size="50" type="text" name="userName" value="<%=h(form.getUserName())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Password:</td>
            <td><input size="50" type="text" name="password" value="<%=h(form.getPassword())%>"></td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>User (test account):</td>
            <td><input size="50" type="text" name="testAccountUser" value="<%=h(form.getTestAccountUser())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Password (test account):</td>
            <td><input size="50" type="text" name="testAccountPassword" value="<%=h(form.getTestAccountPassword())%>"></td>
        </tr>

        <tr>
            <td style="padding-top: 10px; padding-right: 5px;"><%=button("Save Credentials").submit(true)%></td>
            <td style="padding-top: 10px; padding-left: 5px;"><%=button("Cancel").href(panoramaPublicAdminUrl)%></td>
        </tr>
    </table>

</labkey:form>
</p>
