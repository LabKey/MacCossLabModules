<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.MessageExampleBean" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<MessageExampleBean> view = HttpView.currentView();
    var messageExample = view.getModelBean();
    var exampleExpAnnot = messageExample.getExperimentAnnotations();
    var exampleShortUrl = exampleExpAnnot.getShortUrl().renderShortURL();
    var form = messageExample.getForm();
%>
<labkey:errors/>
<div>
    This following message will be posted to the support message threads of the selected experiments.
    The message below is an example for experiment Id <%=exampleExpAnnot.getId()%> (<%=simpleLink(exampleShortUrl, exampleShortUrl)%>).
</div>
<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label" style="text-align:center;">Test Mode:</td>
        <td><%=h(form.getTestMode() ? "Yes" : "No  (message will be posted)")%></td>
    </tr>
    <tr>
        <td class="labkey-form-label" style="text-align:center;">Message Title:</td>
        <td><%=h(messageExample.getTitle())%></td>
    </tr>
    <tr>
        <td class="labkey-form-label" style="text-align:center;">Message (Markdown):</td>
        <td><%=messageExample.getMarkdownMessage()%></td>
    </tr>
    <tr>
        <td class="labkey-form-label" style="text-align:center;">Message:</td>
        <td><%=h(messageExample.getMessage())%></td>
    </tr>
</table>
<div style="margin-top:20px;">
    The message will be posted to the support message threads of the following <%=h(StringUtilsLabKey.pluralize(form.getExperiments().size(), "experiment"))%>
    submitted to Panorama Public.
    <table class="table-condensed labkey-data-region table-bordered">
        <thead>
        <tr class="labkey-col-header-row">
            <th class="labkey-column-header">ExperimentId</th>
            <th class="labkey-column-header">Created</th>
            <th class="labkey-column-header">Short URL</th>
            <th class="labkey-column-header">Title</th></tr>
        </thead>
        <% String trClass = "labkey-alternate-row";
           for (ExperimentAnnotations experiment: form.getExperiments()) {
            var shortUrl = experiment.getShortUrl().renderShortURL();
        %>
        <tr class="<%=h(trClass)%>">
            <td><%=h(experiment.getId())%></td>
            <td><%=h(experiment.getCreated())%></td>
            <td><%=link(shortUrl, shortUrl)%></td>
            <td><%=h(experiment.getTitle())%></td>
        </tr>
        <% trClass = "labkey-alternate-row".equals(trClass) ? "labkey-row" : "labkey-alternate-row"; } %>
    </table>
</div>