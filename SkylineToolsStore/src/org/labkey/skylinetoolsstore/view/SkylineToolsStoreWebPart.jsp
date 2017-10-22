<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.skylinetoolsstore.RatingManager" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreManager" %>
<%@ page import="org.labkey.skylinetoolsstore.model.Rating" %>
<%@ page import="org.labkey.skylinetoolsstore.model.SkylineTool" %>
<%@ page import="org.labkey.skylinetoolsstore.view.SkylineToolStoreUrls" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<?> me = (JspView<?>)HttpView.currentView();
    List<SkylineTool> tools = (List<SkylineTool>)me.getModelBean();

    final boolean admin = getUser().isSiteAdmin();
    final boolean loggedIn = !getUser().isGuest();

    final String contextPath = AppProps.getInstance().getContextPath();
    final String cssDir = contextPath + "/skylinetoolsstore/css/";
    final String imgDir = contextPath + "/skylinetoolsstore/img/";
    final String jsDir = contextPath + "/skylinetoolsstore/js/";

    HashMap<Integer, Integer> toolRatings = new HashMap();
    HashMap<Integer, Integer[]> toolRatingSplit = new HashMap();
    Rating[] allRatings = RatingManager.get().getRatings(null);
    if (allRatings != null)
    {
        for (Rating rating : allRatings)
        {
            int toolId = rating.getToolId();
            int value = rating.getRating();
            toolRatings.put(toolId, (toolRatings.containsKey(toolId)) ?
                toolRatings.get(rating.getToolId()) + value : value);

            if (toolRatingSplit.containsKey(toolId))
                ++toolRatingSplit.get(toolId)[value - 1];
            else
            {
                Integer ratingBreakDown[] = new Integer[] {0, 0, 0, 0, 0};
                ++ratingBreakDown[value - 1];
                toolRatingSplit.put(toolId, ratingBreakDown);
            }
        }
    }
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
    .rating {margin: 0 !important; padding: 0; width: 74px; height: 15px; overflow: hidden;}
    .rating div {position: absolute;}
    .ratingstars {width: 74px !important; overflow:hidden; height: 15px; padding:0 !important; margin:0 !important; }
    .ratingfull {height: 100%; background: url('<%= h(imgDir) %>star_full15x15.png') repeat-x;}
    .ratingempty {width: 100%; height: 100%; background: url('<%= h(imgDir) %>star_empty15x15.png') repeat-x; padding:0 !important; margin:0 !important;}
    .ratingcontent {width: 100%; top: 20px; padding-left:10px; padding-bottom:0px;}
    .toolSubtitle {font-size: 14px; margin:0; padding:0; clear: both;}
    .contentleft {width: 128px; vertical-align: top;}
    .contentright {vertical-align: top;}
    .contentcontainer {margin:0; padding:0;}
    .content {margin: 8px 12px 0 0; padding:0; text-align:justify;}
    .toolButtons {margin-top: 12px;}
    .styled-button{
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
    .toolOwners {width: 80%; min-width: 300px;}
    .ui-menu {width:240px;}
    .dropMenu {position: absolute;}
    .menuMouseArea {display: inline;}
    .sprocket {cursor: pointer; float: right;}
    .menuIconImg {width: 16px; height: 16px;}
    .noCloseDlg .ui-dialog-titlebar-close {display: none;}
    .rating {visibility: hidden;}
    .ratingbox
    {
        width:150px;
        height:15px;
        margin-top:-15px;
        background-color:#EBEBEB;
        display:block;
        margin-left:60px;
    }
    .ratingboxover
    {
        height:15px;
        background-color:#5B74A8;
        display:block;
    }
    .averagerating
    {
        top:-32px;
        left:90px;
        width:100%;
    }
    .ratingfooter
    {
        position:absolute;
    }
    .ratingfooter p, a
    {
        padding-bottom: 0 !important;
        margin: 7px 0 0 0 !important;
    }
    #slider
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
    #sliderover
    {
        width:100px;
        height:20px;
        background: url('<%= h(imgDir) %>star_full20x20.png') repeat-x;
        z-index:99;
    }
    .ui-slider-handle { display:none; }

</style>

<%
    if (admin) {
%>
<button type="button" onclick="$('#uploadPopOwners').show(); $('#updatetarget').val(''); $('#uploadPop').dialog('open')" class="styled-button">Add New Tool</button>
<%
    }
%>
<!--Submit Rating Form-->
<div id="reviewPop" title="Leave a review" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.SubmitRatingAction.class) %>" method="post">
        Title: <input type="text" name="title"><br>
        <input type="text" name="value" style="display:none;" value="5">
        <div id="slider">
            <div id="sliderover"></div>
        </div>
        <input type="hidden" id="ratingToolId" name="toolId" value="" />
        <textarea name="review" rows="6" cols="60"></textarea><br /><br />
        <input type="submit" value="Submit Review" />
    </form>
