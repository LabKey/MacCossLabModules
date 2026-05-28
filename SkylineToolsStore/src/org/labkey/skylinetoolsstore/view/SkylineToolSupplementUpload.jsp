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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Object errorAttribute = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "form");
    if (errorAttribute != null)
    {
%><p class="labkey-error"><%= h(errorAttribute.toString()) %></p><%
    }

    final String suppTarget = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "supptarget");
%>

<form action="<%= h(urlFor(SkylineToolsStoreController.InsertSupplementAction.class)) %>" enctype="multipart/form-data" method="post">
    <p>
        Browse to the supplementary file you would like to upload.<br/><br/>
        <input type="file" size="50" name="suppFile" /><br /><br />
        <input type="hidden" name="supptarget" value="<%= h(suppTarget) %>" />
        <input type="submit" value="Upload Supplementary File" />
    </p>
</form>

<br />
<%= PageFlowUtil.generateBackButton() %>
