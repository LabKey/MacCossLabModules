<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PanoramaPublicAdminViewAction" %>
<%@ page import="org.labkey.panoramapublic.bluesky.BlueskySettings" %>
<%@ page import="org.labkey.panoramapublic.bluesky.BlueskySettingsManager" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>
<%
    JspView<BlueskySettings> currentView = HttpView.currentView();
    BlueskySettings form = currentView.getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(PanoramaPublicAdminViewAction.class);
%>
<p>
<labkey:form method="post" enctype="multipart/form-data">
    <table>
        <tr>
            <td  class='labkey-form-label'>Account:</td>
            <td><input size="50" type="text" name="account" value="<%=h(form.getAccount())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Password:</td>
            <td><input size="50" type="text" name="password" value="<%=h(form.getPassword())%>"></td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Test account:</td>
            <td><input size="50" type="text" name="testAccount" value="<%=h(form.getTestAccount())%>"></td>
        </tr>
        <tr>
            <td  class='labkey-form-label'>Test account password:</td>
            <td><input size="50" type="text" name="testAccountPassword" value="<%=h(form.getTestAccountPassword())%>"></td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Auth URL:</td>
            <td>
                <input size="50" type="text" name="authEndpoint" value="<%=h(form.getAuthEndpoint())%>">
                <div style="font-size: 0.8em; color:gray">e.g. <%=h(BlueskySettingsManager.DEFAULT_AUTH_URL)%></div>
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Post URL:</td>
            <td>
                <input size="50" type="text" name="postEndpoint" value="<%=h(form.getPostEndpoint())%>">
                <div style="font-size: 0.8em; color:gray">e.g. <%=h(BlueskySettingsManager.DEFAULT_POST_URL)%></div>
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Image upload URL:</td>
            <td>
                <input size="50" type="text" name="blobUploadEndpoint" value="<%=h(form.getBlobUploadEndpoint())%>">
                <div style="font-size: 0.8em; color:gray">e.g. <%=h(BlueskySettingsManager.DEFAULT_IMAGE_UPLOAD_URL)%></div>
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Announcement text:</td>
            <td>
                <input size="50" type="text" name="announcementText" value="<%=h(form.getAnnouncementText())%>">
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Hashtags:</td>
            <td>
                <input size="50" type="text" name="hashtags" value="<%=h(form.getHashtags())%>">
                <div style="font-size: 0.8em; color:gray">Hashtags associated with the post, comma-separated (e.g proteomics, proteomicssky, massspec, massspecsky)</div>
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Hashtags (test account):</td>
            <td>
                <input size="50" type="text" name="testHashtags" value="<%=h(form.getTestHashtags())%>">
                <div style="font-size: 0.8em; color:gray">Hashtags associated with the post to the test account, comma-separated.</div>
            </td>
        </tr>

        <tr>
            <td  class='labkey-form-label'>Auto-post to Bluesky on publish:</td>
            <td>
                <input type="checkbox" name="autopost" <%=checked(form.isAutopost())%>/>
            </td>
        </tr>

        <tr>
            <td class="labkey-form-label" style="text-align:center;">Panorama Public Logo:</td>
            <td>
                <% if (form.getImageFileName() != null) { %>
                    <%=link("View Logo",  urlFor(PanoramaPublicController.DownloadPanoramaLogoForBlueskyAction.class))%>
                    <%=link("Delete Logo", urlFor(PanoramaPublicController.DeletePanoramaLogoForBlueskyAction.class)).usePost()%>
                <% } %>
                <input id="imageFileInput" type="file" size="50" style="border: none; background-color: transparent;" accept="image/png,image/jpeg" name="imageFileInput" />
                <input id="imageFileName" name="imageFileName" type="hidden" value="<%=h(form.getImageFileName())%>"/>

                <div style="margin-top:5px; color:gray" class="greyText">
                    PNG or JPG/JPEG file in 16x9 aspect ratio that will be included in the Bluesky post
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
