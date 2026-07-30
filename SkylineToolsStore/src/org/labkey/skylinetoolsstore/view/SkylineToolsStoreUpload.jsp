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
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
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
    JspView<SkylineToolsStoreController.ToolUploadForm> me =
            (JspView<SkylineToolsStoreController.ToolUploadForm>) HttpView.currentView();
    SkylineToolsStoreController.ToolUploadForm form = me.getModelBean();

    final String contextPath = AppProps.getInstance().getContextPath();
    final String imgDir = contextPath + "/skylinetoolsstore/img/";

    // Shared by both upload actions. A toolId means we are publishing a new version of that tool, so
    // the form posts to the tool's own container. Otherwise it is a brand-new tool in this folder.
    final boolean isNewVersion = form.getToolId() > 0;
    final String sender = form.getSender();

    SafeToRender users = SkylineToolsStoreController.getUsersForAutocomplete();
%>

<labkey:errors/>

<labkey:form action="<%= isNewVersion ? urlFor(SkylineToolsStoreController.UpdateToolAction.class)
                                      : urlFor(SkylineToolsStoreController.InsertToolAction.class) %>"
             enctype="multipart/form-data" method="post">
    <p>
        Browse to the zip file containing the tool you would like to upload.<br/><br/>
        <input type="file" size="50" name="toolZip" /><br /><br />
<% if (sender != null) { %>
        <input type="hidden" name="sender" value="<%= h(sender) %>" />
<% } %>
<% if (isNewVersion) { %>
        <input type="hidden" name="toolId" value="<%= form.getToolId() %>" />
<% } else { %>
        <label for="toolOwners">Tool owners </label><br />
        <input style="width: 400px; max-width: 80%;" type="text" id="toolOwners" name="toolOwners" value="<%= h(StringUtils.trimToEmpty(form.getToolOwners())) %>" /><br /><br />
        <br />
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
