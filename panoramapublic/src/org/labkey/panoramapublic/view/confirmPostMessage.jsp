<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PanoramaPublicController.MessageExampleBean> view = (JspView<PanoramaPublicController.MessageExampleBean>) HttpView.currentView();
    var messageExample = view.getModelBean();
    var exampleExpAnnot = messageExample.getExperimentAnnotations();
    var exampleShortUrl = exampleExpAnnot.getShortUrl().renderShortURL();
    var form = messageExample.getForm();
%>
<labkey:errors/>
<div>
    This following message will be posted to the support message threads of the selected experiments.
    The message below is an example for experiment Id <%=exampleExpAnnot.getId()%> (<%=link(exampleShortUrl, exampleShortUrl).clearClasses()%>).
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