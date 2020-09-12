<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.JournalGroupsAdminViewAction" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PXCredentialsForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    PXCredentialsForm form = ((JspView<PXCredentialsForm>) HttpView.currentView()).getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(JournalGroupsAdminViewAction.class);
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
            <td style="padding-top: 10px; padding-right: 5px;"><%=button("Save Credentials").submit(true)%></td>
            <td style="padding-top: 10px; padding-left: 5px;"><%=button("Cancel").href(panoramaPublicAdminUrl)%></td>
        </tr>
    </table>

</labkey:form>
</p>
