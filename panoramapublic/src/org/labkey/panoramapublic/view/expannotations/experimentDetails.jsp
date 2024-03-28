<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.ExperimentAnnotationsDetails" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.ShowExperimentAnnotationsAction" %>
<%@ page import="org.labkey.panoramapublic.model.DataLicense" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.model.Journal" %>
<%@ page import="org.labkey.panoramapublic.query.JournalManager" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="org.labkey.panoramapublic.model.Submission" %>
<%@ page import="org.labkey.panoramapublic.model.JournalSubmission" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.panoramapublic.query.DataValidationManager" %>
<%@ page import="org.labkey.panoramapublic.model.validation.DataValidation" %>
<%@ page import="org.labkey.panoramapublic.model.validation.PxStatus" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="static org.labkey.api.util.DOM.SPAN" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.style" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.title" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.href" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager" %>
<%@ page import="org.labkey.panoramapublic.view.publish.CatalogEntryWebPart" %>
<%@ page import="org.labkey.panoramapublic.model.CatalogEntry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.panoramapublic.proteomexchange.ProteomeXchangeService" %>
<%@ page import="org.labkey.panoramapublic.datacite.DataCiteService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("internal/jQuery");
        dependencies.add("PanoramaPublic/js/clipboard.min.js");
        dependencies.add("PanoramaPublic/js/ExperimentAnnotations.js");
        dependencies.add("PanoramaPublic/css/ExperimentAnnotations.css");
        dependencies.add("hopscotch/js/hopscotch.min.js");
        dependencies.add("hopscotch/css/hopscotch.min.css");
        dependencies.add("PanoramaPublic/css/pxValidation.css");
    }
%>

