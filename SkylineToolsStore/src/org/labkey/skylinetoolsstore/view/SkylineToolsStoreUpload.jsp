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
<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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
    Object errorAttribute = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "form");
    if (errorAttribute != null)
    {
%><p class="labkey-error"><%= h(errorAttribute.toString()) %></p><%
    }

    final String contextPath = AppProps.getInstance().getContextPath();
    final String imgDir = contextPath + "/skylinetoolsstore/img/";

    final String sender = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "sender");
    final String updateTarget = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "updatetarget");
    final String toolOwners = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "toolowners");

    final boolean admin = getUser().hasSiteAdminPermission();
    final SafeToRender autocompleteUsers = admin ? SkylineToolsStoreController.getUsersForAutocomplete() : HtmlString.unsafe("\"\"");
    SafeToRender users = SkylineToolsStoreController.getUsersForAutocomplete();
%>

<labkey:form action="<%= urlFor(SkylineToolsStoreController.InsertAction.class) %>" enctype="multipart/form-data" method="post">
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
</labkey:form>

<br />
<%= PageFlowUtil.generateBackButton() %>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    autocomplete($("#toolOwners"), <%=users%>);
    initJqueryUiImages("<%= h(imgDir + "jquery-ui") %>");
</script>
