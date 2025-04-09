<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.BlueskySettingsForm" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PanoramaPublicAdminViewAction" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    BlueskySettingsForm form = ((JspView<BlueskySettingsForm>) HttpView.currentView()).getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(PanoramaPublicAdminViewAction.class);
%>
<p>
<labkey:form method="post" enctype="multipart/form-data">
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
            <td class="labkey-form-label" style="text-align:center;">Panorama Public Logo:</td>
            <td>
                <input id="imageFileName" type="file" size="50" style="border: none; background-color: transparent;" accept="image/png,image/jpeg" name="imageFileName" />
                <input id="imageFileInput" name="imageFileInput" type="hidden"/>
                <% if (form.getImageFileName() != null) { %>
                    <%=link("View Logo",  urlFor(PanoramaPublicController.DownloadLogoForBlueskyAction.class))%>
                    <%=link("Delete Logo", urlFor(PanoramaPublicController.DeleteLogoForBlueskyAction.class)).usePost()%>
                <% } %>
                <div style="margin-top:5px;" class="greyText">
                    PNG or JPG/JPEG file in 16x9 ascpect ratio that will be included in the Bluesky post
                </div>
            </td>
        </tr>

        <tr>
            <td style="padding-top: 10px; padding-right: 5px;" colspan="2">
                <%=button("Save").submit(true)%>
                <%=button("Cancel").href(panoramaPublicAdminUrl)%>
            </td>
        </tr>
    </table>

</labkey:form>
</p>