<%
    JspView<ExperimentAnnotationsDetails> me = (JspView<ExperimentAnnotationsDetails>) HttpView.currentView();
    ExperimentAnnotationsDetails annotDetails = me.getModelBean();
    ExperimentAnnotations annot = annotDetails.getExperimentAnnotations();
    ActionURL editUrl = PanoramaPublicController.getEditExperimentDetailsURL(getContainer(), annot.getId(),
            PanoramaPublicController.getViewExperimentDetailsURL(annot.getId(), getContainer()));
    ActionURL deleteUrl = PanoramaPublicController.getDeleteExperimentURL(getContainer(), annot.getId(), getContainer().getStartURL(getUser()));

    ActionURL publishUrl = PanoramaPublicController.getSubmitExperimentURL(annot.getId(), getContainer());
    // If the user is a folder admin they should be able to edit the experiment metadata. Data submitters are never admins
    // in the Panorama Public copy, so the ability to edit will be limited to site admins and the Panorama Public project admin.
    final boolean canEdit = getContainer().hasPermission(getUser(), AdminPermission.class);
    // User needs to be the folder admin to publish an experiment.
    final boolean canPublish = annotDetails.isCanPublish();
    final boolean showingFullDetails = annotDetails.isFullDetails();

    ActionURL catalogEntryUrl = null;
    String iconCls = "catalogIcon";
    String catalogEntryTooltip = "Panorama Public Catalog Entry";
    if (CatalogEntryWebPart.canBeDisplayed(annot, getUser()))
    {
        CatalogEntry entry = CatalogEntryManager.getEntryForExperiment(annot);
        if (entry != null)
        {
            catalogEntryUrl = PanoramaPublicController.getViewCatalogEntryUrl(annot, entry);
            iconCls += " catalogIconGreen";
        }
        else
        {
            catalogEntryUrl = PanoramaPublicController.getAddCatalogEntryUrl(annot);
            iconCls += " catalogIconGrey";
        }
    }

    HtmlString pxStatusIndicator = null;
    if (canEdit)
    {
        DataValidation validation = DataValidationManager.getLatestValidation(annot.getId(), annot.getContainer());
        if (validation != null && validation.isComplete())
        {
            ActionURL viewExptDetailsUrl = PanoramaPublicController.getViewExperimentDetailsURL(annot.getId(), annot.getContainer());
            StringBuilder status = new StringBuilder();
            if (DataValidationManager.isValidationOutdated(validation, annot, getUser()))
            {
                DOM.A(DOM.at(href, viewExptDetailsUrl.toContainerRelativeURL()),
                        DOM.SPAN(DOM.cl("labkey-error"), SPAN(DOM.at(style, "background-color: #FFF6D8;margin:2px;font-weight:bold;"), "PX validation is outdated")))
                        .appendTo(status);
            }
            else
            {
                PxStatus pxStatus = validation.getStatusIncludingExptMetadata(annot);
                String pxCls = "pxv-circle " +
                        (PxStatus.Complete == pxStatus ? "pxv-circle-valid" :
                        PxStatus.IncompleteMetadata == pxStatus ? "pxv-circle-incomplete" : "pxv-circle-invalid");
                String pxStatusStr = PxStatus.NotValid == pxStatus ? pxStatus.getLabel() :
                        ("ProteomeXchange status: " + pxStatus.getLabel());
                DOM.A(DOM.at(href, viewExptDetailsUrl.toContainerRelativeURL()),
                        DOM.SPAN(DOM.at(title, pxStatusStr), DOM.SPAN(DOM.cl(pxCls), "PX")))
                        .appendTo(status);
            }
            pxStatusIndicator = HtmlString.unsafe(status.toString());
        }
    }

    ActionURL experimentDetailsUrl = urlFor(ShowExperimentAnnotationsAction.class);
    experimentDetailsUrl.addParameter("id", annot.getId());

    Journal journal = null;
    boolean journalCopyPending = false;
    ShortURLRecord accessUrlRecord = annot.getShortUrl(); // Will have a value if this is a journal copy of an experiment.
    JournalSubmission js = annotDetails.getLastSubmittedRecord();
    String publishButtonText = "Submit";
    if (js != null && !annot.isJournalCopy())
    {
        journal = JournalManager.getJournal(js.getJournalId());
        Submission submission = js.getLatestSubmission();
        if (submission != null)
        {
            journalCopyPending = !submission.hasCopy();
            accessUrlRecord = js.getShortAccessUrl();

            if (!journalCopyPending)
            {
                publishButtonText = "Resubmit";
                publishUrl = PanoramaPublicController.getResubmitExperimentURL(annot.getId(), js.getJournalId(), getContainer(), submission.isKeepPrivate(), true); // Has been copied; User is re-submitting
            }
        }
    }
    String labHeadName = annotDetails.getLabHeadName();
    String accessUrl = accessUrlRecord == null ? null : accessUrlRecord.renderShortURL();
    String linkText = accessUrl == null ? null : "Permanent Link";
    DataLicense license = annot.getDataLicense();
%>
<style>
 #title
 {
     font-size: 17px;
     font-weight: bold;
     padding-right:20px;
 }
 #annotationContainer p
 {
     font-size: 14px;
     font-weight: normal;
 }
 .descriptionBox
 {
     margin-top:24px;
     margin-right:15px;

     border-radius: 3px 3px 3px 3px;
     -moz-border-radius: 3px 3px 3px 3px;
     -webkit-border-radius: 3px 3px 3px 3px;
     background-color: #f3f3f3;
     padding:15px 15px 20px 10px;
 }
 .descriptionBox legend
 {
     margin-top:-8px;
     font-size:15px;
     background-color: transparent;
     border:none;
     color:#000;
     font-weight: bold;
 }
 .descriptionBox p
 {
    padding:3px 0px 0px 0px;
     margin:0;
 }
 .link
 {
     font-size:12px;
     font-weight: normal;
     padding-top:10px;
 }
 #annotationContainer ul
 {
     list-style: none;
     padding:2px 0px 0px 3px;
     margin:10px 0px 0px 0px;

 }
 span.moreContent
 {
     display: none;
 }
 a.moreLink
 {
     display: block;
 }
 a.catalogIcon {
     display: inline-block;
     height: 20px;
     width: 25px;
 }
 a.catalogIconGreen
 {
     background: url("<%= h(AppProps.getInstance().getContextPath()) %>/PanoramaPublic/images/slideshow-icon-green.png") no-repeat bottom 0 right 0;
 }
 a.catalogIconGrey
 {
     background: url("<%= h(AppProps.getInstance().getContextPath()) %>/PanoramaPublic/images/slideshow-icon.png") no-repeat bottom 0 right 0;
 }

