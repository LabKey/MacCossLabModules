<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Object errorAttribute = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "form");
    if (errorAttribute != null)
    {
%><p class="labkey-error"><%= h(errorAttribute.toString()) %></p><%
    }

    final String contextPath = AppProps.getInstance().getContextPath();
    final String cssDir = contextPath + "/skylinetoolsstore/css/";
    final String imgDir = contextPath + "/skylinetoolsstore/img/";
    final String jsDir = contextPath + "/skylinetoolsstore/js/";

    final String sender = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "sender");
    final String updateTarget = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "updatetarget");
    final String toolOwners = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "toolowners");

    final boolean admin = getUser().isSiteAdmin();
    final String autocompleteUsers = admin ? SkylineToolsStoreController.getUsersForAutocomplete() : "\"\"";
    pageContext.setAttribute("autocompleteUsers", autocompleteUsers);
%>

<form action="<%= h(urlFor(SkylineToolsStoreController.InsertAction.class)) %>" enctype="multipart/form-data" method="post">
    <p>
        Browse to the zip file containing the tool you would like to upload.<br/><br/>
        <input type="file" size="50" name="toolZip" /><br /><br />
<% if (sender != null) { %>
        <input type="hidden" name="sender" value="<%= h(sender) %>" />
<% } %>
<% if (updateTarget == null) { %>
        <label for="toolOwners">Tool owners </label><br />
        <input style="width: 400px; max-width: 80%;" type="text" id="toolOwners" name="toolOwners" value="<%= h(toolOwners) %>" /><br /><br />
        <br />
<% } else { %>
        <input type="hidden" name="updatetarget" value="<%= h(updateTarget) %>" />
<% } %>
        <input type="submit" value="Upload Tool" />
    </p>
</form>

<br />
<%= PageFlowUtil.generateBackButton() %>

<link rel="stylesheet" type="text/css" href="<%= h(cssDir) %>jquery-ui.css">
<script type="text/javascript" src="<%= h(jsDir) %>functions.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>

<script>
    autocomplete($("#toolOwners"), ${autocompleteUsers});
    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
