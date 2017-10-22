<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.skylinetoolsstore.RatingManager" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreManager" %>
<%@ page import="org.labkey.skylinetoolsstore.model.Rating" %>
<%@ page import="org.labkey.skylinetoolsstore.model.SkylineTool" %>
<%@ page import="org.labkey.skylinetoolsstore.view.SkylineToolStoreUrls" %>
<%@ page import="java.io.File" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<?> me = (JspView<?>) HttpView.currentView();
    final SkylineTool tool = (SkylineTool)me.getModelBean();
    final boolean admin = getUser().isSiteAdmin();

    final String contextPath = AppProps.getInstance().getContextPath();
    final String cssDir = contextPath + "/skylinetoolsstore/css/";
    final String imgDir = contextPath + "/skylinetoolsstore/img/";
    final String jsDir = contextPath + "/skylinetoolsstore/js/";

    final String autocompleteUsers = admin ? SkylineToolsStoreController.getUsersForAutocomplete() : "\"\"";
    pageContext.setAttribute("autocompleteUsers", autocompleteUsers);

    // Get supporting files in map <url, icon url>
    HashMap<String, String> suppFiles = SkylineToolsStoreController.getSupplementaryFiles(tool);
    Iterator suppIter = suppFiles.entrySet().iterator();

    final String toolOwners = StringUtils.join(SkylineToolsStoreController.getToolOwners(tool), ", ");

    final boolean toolEditor = admin || tool.lookupContainer().hasPermission(getUser(), InsertPermission.class);
    final SkylineTool[] allVersions = SkylineToolsStoreController.sortToolsByCreateDate(SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()));
    final boolean multipleVersions = allVersions.length > 1;
    final boolean isLatestVersion = SkylineToolsStoreManager.get().getToolLatestByIdentifier(tool.getIdentifier()).getVersion().equals(tool.getVersion());
    final boolean leftReview = RatingManager.get().userLeftRating(tool.getIdentifier(), getUser());
    int numDownloads = 0;
    for (SkylineTool iVersion : allVersions)
        numDownloads += iVersion.getDownloads();

    ActionURL toolDetailsUrl = SkylineToolStoreUrls.getToolDetailsUrl(tool);
    ActionURL toolDetailsLatestUrl = SkylineToolStoreUrls.getToolDetailsLatestUrl(tool);
