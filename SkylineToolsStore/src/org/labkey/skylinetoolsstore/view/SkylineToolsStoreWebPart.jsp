<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.SafeToRender"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
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
<%@ page import="org.labkey.api.collections.IntHashMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("skylinetoolsstore/js/functions.js");
    }
%>
<script src="https://code.jquery.com/ui/1.13.2/jquery-ui.min.js" nonce="<%=getScriptNonce()%>"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.13.2/themes/smoothness/jquery-ui.min.css">

<%
    JspView<?> me = HttpView.currentView();
    List<SkylineTool> tools = (List<SkylineTool>)me.getModelBean();

    final boolean admin = getUser().hasSiteAdminPermission();
    final boolean loggedIn = !getUser().isGuest();
    // This web part can be added to any folder's page, including a tool's own version folder,
    // where InsertToolAction refuses an upload. Hide the button rather than offer one that
    // cannot work.
    final boolean canAddTool = admin && SkylineToolsStoreController.isStoreContainer(getContainer());

    final String contextPath = AppProps.getInstance().getContextPath();
    final String imgDir = contextPath + "/skylinetoolsstore/img/";

%>
<style type="text/css">
    .tablewrap {width:100%; min-width:600px; margin-top:20px;}
    .tablewrap td {padding:0; margin:0;}
    .tablewrap:nth-child(odd) {background:#f4f4f4;}
    .leftfill {width:20px; height:100%; background:url('<%= h(imgDir) %>bg.jpg') repeat-y;}
    .icon {height:100px; width:100px; margin: 4px 0 0 10px; border:2px solid #dcdcdc;}
    .title {font-size: 150%; font-weight: 400; color:#0044cc; margin: 0 !important; padding: 0; float: left;}
    .title:hover {text-decoration: underline;}
    .title:active {color: #ff0000;}
    .toolSubtitle {font-size: 14px; margin:0; padding:0; clear: both;}
    .contentleft {width: 128px; vertical-align: top;}
    .contentright {vertical-align: top;}
    .contentcontainer {margin:0; padding:0;}
    .content {margin: 8px 12px 0 0; padding:0; text-align:justify;}
    .toolButtons {margin-top: 12px;}
    .styled-button{
        display:inline-flex;
        align-items:center;
        box-shadow:rgba(0,0,0,0.0.1) 0 1px 0 0;
        background-color:#5B74A8;
        border:1px solid #29447E;
        font-family:'Lucida Grande',Tahoma,Verdana,Arial,sans-serif;
        font-size:12px;
        font-weight:700;
        margin-top: 4px;
        padding:2px 6px;
        height:28px;
        color:#fff;
        border-radius:5px;
        cursor:pointer;
    }
    .styled-button:hover{background-color:#1e90ff; color:#f5f5dc;}
    a.styled-button{text-decoration:none; color:#fff;}
    a.styled-button:visited{color:#fff;}
    .toolOwners {width: 80%; min-width: 300px;}
    .ui-menu {width:240px;}
    .dropMenu {position: absolute;}
    .menuMouseArea {display: inline;}
    .sprocket {cursor: pointer; float: right;}
    .menuIconImg {width: 16px; height: 16px;}
    .noCloseDlg .ui-dialog-titlebar-close {display: none;}

</style>

<% if (canAddTool) { %>
<div style="float: left;">
    <button type="button" id="add-new-tool-btn" class="styled-button">Add New Tool</button>
    <% addHandler("add-new-tool-btn", "click",
            "$('#uploadForm').attr('action', " + q(SkylineToolStoreUrls.getInsertToolUrl(getContainer())) + "); " +
            "$('#uploadPopOwners').show(); $('#uploadFormToolId').val('0'); $('#uploadPop').dialog('open')"); %>
</div>
<% } %>
<!--Manage Tool Owners Form-->
<div id="manageOwnersPop" title="Manage tool owners" style="display:none;">
    <labkey:form action="<%=urlFor(SkylineToolsStoreController.SetOwnersAction.class)%>" method="post">
        <p>
            <label for="toolOwnersManage">Tool owners </label><br />
            <input type="text" id="toolOwnersManage" class="toolOwners" name="toolOwners" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(getActionURL()) %>" />
            <%-- Set per tool when the dialog opens. Zero rather than blank, because an empty string
                 will not bind to the form's int and would fail before the action ever runs. --%>
            <input type="hidden" id="ownersFormToolId" name="toolId" value="0" />
            <input type="submit" value="Update Tool Owners" />
        </p>
    </labkey:form>
</div>
<!--Add Tool / Upload New Version Form-->
<div id="uploadPop" title="Upload tool zip file" style="display:none;">
    <%-- Serves both "Add New Tool" and per-tool "Upload new version", which are different actions in
         different containers, so each handler below sets the action. Defaults to adding a new tool. --%>
    <labkey:form id="uploadForm" action="<%=SkylineToolStoreUrls.getInsertToolUrl(getContainer())%>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the zip file containing the tool you would like to upload.<br/><br />
            <input type="file" name="toolZip" /><br /><br />
            <span id="uploadPopOwners">
                <label for="toolOwnersNew">Tool owners </label><br />
                <input type="text" id="toolOwnersNew" class="toolOwners" name="toolOwners" /><br /><br /><br />
            </span>
            <input type="hidden" name="sender" value="<%= h(getActionURL()) %>" />
            <%-- Zero for "Add New Tool", which InsertToolAction ignores. A blank value would not
                 bind to the form's int, so the upload would fail before reaching the action. --%>
            <input type="hidden" id="uploadFormToolId" name="toolId" value="0" />
            <input type="submit" value="Upload Tool" />
        </p>
    </labkey:form>
</div>
<!--Upload Supplementary File Form-->
<div id="uploadSuppPop" title="Upload supplementary file" style="display:none;">
    <%-- One dialog serves every tool, so the action is set per tool in the menu handler below.
         insertSupplement is addressed to the tool's own container. --%>
    <labkey:form id="uploadSuppForm" enctype="multipart/form-data" method="post">
        <p>
            Browse to the supplementary file you would like to upload.<br/><br/>
            <input type="file" name="suppFile" /><br /><br />
            <%-- Set per tool when the dialog opens. See the note on ownersFormToolId above. --%>
            <input type="hidden" id="suppFormToolId" name="toolId" value="0" />
            <input type="submit" value="Upload Supplementary File" />
        </p>
    </labkey:form>
</div>
<!-- Delete Tool Dialog -->
<div id="delToolAllDlg" title="Delete tool from store" style="display:none;"></div>
<!-- Delete Tool Latest Version Dialog -->
<div id="delToolLatestDlg" title="Delete latest version" style="display:none;"></div>

<div style="float: right;">
    <label for="sort-selector">Sort by:</label>
    <select id="sort-selector">
        <option value="name-asc">Name &uarr;</option>
        <option value="name-desc">Name &darr;</option>
        <option value="downloads-asc">Downloads &uarr;</option>
        <option value="downloads-desc">Downloads &darr;</option>
    </select>
</div>

<div id="all-tools" style="clear: both; padding-top: 2px;">
<%
    HashMap<Integer, String> toolOwners = new IntHashMap<>();
    for (SkylineTool tool : tools)
    {
        final String tableId = "table-" + tool.getName().replaceAll("[^A-Za-z0-9]", "");
        final ActionURL detailsUrl = SkylineToolStoreUrls.getToolDetailsUrl(tool);

        // Get supporting files in map <url, icon url>
        HashMap<String, String> suppFiles = SkylineToolsStoreController.getSupplementaryFiles(tool);
        Iterator suppIter = suppFiles.entrySet().iterator();
        boolean hasDocs = tool.hasDocumentation();
        int docCount = suppFiles.size() + (hasDocs ? 1 : 0);

        final String curToolOwners = StringUtils.join(SkylineToolsStoreController.getToolOwners(tool), ", ");
        toolOwners.put(tool.getRowId(), curToolOwners);
        final boolean toolEditor = admin || tool.lookupContainer().hasPermission(getUser(), UpdatePermission.class);
        final SkylineTool[] allVersions = SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier());
        final boolean multipleVersions = allVersions.length > 1;
        final int numDownloads = Arrays.stream(allVersions).mapToInt(SkylineTool::getDownloads).sum();
%>

<table id="<%= h(tableId) %>" class="tablewrap"
       data-toolId="<%= tool.getRowId() %>" data-toolName="<%= h(tool.getName()) %>" data-toolVersion="<%= h(tool.getVersion()) %>" data-toolLsid="<%= h(tool.getIdentifier()) %>"
       data-toolDownloads="<%= numDownloads %>"
       <%-- One dialog serves every row, so the delete URL rides on the row. It names this tool's
            own folder, which is the folder DeleteLatestAction removes. --%>
       data-deleteLatestUrl="<%= h(SkylineToolStoreUrls.getDeleteLatestUrl(tool)) %>">
    <tr>
        <td class="leftfill"></td>
        <td class="contentleft">
            <a href="<%= h(detailsUrl) %>"><img src="<%= h(tool.getIconUrl()) %>" class="icon" alt="<%= h(tool.getName()) %>"></a>
        </td>
        <td class="contentright">
            <div class="contentcontainer">
                <span class="title"><a href="<%=h(detailsUrl)%>"><%= h(tool.getName()) %></a></span>
<% if (toolEditor) { %>
                <div class="menuMouseArea sprocket" alt="<%= h(tool.getName()) %>">
                    <img src="<%= h(imgDir) %>gear.png" title="Settings" />
                    <ul class="dropMenu">
                        <li><%=simpleLink("Upload new version").onClick(
                                "$('#uploadForm').attr('action', " + q(SkylineToolStoreUrls.getUpdateToolUrl(tool)) + "); " +
                                "$('#uploadPopOwners').hide(); $('#uploadFormToolId').val(" + tool.getRowId() + "); $('#uploadPop').dialog('open')")%></li>
                        <li><%=simpleLink("Upload supplementary file").onClick(
                                "$('#uploadSuppForm').attr('action', " +
                                q(SkylineToolStoreUrls.getInsertSupplementUrl(tool)) + "); " +
                                "$('#suppFormToolId').val(" + tool.getRowId() + "); $('#uploadSuppPop').dialog('open')")%></li>
<% if (multipleVersions) { %>
                        <li><%=simpleLink("Delete latest version").onClick("delToolLatest($(this))")%></li>
<% } %>
<% if (admin) { %>
                        <li><%=simpleLink("Delete tool from store").onClick("delToolAll($(this))")%></li>
                        <li><%=simpleLink("Manage tool owners").onClick("popToolOwners(" + tool.getRowId() + ")")%></li>
<% } %>
                    </ul>
                </div>
<% } %>
                <p class="toolSubtitle">Version: <%= h(tool.getVersion()) %> | Downloads: <%= h(numDownloads) %></p>
<% if (tool.getOrganization() != null) { %>
                <p class="toolSubtitle"><%= h(tool.getOrganization()) %></p>
<% } %>
<% if (tool.getProvider() != null) { %>
                <p class="toolSubtitle"><a href="<%= h(tool.getProvider()) %>" target="_blank"><%= h(tool.getProvider()) %></a></p>
<% } %>
                <p class="content"><%= h(tool.getDescription(), true) %><br />[<a href="<%=h(detailsUrl)%>">Tool Details</a>, <a href="/labkey/home/software/Skyline/tools/Support/<%=h(tool.getName())%>/project-begin.view" target="_blank">Support Board</a>]</p>

                <div class="toolButtons">

                    <%=link(unsafe("Download<span class=\"visually-hidden\">&nbsp;" + h(tool.getName()) + "</span>")).href(urlFor(SkylineToolsStoreController.DownloadToolAction.class).addParameter("id", tool.getRowId()).toString()).clearClasses().addClass("styled-button")%>
<%
    if (docCount == 1 && hasDocs) {
%>
                        <%=link(unsafe("Documentation<span class=\"visually-hidden\">&nbsp;" + h(tool.getName()) + "</span>")).href(tool.getDocsUrl()).clearClasses().addClass("styled-button").target("_blank").rel("noopener noreferrer")%>
<%
    } else if (docCount == 1) {
        Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
                        <%=link(unsafe("Documentation<span class=\"visually-hidden\">&nbsp;" + h(tool.getName()) + "</span>")).href(suppPair.getKey().toString()).clearClasses().addClass("styled-button")%>
<% } else if (docCount > 1) { %>
                        <div class="menuMouseArea">
                            <button type="button" class="styled-button">Documentation<span class="visually-hidden"><%=h(tool.getName())%></span></button>
                            <ul class="dropMenu">
<% if (hasDocs) { %>
                                <li><a href="<%=h(tool.getDocsUrl())%>" target="_blank" rel="noopener noreferrer"><img class="menuIconImg" src="<%= h(imgDir) %>link.png" alt="Documentation">Online Documentation</a></li>
<% } %>
<%
        while (suppIter.hasNext()) {
            Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
                                <li><a href="<%=h(suppPair.getKey())%>"><img class="menuIconImg" src="<%=h(suppPair.getValue())%>" alt="Supplementary file"><%= h(new File(suppPair.getKey().toString()).getName()) %></a></li>
<% } %>
                            </ul>
                        </div>
<% } %>
                </div>
            </div>
        </td>
    </tr>
</table>
<% } %>
</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var READ_MORE_TEXT = "Read more";
    var READ_LESS_TEXT = "Close";
    var BASE_SLIDE_TIME = 100;
    var LINE_THRESHOLD = 2.0;

    function adjustContent(element) {
        var newP = $("<p />").html($("<a />").text(READ_MORE_TEXT).click(function() {
            var content = $(this).parent().prev();
            var smallHeight = content.data("smallheight");
            var fullHeight = content.data("fullheight");
            var expand = content.height() < fullHeight;
            var slideTime = fullHeight - smallHeight + BASE_SLIDE_TIME;
            content.animate(
                {height: expand ? fullHeight : smallHeight},
                {queue: false, duration: slideTime}
            );
            $(this).fadeOut({
                queue: false, duration: slideTime / 2, always: function() {
                    $(this).text(expand ? READ_LESS_TEXT : READ_MORE_TEXT);
                    $(this).fadeIn({queue: false, duration: slideTime / 2});
                }}
            );
        }));

        var lineThresholdHeight = parseInt(element.css("line-height", "120%").css("line-height")) * LINE_THRESHOLD + 1;
        if (element.height() > lineThresholdHeight) {
            element.after(newP)
                   .data("fullheight", element.height()).data("smallheight", lineThresholdHeight)
                   .css("overflow", "hidden").height(lineThresholdHeight);
        }
    }

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

    $(function() {
        $(".content").each(function() {adjustContent($(this));});
    });

<% if (admin) { %>
    var toolOwners = new Array();
<% for (SkylineTool tool : tools) { %>
    toolOwners[<%= h(tool.getRowId()) %>] = "<%= h(toolOwners.get(tool.getRowId())) %>";
<%
        }
        SafeToRender users = SkylineToolsStoreController.getUsersForAutocomplete();
%>
    $(".toolOwners").each(function() {autocomplete($(this), <%=users%>);});

    function popToolOwners(id) {
        $('#ownersFormToolId').val(id);
        $('#manageOwnersPop').dialog('open');
        var ownersTxt = $("#toolOwnersManage");
        ownersTxt.focus();
        ownersTxt.val(toolOwners[id]);
        if (ownersTxt.val())
            ownersTxt.val(ownersTxt.val() + ", ");
    }
<% } %>

    function delToolAll(sender) {
        var parentTable = sender.parents("table:first");
        $("#delToolAllDlg").data("toolTable", parentTable)
                           .html("<p>Completely delete " + parentTable.attr("data-toolName") + "?</p>")
                           .dialog("open");
    }

    function delToolLatest(sender) {
        var parentTable = sender.parents("table:first");
        $("#delToolLatestDlg").data("toolTable", parentTable)
                              .html("<p>Delete version " + parentTable.attr("data-toolVersion") + " of " + parentTable.attr("data-toolName") + "?</p>")
                              .dialog("open");
    }

    function extractToolTable(data, lsid) {
        var parsedData = $.parseHTML(data);
        return $(parsedData).find('.tablewrap[data-toolLsid="' + lsid + '"]:first');
    }

    function showDeleteLatestError(toolTable) {
        $("#delToolLatestDlg").empty().append($("<p></p>").text(
                "An error occurred trying to delete the latest version of " +
                toolTable.attr("data-toolName") + "."));
        $(".ui-dialog-buttonpane button:contains('Ok')").button().hide();
        setButtonsEnabled(true);
    }

    var DLG_EFFECT_SHOW = "fade";
    var DLG_EFFECT_HIDE = "fade";

    $("#uploadPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#manageOwnersPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#uploadSuppPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});

    $("#delToolAllDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                $(this).html("<p>Please wait...</p>");
                var toolTable = $(this).data("toolTable");
                $.post("<%=h(urlFor(SkylineToolsStoreController.DeleteAction.class))%>", {
                    "toolId": toolTable.attr("data-toolId"),
                    "X-LABKEY-CSRF": LABKEY.CSRF
                }).done(function() {
                    $("#delToolAllDlg").dialog("close");
                    toolTable.hide("explode");
                }).fail(function() {
                    $("#delToolAllDlg").html("<p>An error occurred trying to delete " + toolTable.attr("data-toolName") + ".</p>");
                    $(".ui-dialog-buttonpane button:contains('Ok')").button().hide();
                    setButtonsEnabled(true);
                });
            },
            Cancel: function() {$(this).dialog("close");}
        },
        close: function() {
            $(".ui-dialog-buttonpane button:contains('Ok')").button().show();
            setButtonsEnabled(true);
        }
    });

    $("#delToolLatestDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                $(this).html("<p>Please wait...</p>");
                var toolTable = $(this).data("toolTable");
                $.post(toolTable.attr("data-deleteLatestUrl"), {
                    "toolId": toolTable.attr("data-toolId"),
                    "X-LABKEY-CSRF": LABKEY.CSRF
                }).done(function(data) {
                    var newToolTable = extractToolTable(data, toolTable.attr("data-toolLsid"));
                    // A rejected delete comes back as an error view with status 200, so .fail()
                    // does not run and the tool's row is absent from the response. Without this
                    // the row would be replaced by nothing and the tool would appear deleted.
                    if (newToolTable.length === 0) {
                        showDeleteLatestError(toolTable);
                        return;
                    }
                    newToolTable.hide();
                    newToolTable.find(".menuMouseArea").each(function() {initMenu($(this));});
                    $("#delToolLatestDlg").dialog("close");
                    toolTable.hide("explode", function() {
                        $(this).replaceWith(newToolTable);
                        $(newToolTable).show("explode", function() {
                            adjustContent($(newToolTable).find(".content:first"));
                        });
                    });
                }).fail(function() {
                    showDeleteLatestError(toolTable);
                });
            },
            Cancel: function() {$(this).dialog("close");}
        },
        close: function() {
            $(".ui-dialog-buttonpane button:contains('Ok')").button().show();
            setButtonsEnabled(true);
        }
    });

    const sortTools = function(attr, desc) {
        let all = Array.from(document.querySelectorAll("#all-tools table"));
        all.sort((a, b) => {
            let aAttr = a.getAttribute(attr).toLowerCase();
            let bAttr = b.getAttribute(attr).toLowerCase();
            if (/^\d+$/.test(aAttr) && /^\d+$/.test(bAttr)) {
                aAttr = parseInt(aAttr);
                bAttr = parseInt(bAttr);
            }
            let result = 0;
            if (aAttr !== bAttr) {
                result = aAttr < bAttr ? -1 : 1;
            }
            return desc ? -result : result;
        });
        all.forEach(el => document.querySelector("#all-tools").appendChild(el));
    };
    const sortSelector = document.getElementById("sort-selector");
    sortSelector.onchange = function() {
        switch (this.value) {
            case "name-asc": sortTools("data-toolName", false); break;
            case "name-desc": sortTools("data-toolName", true); break;
            case "downloads-asc": sortTools("data-toolDownloads", false); break;
            case "downloads-desc": sortTools("data-toolDownloads", true); break;
        }
    };
    $(sortSelector).val("name-asc").change();

    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