</div>
<!--Manage Tool Owners Form-->
<div id="manageOwnersPop" title="Manage tool owners" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.SetOwnersAction.class) %>" method="post">
        <p>
            <label for="toolOwnersManage">Tool owners </label><br />
            <input type="text" id="toolOwnersManage" class="toolOwners" name="toolOwners" /><br /><br />
            <input type="hidden" name="sender" value="<%= h(request.getRequestURL()) %>" />
            <input type="hidden" id="updatetargetOwners" name="updatetarget" value="" />
            <input type="submit" value="Update Tool Owners" />
        </p>
    </form>
</div>
<!--Add Tool / Upload New Version Form-->
<div id="uploadPop" title="Upload tool zip file" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.InsertAction.class) %>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the zip file containing the tool you would like to upload.<br/><br />
            <input type="file" name="toolZip" /><br /><br />
            <span id="uploadPopOwners">
                <label for="toolOwnersNew">Tool owners </label><br />
                <input type="text" id="toolOwnersNew" class="toolOwners" name="toolOwners" /><br /><br /><br />
            </span>
            <input type="hidden" name="sender" value="<%= h(request.getRequestURL()) %>" />
            <input type="hidden" id="updatetarget" name="updatetarget" value="" />
            <input type="submit" value="Upload Tool" />
        </p>
    </form>
</div>
<!--Upload Supplementary File Form-->
<div id="uploadSuppPop" title="Upload supplementary file" style="display:none;">
    <form action="<%= urlFor(SkylineToolsStoreController.InsertSupplementAction.class) %>" enctype="multipart/form-data" method="post">
        <p>
            Browse to the supplementary file you would like to upload.<br/><br/>
            <input type="file" name="suppFile" /><br /><br />
            <input type="hidden" id="supptarget" name="supptarget" value="" />
            <input type="submit" value="Upload Supplementary File" />
        </p>
    </form>
