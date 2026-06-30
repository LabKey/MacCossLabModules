<%
/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Manage LINCS PSP Endpoint Config
</p>
<br>
<%
    LincsController.ClueCredentialsForm form = ((JspView<LincsController.ClueCredentialsForm>) HttpView.currentView()).getModelBean();
 %>

<labkey:errors/>
<labkey:form method="post">
    <table>
        <tr>
            <td>URL:</td>
            <td><input type="text" name="serverUri" value="<%=h(form.getServerUri())%>"></td>
        </tr>
        <tr>
            <td>API Key:</td>
            <td><input type="text" name="apiKey" value="<%=h(form.getApiKey())%>"></td>
        </tr>
    </table>
    <labkey:button text="Save"></labkey:button>
</labkey:form>