%>
<style>
a { text-decoration: none; }
.logoWrap {
    height: 100px;
    width: 100px;
    float: left;
    margin: 4px;
    border: 2px solid #dcdcdc;
}
#editIcon {opacity: 0.6; filter: alpha(opacity=60);}
.headerwrap {display: block; overflow: hidden;}
.headerwrap h3 {margin: 0 !important; padding: 5px 0 0; font-weight: 500 !important;}
.headerwrap p {margin: 0;}
.headerwrap h2 {margin: 0 !important; padding: 0 !important; font-weight: 500 !important;}
.reviewwrap {width: 100%; min-height: 40px;}
.block {
    float: left;
    margin: 0 0 0 3px !important;
    padding-right: 20px !important;
    padding-bottom: 15px;
}
.block p {padding-top: 4px;}
.importantLink {font-weight: 700; color: red; text-decoration: underline;}
.importantLink:hover {color: #000; text-decoration: none;}
.importantLink:active {color: #f60;}
.leftstyle {
    background: url('<%= h(imgDir) %>bg.jpg') repeat-y;
    margin-top: 20px;
    float: left;
    width: 100%;
}
.reviewwrap h2 {
    float: left;
    font-weight: 600;
    font-size: 1.2em;
    margin: -3px 0 0 30px;
    padding: 0 !important;
}
.reviewwrap h3 {
    float: left;
    padding: 0 !important;
    margin: 1px 0 0 50px !important;
    font-weight: 300 !important;
}
.reviewwrap h4 {margin: 0 !important; padding: 0 !important;}
.reviewwrap p {padding: 0; margin: 6px 0 0 30px !important;}
.reviewdate {float: right; margin: -12px 2% 0 0;}
.bottombar a > div {color: #126495;}
#allVersionsPop a {color: #126495;}
#allVersionsPop a:hover {color: #000;}
#toolOwners {width: 80%; min-width: 300px;}
#editToolDlg input[type=text],#editToolDlg textarea {width: 80%; min-width: 400px;}
#editToolDlg textarea {height: 80%; min-height: 200px;}
#toolDescription {text-align: justify;}
#downloadArea {margin: auto; text-align: center;}
#trashcan {
    position: fixed;
    left: 0;
    bottom: -300px;
    margin: 0;
    padding: 0;
    width: 300px;
    height: 300px;
    background: url('<%= h(imgDir) %>trashcan.png') no-repeat center center;
    background-size: cover;
    z-index: 99;
}
.ratingfull {
    height: 15px;
    background: url('<%= h(imgDir) %>star_full15x15.png') repeat-x left;
}
.ratingempty {
    width: 74px;
    height: 100%;
    background-size: 15px 15px;
    padding: 0 !important;
    margin: 0 !important;
    background: url('<%= h(imgDir) %>star_empty15x15.png') repeat-x left;
}
.ratingstars {
    width: 74px !important;
    height: 15px;
    float: left;
    overflow: hidden !important;
    border-spacing: 0;
    margin: -1px 0 0 30px !important;
    border-spacing: 0 !important;
}
.noCloseDlg .ui-dialog-titlebar-close {display: none;}
.itemsbox {
    min-height: 60px;
    min-width: 190px;
    border: 1px solid #000;
    background-color: #F5F6F7;
    -moz-border-radius: 5px;
    -webkit-border-radius: 5px;
    -khtml-border-radius: 5px;
    border-radius: 5px;
    margin-right: 20px;
    padding-left: 10px;
    margin-top: 25px;
    overflow: visible;
    float: left;
}
#addMissingProp img {float: right; margin-top: 4px;}
.barItem {
    background: #F5F6F7;
    padding: 0 0 5px;
    margin: 10px 24px 0 0;
    z-index: 1;
}
.barItem img {width: 15px; height: 15px;}
.itemsbox legend {
    font-size: 110%;
    font-weight: 600;
    margin: -10px 10px 0 0;
    position: relative;
    background-color: #F5F6F7;
    color: #000;
    border: 1px solid #000;
    max-width:150px;
}
a.banner-button {
    display: block;
    float:left;
    margin: 15px 0 0 0;
    padding: 5px 15px 0 15px;
    height: 25px;
    color: #fff;
    border-radius: 5px;
    font-size: 115%;
    font-weight: bold;
    border: 1px solid #215da0;
    text-shadow: -1px -1px #2e6db3;
    box-shadow: 0 2px #ccc;
    text-align: center;
    background: #73a0e2; /* Old browsers */
    /* IE9 SVG, needs conditional override of 'filter' to 'none' */
    background: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiA/Pgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgdmlld0JveD0iMCAwIDEgMSIgcHJlc2VydmVBc3BlY3RSYXRpbz0ibm9uZSI+CiAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkLXVjZ2ctZ2VuZXJhdGVkIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPgogICAgPHN0b3Agb2Zmc2V0PSIwJSIgc3RvcC1jb2xvcj0iIzczYTBlMiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiMyMTVkYTAiIHN0b3Atb3BhY2l0eT0iMSIvPgogIDwvbGluZWFyR3JhZGllbnQ+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgZmlsbD0idXJsKCNncmFkLXVjZ2ctZ2VuZXJhdGVkKSIgLz4KPC9zdmc+);
    background: -moz-linear-gradient(top,  #73a0e2 0%, #215da0 100%); /* FF3.6+ */
    background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,#73a0e2), color-stop(100%,#215da0)); /* Chrome,Safari4+ */
    background: -webkit-linear-gradient(top,  #73a0e2 0%,#215da0 100%); /* Chrome10+,Safari5.1+ */
    background: -o-linear-gradient(top,  #73a0e2 0%,#215da0 100%); /* Opera 11.10+ */
    background: -ms-linear-gradient(top,  #73a0e2 0%,#215da0 100%); /* IE10+ */
    background: linear-gradient(to bottom,  #73a0e2 0%,#215da0 100%); /* W3C */
    filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#73a0e2', endColorstr='#215da0',GradientType=0 ); /* IE6-8 */
}
a.banner-button-small{
    display: block;
    float:left;
    margin: 15px 0 0 0;
    padding: 2px 13px 0 9px;
    height: 15px;
    color: #fff;
    border-radius: 5px;
    font-size: 75%;
    font-weight: bold;
    border: 1px solid #6d0019;
    text-shadow: -1px -1px #6d0019;
    box-shadow: 0 2px #ccc;
    text-align: center;
    background: #a90329; /* Old browsers */
    /* IE9 SVG, needs conditional override of 'filter' to 'none' */
    background: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiA/Pgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgdmlld0JveD0iMCAwIDEgMSIgcHJlc2VydmVBc3BlY3RSYXRpbz0ibm9uZSI+CiAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkLXVjZ2ctZ2VuZXJhdGVkIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPgogICAgPHN0b3Agb2Zmc2V0PSIwJSIgc3RvcC1jb2xvcj0iIzczYTBlMiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiMyMTVkYTAiIHN0b3Atb3BhY2l0eT0iMSIvPgogIDwvbGluZWFyR3JhZGllbnQ+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgZmlsbD0idXJsKCNncmFkLXVjZ2ctZ2VuZXJhdGVkKSIgLz4KPC9zdmc+);
    background: -moz-linear-gradient(top,  #a90329 0%, #6d0019 100%); /* FF3.6+ */
    background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,#a90329), color-stop(100%,#6d0019)); /* Chrome,Safari4+ */
    background: -webkit-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* Chrome10+,Safari5.1+ */
    background: -o-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* Opera 11.10+ */
    background: -ms-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* IE10+ */
    background: linear-gradient(to bottom,  #a90329 0%,#6d0019 100%); /* W3C */
    filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#a90329', endColorstr='#6d0019',GradientType=0 ); /* IE6-8 */
}
.ui-menu {width: 240px;}
.dropMenu {position: absolute;}
.menuMouseArea {display: inline;}
.sprocket {cursor: pointer; float: right; margin: 0 0 8px 12px;}
.noCloseDlg .ui-dialog-titlebar-close {display: none;}
.boldfont {font-weight: 700;}
.ratingbutton {float: right;}
#ratingSlider, #ratingSliderPop
{
    border: 0 !important;
    width: 100px;
    height: 20px;
    background-color: #8e8d8d;
    background: url('<%= h(imgDir) %>star_empty20x20.png') repeat-x;
    z-index: 0;
    overflow: hidden;
    cursor:pointer;
}
#ratingSliderOver, #ratingSliderOverPop
{
    width:100px;
    height:20px;
    background: url('<%= h(imgDir) %>star_full20x20.png') repeat-x;
    z-index:99;
}
.ratinginput
{
    width:400px;
    float:left;
    border:0px !important;
}
#ratingform
{
    margin-top:24px;
    width:400px;
    border-radius: 5px 5px 5px 5px;
    -moz-border-radius: 5px 5px 5px 5px;
    -webkit-border-radius: 5px 5px 5px 5px;
    background-color: #e9e9e9;
    padding:12px 15px 20px 10px;
    height:170px;
    float:left;
    border: 1px #6E6E6E solid;
}
#ratingform legend
{
    font-weight: bold;
    font-size:14px;
    margin: -23px 10px 10px 0px;
    position: relative;
    background-color: #e9e9e9;
    color: #000;
    border: 1px #6E6E6E solid;
    max-width:150px;
    text-shadow: 1px 1px #fff;
}
#ratingform h3
{
    padding: 0px 0px 8px;
    margin: 0px;
}
#separatorborder
{
    border-bottom: 1px solid #d9d9d9;
    width:100%;
    padding-top:20px;
    margin-bottom:10px;

}
.ui-slider-handle {display: none;}
.versionheader {margin:0; padding:0;}
</style>
<div id="trashcan"></div>
<div id="allVersionsPop" title="All versions" style="display:none;">
<%
    for (SkylineTool iVersion : allVersions) {
        boolean viewingThis = iVersion.getVersion().equals(tool.getVersion());
 %>
    <p<% if (iVersion.getLatest()) { %> class="boldfont"<% } %>>
        <%= h(iVersion.getPrettyCreated()) %> |
<% if (!viewingThis) { %>
        <a href="<%= SkylineToolStoreUrls.getToolDetailsUrl(iVersion) %>">
<% } %>
            <%= h(iVersion.getName()) %> (version <%= h(iVersion.getVersion()) %>)
<% if (!viewingThis) { %>
        </a>
<% } %>
    </p>
<% } %>
</div>
<!--Manage Tool Owners Form-->
<div id="manageOwnersPop" title="Manage tool owners" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.SetOwnersAction.class) %>" method="post">
        <p>
            <label for="toolOwners">Tool owners </label><br />
            <input type="text" id="toolOwners" name="toolOwners" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="updatetarget" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Update Tool Owners" />
        </p>
    </form>
</div>
<!--Upload New Version Form-->
<div id="uploadPop" title="Upload tool zip file" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.InsertAction.class) %>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the zip file containing the tool you would like to upload.<br/><br/>
            <input type="file" size="50" name="toolZip" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="updatetarget" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Upload Tool" />
        </p>
    </form>
</div>
<!--Upload Supplementary File Form-->
<div id="uploadSuppPop" title="Upload supplementary file" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.InsertSupplementAction.class) %>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the supplementary file you would like to upload.<br/><br/>
            <input type="file" size="50" name="suppFile" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="supptarget" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Upload Supplementary File" />
        </p>
    </form>
</div>
<!--Submit Rating Web Form-->
<div id="reviewPop" title="Leave a review" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.SubmitRatingAction.class) %>" method="post">
        Title: <input type="text" name="title"><br>
        <input type="text" id="reviewValuePop" name="value" style="display:none;" value="5">
        <div id="ratingSliderPop">
            <div id="ratingSliderOverPop"></div>
        </div>
        <textarea name="review" rows="6" cols="60"></textarea><br /><br />
        <input type="hidden" name="toolId" value="<%= tool.getRowId() %>" />
        <input type="hidden" name="ratingId" value="" />
        <input type="submit" value="Submit Review" />
    </form>
</div>
<!--Delete Rating Dialog-->
<div id="delRatingDlg" title="Delete Review" style="display:none;">
    <p>Delete review?</p>
</div>
<!--Delete Tool Dialog-->
<div id="delToolAllDlg" title="Delete" style="display:none;">
    <p>Are you sure you want to completely delete <%= h(tool.getName()) %>?</p>
</div>
<!--Delete Tool Latest Version Dialog-->
<div id="delToolLatestDlg" title="Delete latest version" style="display:none;">
    <p>Are you sure you want to delete <%= h(allVersions[0].getName()) %> version <%= h(allVersions[0].getVersion()) %>?</p>
</div>
<!--Edit Tool Properties Dialog-->
<div id="editToolDlg" title="Edit tool properties" style="display:none;">
    <h3></h3>
    <input type="text" />
    <textarea></textarea>
    <input id="editIconFile" type="file" />
</div>

<div class="headerwrap">
    <div style="float:left; width:351px;">
        <img id="toolIcon" src="<%= h(tool.getIconUrl()) %>" class="logoWrap" alt="<%= h(tool.getName()) %>">
<% if (toolEditor) { %>
        <a id="editIcon" class="toolProperty" title="Icon" onclick="editTool($(this), 'Icon')">
            <img src="<%= h(imgDir) %>pencil.png" />
        </a>
<% } %>
        <div class="block">
            <h2><%= h(tool.getName()) %></h2>
            <p>
                Version <%= h(tool.getVersion()) %>
<% if (allVersions.length > 1) { %>
                [<a onclick="$('#allVersionsPop').dialog('open')">View All</a>]
            </p>
<% } %>
            </p>
            <p>Uploaded <%= h(tool.getPrettyCreated()) %></p>

            <% if (!tool.getLatest()) { %>
            <p>
                <a class="importantLink" href="<%= SkylineToolStoreUrls.getToolDetailsUrl(allVersions[0]) %>">See latest version</a>
            <p>
<% } %>
        </div>

        <a class="banner-button-small" style="float:left; margin-top:2px; margin-left:2px;" href="/labkey/project/home/software/Skyline/tools/Support/<%=h(tool.getName())%>/begin.view?" target="_blank">Support Board</a>

    </div>
<% if (toolEditor) { %>
    <div class="menuMouseArea sprocket">
        <img src="<%= h(imgDir) %>gear.png" title="Settings" alt="Sprocket" />
        <ul class="dropMenu">
            <li><a onclick="$('#uploadPop').dialog('open')">Upload new version</a></li>
            <li><a onclick="$('#uploadSuppPop').dialog('open')">Upload supplementary file</a></li>
<% if (multipleVersions) { %>
            <li><a onclick="$('#delToolLatestDlg').dialog('open')">Delete latest version</a></li>
<% } %>
<% if (admin) { %>
            <li><a onclick="$('#delToolAllDlg').dialog('open')">Delete</a></li>
            <li><a onclick="popToolOwners()">Manage tool owners</a></li>
<% } %>
        </ul>
    </div>
<% } %>

    <p id="toolDescription" class="toolProperty" title="Description">
        <span class="toolPropertyValue"><%= h(tool.getDescription(), true) %></span>
<% if (toolEditor) { %>
        <a onclick="editTool($(this))"><img src="<%= h(imgDir) %>pencil.png" alt="Pencil" title="Edit" /></a>
<% } %>
    </p>
    <table id="downloadArea">
        <tr>
            <td>
                <a class="banner-button" onclick="downloadTool(<%= h(tool.getRowId()) %>);">Download <%=h(tool.getName())%> </a>
            </td>
        </tr>
        <tr>
            <td>
                <strong>Downloaded: <span id="downloadcounter"><%= numDownloads %></span></strong>
            </td>
        </tr>
    </table>
</div>

<% if (suppIter.hasNext()) { %>
<div id="documentationbox" class="itemsbox">
    <legend>Documentation</legend>
<%
    while (suppIter.hasNext()) {
        Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
    <div class="barItem suppfile">
        <a href="<%= suppPair.getKey() %>">
        <img src="<%= suppPair.getValue() %>" alt="Supplementary file" />
        <span class="suppfilename"><%= h(new File(suppPair.getKey().toString()).getName()) %></span>
        </a>
    </div>
<% } %>
</div>
<% } %>
<div id="toolinformationbox" class="itemsbox">
    <legend>Tool Information</legend>
<% if (tool.getOrganization() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Organization">
        <!--<img src="<%= h(imgDir) %>organization.png" alt="Organization" /> -->
        <span class="boldfont">Organization:</span>
        <span class="toolPropertyValue"><%= h(tool.getOrganization()) %></span>
<% if (toolEditor) { %>
        <a onclick="editTool($(this))"><img src="<%= h(imgDir) %>pencil.png" alt="Pencil" title="Edit" /></a>
<% } %>
    </div>
<% } %>
<% if (tool.getAuthors() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Authors">
        <!--<img src="<%= h(imgDir) %>author.png" alt="Authors" />-->
        <span class="boldfont">Authors:</span>
        <span class="toolPropertyValue"><%= h(tool.getAuthors()) %></span>
<% if (toolEditor) { %>
        <a onclick="editTool($(this), 'author')"><img src="<%= h(imgDir) %>pencil.png" alt="Pencil" title="Edit" /></a>
<% } %>
    </div>
<% } %>
<% if (tool.getLanguages() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Languages">
        <!--<img src="<%= h(imgDir) %>language_type.png" alt="Languages" />-->
        <span class="boldfont">Languages:</span>
        <span class="toolPropertyValue"><%= h(tool.getLanguages()) %></span>
<% if (toolEditor) { %>
        <a onclick="editTool($(this))"><img src="<%= h(imgDir) %>pencil.png" alt="Pencil" title="Edit" /></a>
<% } %>
    </div>
<% } %>
<% if (tool.getProvider() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Provider's Website">
        <!--<img src="<%= h(imgDir) %>link.png" alt="Provider" />-->
        <span class="boldfont">More Information:</span>
        <a href="<%= h(tool.getProvider()) %>" target="_blank"><span class="toolPropertyValue"><%= h(tool.getProvider()) %></span></a>
<% if (toolEditor) { %>
        <a onclick="editTool($(this), 'provider')"><img src="<%= h(imgDir) %>pencil.png" alt="Pencil" title="Edit" /></a>
<% } %>
    </div>


<% } %>
</div>

<% if (!getUser().isGuest() && isLatestVersion && !leftReview) { %>

<form action="<%= urlFor(SkylineToolsStoreController.SubmitRatingAction.class) %>" method="post" id="ratingform">
    <legend>Leave a Review</legend>
    <%--<center><h3>Leave a Review</h3></center>--%>
    <input type="text" name="title" class="ratinginput">
    <input type="text" id="reviewValue" name="value" style="display:none;" value="5">
    <br><br>
    <textarea name="review" rows="6" cols="40" class="ratinginput"></textarea><br /><br />
    <input type="hidden" name="toolId" value="<%= tool.getRowId() %>" />
    <input type="hidden" name="ratingId" value="" />
    <div id="ratingSlider" style="float:left; margin-top:15px; margin-left:10px;">
        <div id="ratingSliderOver"></div>
    </div>
    <input type="submit" value="Submit Review" style="float:right; margin-right:10px; margin-top:10px;"/>
</form>
<br />

<% } %>
<div style="width:100%; height:100%; overflow:hidden;">
    <div id="separatorborder">
    </div>
<%
    Rating[] ratings = RatingManager.get().getRatingsByToolAllVersions(tool.getIdentifier());

    if (!tool.getVersion().equals(SkylineToolsStoreManager.get().getToolLatestByIdentifier(tool.getIdentifier()).getVersion()))
        ratings = RatingManager.get().getRatingsByToolId(tool.getRowId());

    List<String> usedVersions = new ArrayList<>();

    for (Rating rating : ratings)
    {
        final String tableId2 = "table-" + rating.getRowId();
        final String reviewTitle = rating.getTitle();
        final String review = h(rating.getReview()).replace("\r\n", "\n").replace("\n", "<br>");
        pageContext.setAttribute("review", review);
        pageContext.setAttribute("reviewEscaped", review.replace("&#039;", "\\'"));
        final Integer ratingValue = rating.getRating();
        final String ratingVersion =  SkylineToolsStoreManager.get().getTool(rating.getToolId()).getVersion();

        DateFormat df = new SimpleDateFormat("MM/dd/yy");
        String formattedDate = df.format(rating.getModified());

        if (!usedVersions.contains(ratingVersion)) {
            usedVersions.add(ratingVersion);
            if (tool.getVersion().equals(SkylineToolsStoreManager.get().getToolLatestByIdentifier(tool.getIdentifier()).getVersion()) && multipleVersions)  {
%>
<div id="version-<%= h(ratingVersion) %>" class="versionheader" style="text-align: center;"><h3>Version <%= h(ratingVersion) %> ratings</h3></div>
<%
            }
        }
%>
<div class="leftstyle" id="<%= h(tableId2) %>" >
    <div class="reviewwrap">
        <div class="reviewbar">
            <h2><%= h(reviewTitle) %></h2>
            <div class="ratingstars">
                <div class="ratingempty">
                    <div class="ratingfull" style="width:<%= h(ratingValue * 15) %>px;"></div>
                </div>
            </div>
            <br />
            <div class="reviewdate"><h4><%= h(formattedDate) %></h4></div>
        </div>
        <p>
            ${review}
<% if (rating.getCreatedBy() == getUser().getUserId() || admin) { %>
            <br />
            <button type="button" class="ratingbutton" style="margin-right:25px;" onclick="prepReviewPop('<%= h(reviewTitle) %>', <%= ratingValue %>, '${reviewEscaped}', <%= rating.getRowId() %>); $('#reviewPop').dialog('open')">
                Edit Review
            </button>
            <button type="button" class="ratingbutton" onclick="$('#delRatingDlg').dialog('open').data('ratingId', <%= rating.getRowId() %>)">
                Delete Review
            </button>
<% } %>
       </p>
    </div>
</div>

<%
    }
%>
<%--<% if (!getUser().isGuest() && isLatestVersion && !leftReview) { %>--%>
    <%--<p style="text-align: center;">--%>
        <%--<a onclick="$('#reviewPop').dialog('open')">Leave a review</a>--%>
    <%--</p>--%>
<%--<% } %>--%>
</div>

<link rel="stylesheet" type="text/css" href="<%= h(cssDir) %>jquery-ui.css">
<script type="text/javascript" src="<%= h(jsDir) %>functions.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>

<script>
    $(function() {
        initRatingSlider($("#ratingSlider"), $("#ratingSliderOver"), $("#reviewValue"));
        initRatingSlider($("#ratingSliderPop"), $("#ratingSliderOverPop"), $("#reviewValuePop"));
        $("#editIcon").position({my: "right bottom", at: "right bottom", of: $("#editIcon").siblings(".logoWrap:first")});
    });

<% if (tool.lookupContainer().hasPermission(getUser(), DeletePermission.class)) { %>
    $("#trashcan").droppable({
        accept: ".suppfile",
        drop: function(event, ui) {
            var offset = (ui.draggable).data("offset");
            var targetDel = (ui.draggable).find(".suppfilename").html().trim();
            if (!confirm("Really delete the supplementary file \"" + targetDel + "\"?")) {
                (ui.draggable).offset({top: offset.top, left: offset.left});
                return;
            }
            $.post("<%= urlFor(SkylineToolsStoreController.DeleteSupplementAction.class) %>", {
                "supptarget": <%= h(tool.getRowId()) %>,
                "suppFile": targetDel
            }).done(function() {
                (ui.draggable).hide("explode");
                if ($("#documentationbox").children(".suppfile:visible").length <= 1)
                    $("#documentationbox").hide("fade");
            }).fail(function() {
                (ui.draggable).offset({top: offset.top, left: offset.left});
                alert("An error occurred while trying to delete the file.");
            });
        }
    });

    var TRASH_SLIDE_DURATION = 200;
    $(".suppfile").each(function() {
        $(this).draggable({
            start: function() {
                $(this).tooltip("disable")
                       .data("offset", $(this).offset())
                       .css("box-shadow", "10px 10px 5px #888888").css("padding", "8px")
                       .css("z-index", "500")
                       .css("border-radius", "8px").css("border", "1px solid #cccccc");
                $("#trashcan").animate({bottom: 0}, TRASH_SLIDE_DURATION);
            },
            stop: function() {
                $(this).tooltip("enable")
                       .css("box-shadow", "").css("padding", "").css("border-radius", "")
                       .css("border", "").css("z-index", "");
                $("#trashcan").animate({bottom: "-300px"}, TRASH_SLIDE_DURATION)
            },
            revert: "invalid"
        })
        .attr("title", "Click and drag this file to delete it").tooltip();
    });
<% } %>
    var MENU_SLIDE_TIME = 100;
    function initMenu(element) {
        var myMenu = element.children(".dropMenu:first");
        if (myMenu.children().length > 0) {
            myMenu.menu().hide();
            element.click(function(e) {
                // Stop click from bubbling up to document click handler
                e.stopPropagation();
                // Only allow one menu open at a time
                if ($(this).children(".dropMenu:first").is(":hidden"))
                    closeMenus();
                myMenu.stop().slideToggle(MENU_SLIDE_TIME);
                myMenu.position({of: $(element).children(":first"), at: "left bottom", my: "left top"});
            });
        }
    }

    // Close menus on non-menu click
    $(document).click(function() {closeMenus();});

    function closeMenus() {
        $(".dropMenu:visible").slideUp(MENU_SLIDE_TIME);
    }

    $(".menuMouseArea").each(function() {initMenu($(this));});

    var REPLACE_TEXT_FADE_TIME = 250;

    function downloadTool(toolId) {
        if (getCookie("<%= h(SkylineToolsStoreController.DownloadToolAction.DOWNLOADED_COOKIE_PREFIX) %>" + toolId) != "1") {
            var downloadCounter = $("#downloadcounter");
            downloadCounter.fadeOut(REPLACE_TEXT_FADE_TIME, function() {
                downloadCounter.html(parseInt(downloadCounter.html()) + 1);
                downloadCounter.fadeIn(REPLACE_TEXT_FADE_TIME);
            });
        }

        window.location.href = "<%= urlFor(SkylineToolsStoreController.DownloadToolAction.class) %>id=" + toolId;
    }

    function popToolOwners() {
        var ownersTxt = $("#toolOwners");
        $("#manageOwnersPop").dialog("open");
        ownersTxt.focus().val("<%= h(toolOwners) %>");
        if (ownersTxt.val())
            ownersTxt.val(ownersTxt.val() + ", ");
    }

    var DLG_EFFECT_SHOW = "fade";
    var DLG_EFFECT_HIDE = "fade";
    $("#allVersionsPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#uploadPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#manageOwnersPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#uploadSuppPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#reviewPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});

    $("#delRatingDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                window.location = "<%= urlFor(SkylineToolsStoreController.DeleteRatingAction.class) %>id=" + $("#delRatingDlg").data("ratingId");
            },
            Cancel: function() {$(this).dialog("close");}
        }
    });

    $("#delToolAllDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                window.location = "<%= urlFor(SkylineToolsStoreController.DeleteAction.class).addParameter("id", tool.getRowId()) %>"
            },
            Cancel: function() {$(this).dialog("close");}
        }
    });

    $("#delToolLatestDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                window.location = "<%= urlFor(SkylineToolsStoreController.DeleteLatestAction.class).addParameter("id", tool.getRowId()).addParameter("sender", toolDetailsLatestUrl.getLocalURIString()) %>"
            },
            Cancel: function() {$(this).dialog("close");}
        }
    });

    $("#editToolDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                var propName = $(this).data("propName");
                var propValue;
                var isIcon = (propName.toLowerCase() == "icon") ? true : false;
                var postData;
                if (!isIcon) {
                    propValue = $(this).children("input:text:visible, textarea:visible").first().val().replace(/r?\n/g, "\r\n").replace(/\\*$/, "");
                    postData = {
                        "id": <%= tool.getRowId() %>,
                        "propName": propName,
                        "propValue": propValue
                    };
                } else {
                    postData = new FormData();
                    postData.append("id", <%= tool.getRowId() %>);
                    postData.append("propName", propName);
                    postData.append("propValue", document.getElementById("editIconFile").files[0]);
                }

                $(this).html("<p>Please wait...</p>");
                $.ajax({
                    type: "POST",
                    url: "<%= urlFor(SkylineToolsStoreController.UpdatePropertyAction.class) %>",
                    data: postData,
                    success: function() {
                        $("#editToolDlg").dialog("close");
                        var container = $("#editToolDlg").data("propValueContainer");
                        if (isIcon) {
                            var newImgSrc = container.attr("src") + "?" + (new Date()).getTime();
                            container.animate({opacity: 0}, REPLACE_TEXT_FADE_TIME, function() {
                                container.attr("src", newImgSrc)
                                         .load(function() {
                                            $(this).animate({opacity: 1}, REPLACE_TEXT_FADE_TIME);
                                         });
                            });
                            return;
                        }
                        var containerParent = container.parent();
                        if (containerParent.is("a") && containerParent.attr("href") == container.text())
                            containerParent.attr("href", propValue);
                        container.parents(".toolProperty:first").fadeOut(REPLACE_TEXT_FADE_TIME, function() {
                            var toolPropertyElement = container.closest(".toolProperty");
                            container.html(propValue.replace(/\n/g, "<br />"));
                            $(this).fadeIn(REPLACE_TEXT_FADE_TIME);
                        });
                    },
                    error: function() {
                        $("#editToolDlg").html("<p>An error occurred trying to edit \"" + propName + "\".</p>");
                        $(".ui-dialog-buttonpane button:contains('Ok')").button().hide();
                        setButtonsEnabled(true);
                    },
                    contentType: (!isIcon ? "application/x-www-form-urlencoded; charset=UTF-8" : false),
                    processData: !isIcon
                });
            },
            Cancel: function() {$(this).dialog("close");}
        },
        close: function() {
            $(".ui-dialog-buttonpane button:contains('Ok')").button().show();
            setButtonsEnabled(true);
        }
    }).data("originalHtml", $("#editToolDlg").html())
      .keydown(function (e) {
          if (e.keyCode == 13 &&
              ($(this).children("input:text:visible").length > 0 ||
              (e.ctrlKey && $(this).children("textarea:visible").length > 0)))
              $(this).parent().find("button:eq(1)").trigger("click");
      });

    function editTool(sender, property) {
        var parent = sender.closest(".toolProperty");
        var propName = property || parent.attr("title");
        var propValueContainer = (propName.toLowerCase() != "icon") ?
            parent.find(".toolPropertyValue:first") : $("#toolIcon");

        var targetType = "input:text";
        var hideType = "textarea, input:file";
        if (propName.toLowerCase() == "description") {
            targetType = "textarea";
            hideType = "input:text, input:file";
        } else if (propName.toLowerCase() == "icon") {
            targetType = "input:file";
            hideType = "input:text, textarea";
        }

        $("#editToolDlg").html($("#editToolDlg").data("originalHtml"))
                         .data("propName", propName)
                         .data("propValueContainer", propValueContainer)
                         .children("h3:first").text(parent.attr("title")).end()
                         .children(hideType).hide().end()
                         .dialog("open")
                         .children(targetType + ":first").show().focus().val(propValueContainer.text());
    }

    function prepReviewPop(title, value, content, ratingId) {
        $("#reviewPop").find('input[name="title"]').val(title).end()
                       .find('input[name="value"]').val(value).end()
                       .find('textarea[name="review"]').html(content.replace(/<br>/g, "&#13;&#10;")).end()
                       .find('input[name="ratingId"]').val(ratingId);
        $('#sliderover').css('width', value * 20);
    }

    autocomplete($("#toolOwners"), ${autocompleteUsers});
    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
