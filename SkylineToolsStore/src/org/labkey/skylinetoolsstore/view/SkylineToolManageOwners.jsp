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
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SkylineToolsStoreController.SetOwnersForm> me =
            (JspView<SkylineToolsStoreController.SetOwnersForm>) HttpView.currentView();
    SkylineToolsStoreController.SetOwnersForm form = me.getModelBean();

    final String contextPath = AppProps.getInstance().getContextPath();
    final String cssDir = contextPath + "/skylinetoolsstore/css/";
    final String imgDir = contextPath + "/skylinetoolsstore/img/";
    final String jsDir = contextPath + "/skylinetoolsstore/js/";

    final String toolOwners = StringUtils.trimToEmpty(form.getToolOwners());
    final String sender = form.getSender();

    final boolean admin = getUser().hasSiteAdminPermission();
    final SafeToRender autocompleteUsers = admin ? SkylineToolsStoreController.getUsersForAutocomplete() : HtmlString.unsafe("\"\"");
    pageContext.setAttribute("autocompleteUsers", autocompleteUsers);
%>

<labkey:errors/>

<labkey:form action="<%= urlFor(SkylineToolsStoreController.SetOwnersAction.class) %>" enctype="multipart/form-data" method="post">
    <p>
        <label for="toolOwners">Tool owners </label><br />
        <input style="width: 400px; max-width: 80%;" type="text" id="toolOwners" name="toolOwners" /><br /><br />
        <br />
<% if (sender != null) { %>
        <input type="hidden" name="sender" value="<%= h(sender) %>" />
<% } %>
        <input type="hidden" name="toolId" value="<%= form.getToolId() %>" />
        <input type="submit" value="Update Tool Owners" />
    </p>
</labkey:form>

<br />
<%= PageFlowUtil.generateBackButton() %>

<link rel="stylesheet" type="text/css" href="<%= h(cssDir) %>jquery-ui.css">
<script type="text/javascript" src="<%= h(jsDir) %>functions.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var ownersTxt = $("#toolOwners");
    ownersTxt.focus();
    ownersTxt.val("<%= h(toolOwners) %>");

    autocomplete(ownersTxt, ${autocompleteUsers});
    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