</style>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    Ext4.onReady(function(){

        var textDiv = Ext4.get("journal_copy_pending_text");
        if(textDiv)
        {
            textDiv.hover(
                    function ()
                    {
                        Ext4.get("journal_copy_pending_details").fadeIn();
                    },
                    function ()
                    {
                        Ext4.get("journal_copy_pending_details").fadeOut()
                    }
            );
        }
    });

</script>
<div id="annotationContainer">

<%if(journalCopyPending && canEdit) { %>
    <div style="color:red; font-weight: bold;font-size:1.1em" id="journal_copy_pending_text">Copy Pending!</div>
    <div style="color:red; visibility:hidden; margin-bottom:5px; font-size:0.85em;" id="journal_copy_pending_details">
        This experiment has not yet been copied. Any changes made to this experiment,
        or the data contained in the folder(s) for this experiment, will also get copied when a copy is made.
    </div>
<% } %>
<div id="title"><%=h(annot.getTitle())%></div>
<div>
    <%if(canPublish && !journalCopyPending){%>
        <span style="float:left; margin:0px 5px 0px 2px;">
            <%=link(publishButtonText, publishUrl).clearClasses().addClass("button-small").addClass("button-small-red")%>
        </span>
    <%}%>
    <% if (annotDetails.canAddPublishLink(getUser())) { %>
        <%=link(annotDetails.getPublishButtonText(), new ActionURL(PanoramaPublicController.MakePublicAction.class,getContainer())
                .addParameter("id", annot.getId()))
                .clearClasses().addClass("button-small button-small-red")
                .style("margin:0px 5px 0px 2px;")%>
    <% } %>
    <%if(canEdit){%>
    <a style="margin-top:2px; margin-left:2px;" href="<%=h(editUrl)%>">[Edit]</a>
    <a style="margin-top:2px; margin-left:2px;" href="<%=h(deleteUrl)%>">[Delete]</a>
    <%}%>
    <%if(!showingFullDetails) {%>
    <a style="margin-top:2px; margin-left:2px;" href="<%=h(experimentDetailsUrl)%>">[More Details...]</a>
    <% if (pxStatusIndicator != null) { %>
    <span><%=pxStatusIndicator%></span>
    <% } %>
    <%}%>
    <% if (catalogEntryUrl != null) { %>
        <%=iconLink(iconCls, catalogEntryTooltip, catalogEntryUrl).style("margin-left:8px;")%>
    <% } %>
</div>

<% if(!StringUtils.isBlank(accessUrl)) {%>
    <div class="link">
       <strong><%=h(linkText)%>: </strong>
       <span id="accessUrl" style="margin-top:5px;"><a href="<%=h(accessUrl)%>"><%=h(accessUrl)%></a></span>
        <%=link("Share").clearClasses().addClass("button-small button-small-green")
                .style("margin:0px 5px 0px 2px")
                .onClick("showShareLink(this, " + q(accessUrl) + "); return false;")%>
        <% if (annotDetails.hasVersion()) {%>
        <span class="link" id="publishedDataVersion" style="margin-left:10px;"><strong>Version: <span style="color:<%=h(annotDetails.isCurrentVersion() ? "green" : "red")%>;"><%=h(annotDetails.getVersion())%></span>
                <% if (annotDetails.hasVersionsLink()) { %>
                   <span><%=link(annotDetails.isCurrentVersion() ? "[All Versions]" : "[Current Version]", annotDetails.getVersionsLink())%></span>
                <% } %>
            </strong> </span>
        <% } %>
    </div>
 <% } %>