</div>
<!-- Delete Tool Dialog -->
<div id="delToolAllDlg" title="Delete" style="display:none;"></div>
<!-- Delete Tool Latest Version Dialog -->
<div id="delToolLatestDlg" title="Delete latest version" style="display:none;"></div>
<%
    HashMap<Integer, String> toolOwners = new HashMap<>();
    for (SkylineTool tool : tools)
    {
        final String tableId = "table-" + tool.getName().replaceAll("[^A-Za-z0-9]", "");
        final ActionURL detailsUrl = SkylineToolStoreUrls.getToolDetailsUrl(tool);

        // Get supporting files in map <url, icon url>
        HashMap<String, String> suppFiles = SkylineToolsStoreController.getSupplementaryFiles(tool);
        Iterator suppIter = suppFiles.entrySet().iterator();

        final String curToolOwners = StringUtils.join(SkylineToolsStoreController.getToolOwners(tool), ", ");
        toolOwners.put(tool.getRowId(), curToolOwners);
        final boolean toolEditor = admin || tool.lookupContainer().hasPermission(getUser(), UpdatePermission.class);
        final boolean multipleVersions = SkylineToolsStoreManager.get().getToolsByIdentifier(tool.getIdentifier()).length > 1;
        final Rating[] ratings = RatingManager.get().getRatingsByToolAllVersions(tool.getIdentifier());
        final Rating[] ratingsCurVer = RatingManager.get().getRatingsByToolId(tool.getRowId());
        final boolean leftReview = RatingManager.get().userLeftRating(tool.getIdentifier(), getUser());
%>
<table id="<%= h(tableId) %>" class="tablewrap" data-toolId="<%= h(tool.getRowId()) %>" data-toolName="<%= h(tool.getName()) %>"  data-toolVersion="<%= h(tool.getVersion()) %>" data-toolLsid="<%= h(tool.getIdentifier()) %>">
    <tr>
        <td class="leftfill"></td>
        <td class="contentleft">
            <a href="<%= h(detailsUrl) %>"><img src="<%= h(tool.getIconUrl()) %>" class="icon" alt="<%= h(tool.getName()) %>"></a>
        </td>
        <td class="contentright">
            <div class="contentcontainer">
                <span class="title"><a href="<%= detailsUrl %>"><%= h(tool.getName()) %></a></span>
<% if (toolEditor) { %>
                <div class="menuMouseArea sprocket" alt="<%= h(tool.getName()) %>">
                    <img src="<%= h(imgDir) %>gear.png" title="Settings" />
                    <ul class="dropMenu">
                        <li><a onclick="$('#uploadPopOwners').hide(); $('#updatetarget').val(<%= h(tool.getRowId()) %>); $('#uploadPop').dialog('open')">Upload new version</a></li>
                        <li><a onclick="$('#supptarget').val(<%= h(tool.getRowId()) %>); $('#uploadSuppPop').dialog('open')">Upload supplementary file</a></li>
<% if (multipleVersions) { %>
                        <li><a onclick="delToolLatest($(this))">Delete latest version</a></li>
<% } %>
<% if (admin) { %>
                        <li><a onclick="delToolAll($(this))">Delete</a></li>
                        <li><a onclick="popToolOwners(<%= h(tool.getRowId()) %>)">Manage tool owners</a></li>
<% } %>
                    </ul>
                </div>
<% } %>
                <p class="toolSubtitle">Version <%= h(tool.getVersion()) %></p>
<% if (tool.getOrganization() != null) { %>
                <p class="toolSubtitle"><%= h(tool.getOrganization()) %></p>
<% } %>
<% if (tool.getProvider() != null) { %>
                <p class="toolSubtitle"><a href="<%= h(tool.getProvider()) %>" target="_blank"><%= h(tool.getProvider()) %></a></p>
<% } %>
<%
    if (ratings != null && ratings.length > 0)
    {
        int totalReviews = ratings.length;
        List<Integer> ratingValues = new ArrayList<>();
        double averageRating = 0;
        double averageRatingRounded = 0;
        double percentOfTotal[] = new double[]{0,0,0,0,0};
        int ratingsBreakDown[] = new int[]{0,0,0,0,0};
        List<Integer> usedIds = new ArrayList<>();

        for (Rating getRatings: ratings) {
            if (toolRatings.containsKey(getRatings.getToolId()))
                ratingValues.add(getRatings.getRating());

            if (toolRatingSplit.containsKey(getRatings.getToolId()))
            {
                if (!usedIds.contains(getRatings.getToolId()))
                {
                    for (int j = 0; j < 5; j++)
                        ratingsBreakDown[j] = ratingsBreakDown[j] + toolRatingSplit.get(getRatings.getToolId())[j];
                    usedIds.add(getRatings.getToolId());
                }
            }
        }

        for (int j = 0; j < 5; j++)
            percentOfTotal[j]= ((double)ratingsBreakDown[j]/totalReviews) * 100;

        for (int i = 0; i < ratingValues.size(); i++)
        {
            averageRating = averageRating + ratingValues.get(i);
            if (i == ratingValues.size() - 1)
            {
                averageRating = averageRating / totalReviews;
                averageRatingRounded =   Math.round(averageRating * 100.0) / 100.0;
                break;
            }
        }
%>
                <div class="rating">
                    <div class="ratingstars">
                        <div class="ratingempty"></div>
                        <%--98% because of css properties inherited.  Actual ratingfull size is not exactly 75px--%>
                        <div class="ratingfull" style="width:<%= averageRating / 5 * 100 %>%;"></div>
                    </div>
                    <div class="ratingcontent">
                        <div class="averagerating"><p><%= averageRatingRounded %> out of 5 stars</p></div>
                        5 stars:<div id="rating-5-<%= h(tool.getRowId()) %>" class="ratingbox"><div class="ratingboxover" style="width:<%=h(percentOfTotal[4])%>%;"></div><div style="margin-left:153px !important;"><%= h(ratingsBreakDown[4]) %></div></div><br>
                        4 stars:<div id="rating-5-<%= h(tool.getRowId()) %>" class="ratingbox"><div class="ratingboxover" style="width:<%=h(percentOfTotal[3])%>%;"></div><div style="margin-left:153px !important;"><%= h(ratingsBreakDown[3]) %></div></div><br>
                        3 stars:<div id="rating-5-<%= h(tool.getRowId()) %>" class="ratingbox"><div class="ratingboxover" style="width:<%=h(percentOfTotal[2])%>%;"></div><div style="margin-left:153px !important;"><%= h(ratingsBreakDown[2]) %></div></div><br>
                        2 stars:<div id="rating-5-<%= h(tool.getRowId()) %>" class="ratingbox"><div class="ratingboxover" style="width:<%=h(percentOfTotal[1])%>%;"></div><div style="margin-left:153px !important;"><%= h(ratingsBreakDown[1]) %></div></div><br>
                        1 stars:<div id="rating-5-<%= h(tool.getRowId()) %>" class="ratingbox"><div class="ratingboxover" style="width:<%=h(percentOfTotal[0])%>%;"></div><div style="margin-left:153px !important;"><%= h(ratingsBreakDown[0]) %></div></div><br>
                        <div class="ratingfooter">
                            <p>
                                <a href="<%= h(detailsUrl) %>">See all <%= totalReviews %> reviews</a>
<% if (loggedIn && !leftReview) { %>
                                / <a onclick="$('#ratingToolId').val(<%= h(tool.getRowId()) %>); $('#reviewPop').dialog('open')">Leave review</a>
<% } %>
                            </p>
                        </div>
                    </div>
                </div>
<% } %>
                <p class="content"><%= h(tool.getDescription(), true) %><br />[<a href="<%=h(detailsUrl)%>">Tool Details</a>, <a href="/labkey/project/home/software/Skyline/tools/Support/<%=h(tool.getName())%>/begin.view?" target="_blank">Support Board</a>]</p>

                <div class="toolButtons">

                    <button type="button" onclick="window.location.href = '<%= urlFor(SkylineToolsStoreController.DownloadToolAction.class).addParameter("id", tool.getRowId()) %>'" class="styled-button">Download</button>
<% if ((ratingsCurVer == null || ratingsCurVer.length == 0) && loggedIn) { %>
                    <%--<button type="button" onclick="$('#ratingToolId').val(<%= h(tool.getRowId()) %>); $('#reviewPop').dialog('open')" class="styled-button">Leave the first Review!</button>--%>
<%
    }
    if (suppFiles.size() == 1) {
        Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
                        <a href="<%= suppPair.getKey() %>"><button type="button" class="styled-button">Documentation</button></a>
<% } else if (suppFiles.size() > 1) { %>
                        <div class="menuMouseArea">
                            <button type="button" class="styled-button">Documentation</button>
                            <ul class="dropMenu">
<%
        while (suppIter.hasNext()) {
            Map.Entry suppPair = (Map.Entry)suppIter.next();
%>
                                <li><a href="<%= suppPair.getKey() %>"><img class="menuIconImg" src="<%= suppPair.getValue() %>" alt="Supplementary file"><%= h(new File(suppPair.getKey().toString()).getName()) %></a></li>
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

<link rel="stylesheet" type="text/css" href="<%= h(cssDir) %>jquery-ui.css">
<script type="text/javascript" src="<%= h(jsDir) %>functions.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.10.3/themes/smoothness/jquery-ui.css">

<script>
    var READ_MORE_TEXT = "Read more";
    var READ_LESS_TEXT = "Close";
    var BASE_SLIDE_TIME = 100;
    var LINE_THRESHOLD = 2.0;

    $(function() {
        initRatingSlider($("#slider"), $("#sliderover"), "value");
    });

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

    function ratinghover(){

        $(function() {
            $(".content").each(function() {adjustContent($(this));});

            $(".rating").each(function() {
                var containingTable = $(this).parents(".tablewrap:first");
                containingTable.height(containingTable.height());
                var toShift = $(this).next();
                var oldTop = toShift.position().top;
                $(this).css("visibility", "visible");
                $(this).css("position", "absolute");
                var newTop = toShift.position().top;
                toShift.css("margin-top", ((parseInt(toShift.css("margin-top")) + (oldTop - newTop)) + "px"));
            });
        });
        var REVIEW_EXPAND_TIME = 250;
        var lastExpanded = false;
        $(".rating").mouseenter(function() {
            lastExpanded = true;
            $(this).css("background", "#ffffff").css("box-shadow", "7px 7px 8px #888888");
            $(this).animate(
                    {height: $(this).prop("scrollHeight"), width: "250px", height: "131px"},
                    {queue: false, duration: REVIEW_EXPAND_TIME}
            );
        }).mouseleave(function() {
            lastExpanded = false;
            $(this).animate(
                {height: $(this).children(":first").height(), width: $(this).children(":first").width()},
                {queue: false, duration: REVIEW_EXPAND_TIME, always: function() {
                    if (!lastExpanded)
                        $(this).css("background", "").css("box-shadow", "0px 0px 0px 0px #fff");
                }}
            );
        });
    }

<% if (admin) { %>
    var toolOwners = new Array();
<% for (SkylineTool tool : tools) { %>
    toolOwners[<%= h(tool.getRowId()) %>] = "<%= h(toolOwners.get(tool.getRowId())) %>";
<%
        }
        pageContext.setAttribute("autocompleteUsers", SkylineToolsStoreController.getUsersForAutocomplete());
%>
    $(".toolOwners").each(function() {autocomplete($(this), ${autocompleteUsers});});

    function popToolOwners(id) {
        $('#updatetargetOwners').val(id);
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

    var DLG_EFFECT_SHOW = "fade";
    var DLG_EFFECT_HIDE = "fade";

    $("#uploadPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#manageOwnersPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#reviewPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});
    $("#uploadSuppPop").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE});

    $("#delToolAllDlg").dialog({modal:true, autoOpen:false, create:function(){fixDlg($(this));}, width:'auto', show:DLG_EFFECT_SHOW, hide:DLG_EFFECT_HIDE, dialogClass:"noCloseDlg",
        buttons: {
            Ok: function() {
                setButtonsEnabled(false);
                $(this).html("<p>Please wait...</p>");
                var toolTable = $(this).data("toolTable");
                $.post("<%= urlFor(SkylineToolsStoreController.DeleteAction.class) %>", {
                    "id": toolTable.attr("data-toolId")
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
                $.post("<%= urlFor(SkylineToolsStoreController.DeleteLatestAction.class)%>", {
                    "id": toolTable.attr("data-toolId")
                }).done(function(data) {
                    var newToolTable = extractToolTable(data, toolTable.attr("data-toolLsid"));
                    newToolTable.hide();
                    newToolTable.find(".menuMouseArea").each(function() {initMenu($(this));});
                    newToolTable.find(".rating").each(function() {initMenu($(this));});
                    $("#delToolLatestDlg").dialog("close");
                    toolTable.hide("explode", function() {
                        $(this).replaceWith(newToolTable);
                        $(newToolTable).show("explode", function() {
                            adjustContent($(newToolTable).find(".content:first"));
                           ratinghover();
                        });
                    });
                }).fail(function() {
                    $("#delToolLatestDlg").html("<p>An error occurred trying to delete the latest version of " + toolTable.attr("data-toolName") + ".</p>");
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

    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
    window.onload = ratinghover;
</script>
