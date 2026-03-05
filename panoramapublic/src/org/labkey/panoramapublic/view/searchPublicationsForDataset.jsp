<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.SearchPublicationsForDatasetBean" %>
<%@ page import="org.labkey.panoramapublic.ncbi.PublicationMatch" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<SearchPublicationsForDatasetBean> view = HttpView.currentView();
    SearchPublicationsForDatasetBean bean = view.getModelBean();
    ExperimentAnnotations experiment = bean.getExperimentAnnotations();
    ShortURLRecord shortUrl = experiment.getShortUrl();
    String submitterName = experiment.getSubmitterName();
    ActionURL postUrl = new ActionURL(PanoramaPublicController.NotifySubmitterOfPublicationsAction.class, getContainer());
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    LABKEY.Utils.onReady(function() {
        // When a publication radio button is selected, copy its data-publicationtype and
        // data-matchinfo attributes into the hidden form fields so they are submitted with the form.
        const form = document.getElementById('notify-submitter-form');
        if (!form) return;
        form.querySelectorAll('input[name="publicationId"]').forEach(function(radio) {
            radio.addEventListener('change', function() {
                form.querySelector('input[name="publicationType"]').value = this.dataset.publicationtype;
                form.querySelector('input[name="matchInfo"]').value = this.dataset.matchinfo;
            });
        });
    });
</script>

<labkey:errors/>

<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Title:</td>
        <td>
            <% if (shortUrl != null) { %>
                <a href="<%=h(shortUrl.renderShortURL())%>"><%=h(experiment.getTitle())%></a>
            <% } else { %>
                <%=h(experiment.getTitle())%>
            <% } %>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Created:</td>
        <td><%=h(DateUtil.formatDateTime(experiment.getCreated(), "yyyy-MM-dd"))%></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Submitter:</td>
        <td><%=h(submitterName != null ? submitterName : "Unknown")%></td>
    </tr>
    <% if (experiment.hasPxid()) { %>
    <tr>
        <td class="labkey-form-label">PX ID:</td>
        <td><%=simpleLink(experiment.getPxid(), ProteomeXchangeService.toUrl(experiment.getPxid()))%></td>
    </tr>
    <% } %>
    <% if (experiment.hasDoi()) { %>
    <tr>
        <td class="labkey-form-label">DOI:</td>
        <td><%=h(experiment.getDoi())%></td>
    </tr>
    <% } %>
</table>

<% if (!bean.getMatches().isEmpty()) { %>

<labkey:form id="notify-submitter-form" method="POST" action="<%=postUrl%>">
    <input type="hidden" name="id" value="<%=experiment.getId()%>" />
    <input type="hidden" name="publicationType" value="" />
    <input type="hidden" name="matchInfo" value="" />

    <table class="labkey-data-region labkey-show-borders table-bordered table-condensed">
        <tr>
            <th class="labkey-col-header">Select</th>
            <th class="labkey-col-header">Publication</th>
            <th class="labkey-col-header">Matches</th>
            <% if (bean.isShowDismissedColumn()) { %>
            <th class="labkey-col-header">User Dismissed</th>
            <% } %>
        </tr>
        <%
            int rowIdx = 0;
            for (PublicationMatch match : bean.getMatches())
            {
                String rowCls = (rowIdx++) % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                String pubId = match.getPublicationId();
        %>
        <tr class="<%=h(rowCls)%>">
            <td>
                <input type="radio" name="publicationId" value="<%=h(pubId)%>"
                       data-publicationtype="<%=h(match.getPublicationType().name())%>"
                       data-matchinfo="<%=h(match.getMatchInfo())%>" />
            </td>
            <td>
                <%=simpleLink(match.getCitation() != null ? match.getCitation() : match.getPublicationIdLabel(), match.getPublicationUrl()).target("_blank")%>
            </td>
            <td><%=h(match.getMatchInfo())%></td>
            <% if (bean.isShowDismissedColumn()) { %>
            <td><%=h(pubId.equals(bean.getDismissedPubId()) ? "Yes" : "")%></td>
            <% } %>
        </tr>
        <% } %>
    </table>
    <br/>
    <%=button("Notify Submitter").submit(true)%>
</labkey:form>

<% } else { %>

<div style="margin-top:10px;">No publications found for this dataset.</div>

<% } %>