<% if(annot.getCitation() != null || annot.getPublicationLink() != null) { %>
    <div class="link">
        <% if (annot.hasCitation()) { %> <%=annot.getHtmlCitation() %> <% } %>
        <% if (annot.getPublicationLink() != null) { %> <div><strong>[<a href="<%=h(annot.getPublicationLink())%>" target="_blank">Publication</a>]</strong></div> <% } %>
    </div>
<% } %>
<div>
    <% boolean addSep = false; %>
    <% if(license != null){%>
    <span class="link">
        <strong>Data License: </strong> <%=license.getDisplayLinkHtml()%>
    </span>
    <% addSep = true; }%>
    <%if(annot.getPxid() != null){%>
    <%if(addSep) {%> <span style="margin-right:10px;margin-left:10px;">|</span> <%}%>
    <span class="link">
        <strong>ProteomeXchange: </strong><%= link(annot.getPxid()).href(ProteomeXchangeService.toUrl(annot.getPxid())).target("_blank").rel("noopener noreferrer").clearClasses() %>
    </span>
    <% addSep = true; }%>
    <%if(annot.hasDoi()){%>
    <%if(addSep) {%> <span style="margin-right:10px;margin-left:10px;">|</span> <%}%>
    <span class="link">
        <strong>doi: </strong><%= DataCiteService.toLink(annot.getDoi()).target("_blank").clearClasses() %>
    </span>
    <%}%>
</div>

<ul>
    <%if(annot.getOrganism() != null){%>
 <li><strong>Organism:</strong> <%=h(annot.getOrganismsNoTaxId())%></li>
    <%}%>
    <%if(annot.getInstrument() != null){%>
 <li><strong>Instrument:</strong> <%=h(annot.getInstrument())%></li>
    <%}%>
    <%if(annot.getSpikeIn() != null){%>
 <li><strong>SpikeIn:</strong>
     <%=h(annot.getSpikeIn() ? "Yes" : "No")%>
 </li>
    <%}%>
    <%if(annot.getKeywords() != null){%>
    <li><strong>Keywords:</strong>
        <%=h(annot.getKeywords())%>
    </li>
    <%}%>
    <%if(annot.getSubmitter() != null || labHeadName != null){%>
    <li>
        <%if(labHeadName != null) { %> <span style="margin-right:6px;"><strong>Lab head:</strong> <%=h(labHeadName)%> </span> <%}%>
        <%if(annot.getSubmitter() != null) { %> <strong>Submitter:</strong> <%=h(annot.getSubmitterName())%>  <%}%>
    </li>
    <%}%>
</ul>
    <%if(annot.getAbstract() != null){%>
<div class="descriptionBox"><legend>Abstract</legend><div class="content"><%=h(annot.getAbstract())%></div></div>
    <%}%>
    <%if(annot.getExperimentDescription() != null){%>
<div class="descriptionBox"><legend>Experiment Description</legend><div class="content"><%=h(annot.getExperimentDescription())%></div> </div>
    <%}%>
    <%if(annot.getSampleDescription() != null){%>
<div class="descriptionBox"><legend>Sample Description</legend><div class="content"><%=h(annot.getSampleDescription())%></div></div>
    <%}%>

<div style="text-align: center; margin-top:15px;">
    <span>Created on <%=h(SimpleDateFormat.getInstance().format(annot.getCreated()))%> </span>
</div>

</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    var SHOW_MORE_TEXT = "[Show more]";
    var SHOW_LESS_TEXT = "[Show less]";
    var MAX_CHARS = 500;

+function($){
    function showLess(element)
    {
        var text = element.html();
        var length = text.length;
        if(length > MAX_CHARS)
        {
            var less = text.substr(0, MAX_CHARS);
            var ellipsis = "...";
            var more = text.substr(MAX_CHARS);

            var html = less + "<span class=\"ellipsis\">" + ellipsis +"</span><span class=\"moreContent\">" + more + "</span>";
            var moreLink = "<a class=\"moreLink\">" + SHOW_MORE_TEXT + "</a>";
            element.html(html + moreLink);
        }
    }

    $(document).ready(function()
    {
        $(".content").each(function() {showLess($(this));});

        $(".moreLink").click(function(event)
        {
            event.preventDefault();
            if($(this).hasClass("less"))
            {
                $(this).removeClass("less");
                $(this).html(SHOW_MORE_TEXT);
            }
            else
            {
                $(this).addClass("less");
                $(this).html(SHOW_LESS_TEXT);
            }
            $(this).prev().prev().toggle();
            $(this).prev().toggle();
        });
    });
}(jQuery);

</script>