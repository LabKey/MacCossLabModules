<%
/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreManager" %>
<%@ page import="org.labkey.skylinetoolsstore.model.SkylineTool" %>
<%@ page import="org.labkey.skylinetoolsstore.view.SkylineToolStoreUrls" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.labkey.api.util.DOM.IMG" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.src" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.alt" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("skylinetoolsstore/js/functions.js");
    }

    public final HtmlString editIconImgHtml = DOM.createHtml(IMG(DOM.at(src, getWebappURL("skylinetoolsstore/img/pencil.png")).at(alt, "Pencil")));
%>
<script src="https://code.jquery.com/ui/1.13.2/jquery-ui.min.js" nonce="<%=getScriptNonce()%>"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.13.2/themes/smoothness/jquery-ui.min.css">

<%
    JspView<?> me = HttpView.currentView();
    final SkylineTool tool = (SkylineTool)me.getModelBean();
    final boolean admin = getUser().hasSiteAdminPermission();

    final String contextPath = AppProps.getInstance().getContextPath();
    final String imgDir = contextPath + "/skylinetoolsstore/img/";

    final SafeToRender autocompleteUsers = admin ? SkylineToolsStoreController.getUsersForAutocomplete() : HtmlString.unsafe("\"\"");

    final Container toolContainer = tool.lookupContainer(); // Cannot be null here

    // Get supporting files in map <url, icon url>
    HashMap<String, String> suppFiles = SkylineToolsStoreController.getSupplementaryFiles(tool);
    Iterator suppIter = suppFiles.entrySet().iterator();

    final String toolOwners = StringUtils.join(SkylineToolsStoreController.getToolOwners(tool), ", ");

    // A tool owner is granted the Editor role on the tool's own folder, which carries Insert, Update
    // and Delete together, so one check covers every control in the settings menu.
    final boolean toolEditor = admin || toolContainer.hasPermission(getUser(), InsertPermission.class);
    final SkylineTool[] allVersions = SkylineToolsStoreController.sortToolsByCreateDate(SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()));
    final boolean multipleVersions = allVersions.length > 1;
    final int numDownloads = Arrays.stream(allVersions).mapToInt(SkylineTool::getDownloads).sum();

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
.bottombar a > div {color: #126495;}
#allVersionsPop a {color: #126495;}
#allVersionsPop a:hover {color: #000;}
#toolOwners {width: 80%; min-width: 300px;}
#editToolDlg input[type=text],#editToolDlg textarea {width: 80%; min-width: 400px;}
#editToolDlg textarea {height: 80%; min-height: 200px;}
#toolDescription {text-align: justify;}
#downloadArea {margin: 15px auto 0 auto; text-align: center;}
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
.banner-button {
    display: inline-flex;
    align-items: center;
    margin: 0;
    padding: 15px;
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
.banner-button-small {
    display: inline-flex;
    align-items: center;
    margin: 0;
    padding: 10px 12px;
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
        <a href="<%=h(SkylineToolStoreUrls.getToolDetailsUrl(iVersion))%>">
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
    <labkey:form action="<%=urlFor(SkylineToolsStoreController.SetOwnersAction.class)%>" method="post">
        <p>
            <label for="toolOwners">Tool owners </label><br />
            <input type="text" id="toolOwners" name="toolOwners" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="toolId" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Update Tool Owners" />
        </p>
    </labkey:form>
</div>
<!--Upload New Version Form-->
<div id="uploadPop" title="Upload tool zip file" style="display:none;">
    <labkey:form action="<%=SkylineToolStoreUrls.getUpdateToolUrl(tool)%>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the zip file containing the tool you would like to upload.<br/><br/>
            <input type="file" size="50" name="toolZip" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="toolId" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Upload Tool" />
        </p>
    </labkey:form>
</div>
<!--Upload Supplementary File Form-->
<div id="uploadSuppPop" title="Upload supplementary file" style="display:none;">
    <labkey:form action="<%=SkylineToolStoreUrls.getInsertSupplementUrl(tool)%>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the supplementary file you would like to upload.<br/><br/>
            <input type="file" size="50" name="suppFile" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(toolDetailsUrl) %>" />
            <input type="hidden" name="toolId" value="<%= h(tool.getRowId()) %>" />
            <input type="submit" value="Upload Supplementary File" />
        </p>
    </labkey:form>
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
        <%=simpleLink(editIconImgHtml).addClass("toolProperty").id("editIcon").title("Icon").onClick("editTool($(this), 'Icon')")%>
<% } %>
        <div class="block">
            <h2><%= h(tool.getName()) %></h2>
            <p>
                Version <%= h(tool.getVersion()) %>
<% if (allVersions.length > 1) { %>
                [<%=simpleLink("View All").onClick("$('#allVersionsPop').dialog('open')")%>]
            </p>
<% } %>
            </p>
            <p>Uploaded <%= h(tool.getPrettyCreated()) %></p>

            <% if (!tool.getLatest()) { %>
            <p>
                <a class="importantLink" href="<%=h(SkylineToolStoreUrls.getToolDetailsUrl(allVersions[0]))%>">See latest version</a>
            <p>
<% } %>
        </div>

        <%
            Container supportContainer = getContainer().getChild("Support");
            Container toolSupportBoard = supportContainer != null ? supportContainer.getChild(tool.getName()) : null;
            if (toolSupportBoard == null)
                toolSupportBoard = ContainerManager.getForPath("/home/support");
        %>
        <% if (toolSupportBoard != null) { %>
        <button id="tool-support-board-btn" class="banner-button-small">Support Board</button>
        <% addHandler("tool-support-board-btn", "click", "window.open(" + q(urlProvider(ProjectUrls.class).getBeginURL(toolSupportBoard)) + ", '_blank', 'noopener,noreferrer')"); %>
        <% } %>
    </div>
<% if (toolEditor) { %>
    <div class="menuMouseArea sprocket">
        <img src="<%= h(imgDir) %>gear.png" title="Settings" alt="Sprocket" />
        <ul class="dropMenu">
            <li><%=simpleLink("Upload new version").onClick("$('#uploadPop').dialog('open')")%></li>
            <li><%=simpleLink("Upload supplementary file").onClick("$('#uploadSuppPop').dialog('open')")%></li>
<% if (multipleVersions) { %>
            <li><%=simpleLink("Delete latest version").onClick("$('#delToolLatestDlg').dialog('open')")%></li>
<% } %>
<% if (admin) { %>
            <li><%=simpleLink("Delete").onClick("$('#delToolAllDlg').dialog('open')")%></li>
            <li><%=simpleLink("Manage tool owners").onClick("popToolOwners()")%></li>
<% } %>
        </ul>
    </div>
<% } %>

    <p id="toolDescription" class="toolProperty" title="Description">
        <span class="toolPropertyValue"><%= h(tool.getDescription(), true) %></span>
<% if (toolEditor) { %>
        <%=simpleLink(editIconImgHtml).onClick("editTool($(this))")%>
<% } %>
    </p>
    <div id="downloadArea">
        <button id="download-tool-btn" class="banner-button">Download <%=h(tool.getName())%></button>
        <% addHandler("download-tool-btn", "click", "downloadTool(" + tool.getRowId() + ")"); %>
        <br>
        <strong>Downloaded: <span id="downloadcounter"><%= numDownloads %></span></strong>
    </div>
</div>

<%
    boolean hasDocumentation = tool.hasDocumentation();
%>
<% if (hasDocumentation || suppIter.hasNext()) { %>
<div id="documentationbox" class="itemsbox">
    <legend>Documentation</legend>
<% if (hasDocumentation) { %>
    <div class="barItem">
        <a href="<%=h(tool.getDocsUrl())%>" target="_blank" rel="noopener noreferrer">
        <img src="<%= h(imgDir) %>link.png" alt="Documentation" />
        <span>Online Documentation</span>
        </a>
    </div>
<% } %>
<%
    while (suppIter.hasNext()) {
        Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
    <div class="barItem suppfile">
        <a href="<%=h(suppPair.getKey())%>">
        <img src="<%=h(suppPair.getValue())%>" alt="Supplementary file" />
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
       <%=simpleLink(editIconImgHtml).onClick("editTool($(this))")%>
<% } %>
    </div>
<% } %>
<% if (tool.getAuthors() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Authors">
        <!--<img src="<%= h(imgDir) %>author.png" alt="Authors" />-->
        <span class="boldfont">Authors:</span>
        <span class="toolPropertyValue"><%= h(tool.getAuthors()) %></span>
<% if (toolEditor) { %>
        <%=simpleLink(editIconImgHtml).onClick("editTool($(this), 'author')")%>
<% } %>
    </div>
<% } %>
<% if (tool.getLanguages() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Languages">
        <!--<img src="<%= h(imgDir) %>language_type.png" alt="Languages" />-->
        <span class="boldfont">Languages:</span>
        <span class="toolPropertyValue"><%= h(tool.getLanguages()) %></span>
<% if (toolEditor) { %>
        <%=simpleLink(editIconImgHtml).onClick("editTool($(this))")%>
<% } %>
    </div>
<% } %>
<% if (tool.getProvider() != null || toolEditor) { %>
    <div class="barItem toolProperty" title="Provider's Website">
        <!--<img src="<%= h(imgDir) %>link.png" alt="Provider" />-->
        <span class="boldfont">More Information:</span>
        <a href="<%= h(tool.getProvider()) %>" target="_blank" rel="noopener noreferrer"><span class="toolPropertyValue"><%= h(tool.getProvider()) %></span></a>
<% if (toolEditor) { %>
        <%=simpleLink(editIconImgHtml).onClick("editTool($(this), 'provider')")%>
<% } %>
    </div>


<% } %>
</div>


<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    $(function() {
        $("#editIcon").position({my: "right bottom", at: "right bottom", of: $("#editIcon").siblings(".logoWrap:first")});
    });

<% if (toolContainer.hasPermission(getUser(), DeletePermission.class)) { %>
    $("#trashcan").droppable({
        accept: ".suppfile",
        drop: function(event, ui) {
            var offset = (ui.draggable).data("offset");
            var targetDel = (ui.draggable).find(".suppfilename").html().trim();
            if (!confirm("Really delete the supplementary file \"" + targetDel + "\"?")) {
                (ui.draggable).offset({top: offset.top, left: offset.left});
                return;
            }
            $.post("<%=h(SkylineToolStoreUrls.getDeleteSupplementUrl(tool))%>", {
                "toolId": <%= h(tool.getRowId()) %>,
                "suppFile": targetDel,
                "X-LABKEY-CSRF": LABKEY.CSRF
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

        window.location.href = <%= q(urlFor(SkylineToolsStoreController.DownloadToolAction.class).addParameter("id", tool.getRowId())) %>;
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
    $("#delToolAllDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                // Attributes are set via .attr() rather than built into an HTML string, so a value
                // cannot break out of the markup. Same pattern as the delete-latest dialog below.
                var form = $('<form method="post"></form>')
                        .attr('action', <%=q(urlFor(SkylineToolsStoreController.DeleteAction.class))%>);
                $('<input type="hidden">').attr('name', 'X-LABKEY-CSRF').attr('value', LABKEY.CSRF).appendTo(form);
                $('<input type="hidden">').attr('name', 'toolId').attr('value', <%=tool.getRowId()%>).appendTo(form);
                $('body').append(form);
                form.submit();
            },
            Cancel: function() {$(this).dialog("close");}
        }
    });

    $("#delToolLatestDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                // Submit a POST rather than navigating. DeleteLatestAction deletes a container, so it
                // must not be reachable by GET, and the CSRF token cannot ride on a navigation.
                // Attributes are set via .attr() rather than built into an HTML string so the sender
                // URL cannot break out of the markup.
                var form = $('<form method="post"></form>')
                        .attr('action', <%=q(urlFor(SkylineToolsStoreController.DeleteLatestAction.class))%>);
                $('<input type="hidden">').attr('name', 'X-LABKEY-CSRF').attr('value', LABKEY.CSRF).appendTo(form);
                $('<input type="hidden">').attr('name', 'toolId').attr('value', <%=tool.getRowId()%>).appendTo(form);
                $('<input type="hidden">').attr('name', 'sender')
                        .attr('value', <%=q(toolDetailsLatestUrl.getLocalURIString())%>).appendTo(form);
                $('body').append(form);
                form.submit();
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
                        "toolId": <%= tool.getRowId() %>,
                        "propName": propName,
                        "propValue": propValue
                    };
                } else {
                    postData = new FormData();
                    postData.append("toolId", <%= tool.getRowId() %>);
                    postData.append("propName", propName);
                    postData.append("propValue", document.getElementById("editIconFile").files[0]);
                }

                $(this).html("<p>Please wait...</p>");
                // Raw jQuery does not attach the CSRF token the way LABKEY.Ajax does, so the header
                // below sends it explicitly. That covers both the FormData and url-encoded cases.
                $.ajax({
                    type: "POST",
                    headers: {"X-LABKEY-CSRF": LABKEY.CSRF},
                    url: "<%=h(SkylineToolStoreUrls.getUpdatePropertyUrl(tool))%>",
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

    autocomplete($("#toolOwners"), <%=autocompleteUsers%>);
    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
