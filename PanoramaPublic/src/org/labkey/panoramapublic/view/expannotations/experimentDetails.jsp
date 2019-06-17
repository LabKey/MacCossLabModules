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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.targetedms.model.Journal" %>
<%@ page import="org.labkey.targetedms.model.JournalExperiment" %>
<%@ page import="org.labkey.targetedms.query.JournalManager" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("TargetedMS/js/clipboard.min.js");
        dependencies.add("TargetedMS/js/ExperimentAnnotations.js");
        dependencies.add("TargetedMS/css/ExperimentAnnotations.css");
        dependencies.add("hopscotch/js/hopscotch.min.js");
        dependencies.add("hopscotch/css/hopscotch.min.css");
    }
%>

<%
    JspView<TargetedMSController.ExperimentAnnotationsDetails> me = (JspView<TargetedMSController.ExperimentAnnotationsDetails>) HttpView.currentView();
    TargetedMSController.ExperimentAnnotationsDetails annotDetails = me.getModelBean();
    ExperimentAnnotations annot = annotDetails.getExperimentAnnotations();
    ActionURL editUrl = TargetedMSController.getEditExperimentDetailsURL(getContainer(), annot.getId(),
            TargetedMSController.getViewExperimentDetailsURL(annot.getId(), getContainer()));
    ActionURL deleteUrl = TargetedMSController.getDeleteExperimentURL(getContainer(), annot.getId(), getContainer().getStartURL(getUser()));

    ActionURL publishUrl = PublishTargetedMSExperimentsController.getPrePublishExperimentCheckURL(annot.getId(), getContainer());
    Container experimentContainer = annot.getContainer();
    final boolean canEdit = (!annot.isJournalCopy() || getUser().hasSiteAdminPermission()) && experimentContainer.hasPermission(getUser(), InsertPermission.class);
    // User needs to be the folder admin to publish an experiment.
    final boolean canPublish = annotDetails.isCanPublish();
    final boolean showingFullDetails = annotDetails.isFullDetails();

    ActionURL experimentDetailsUrl = new ActionURL(TargetedMSController.ShowExperimentAnnotationsAction.class, getContainer());
    experimentDetailsUrl.addParameter("id", annot.getId());

    Journal journal = null;
    boolean journalCopyPending = false;
    ShortURLRecord accessUrlRecord = annot.getShortUrl(); // Will have a value if this is a journal copy of an experiment.
    JournalExperiment je = me.getModelBean().getLastPublishedRecord(); // Will be non-null if this experiment is in a user (not journal) project.
    String publishButtonText = "Submit";
    if(je != null)
    {
        journal = JournalManager.getJournal(je.getJournalId());
        journalCopyPending = je.getCopied() == null;
        accessUrlRecord = je.getShortAccessUrl();

        if(!journalCopyPending)
        {
            publishButtonText = "Resubmit";
            publishUrl = PublishTargetedMSExperimentsController.getRePublishExperimentURL(annot.getId(), je.getJournalId(), getContainer());
        }
    }
    String accessUrl = accessUrlRecord == null ? null : accessUrlRecord.renderShortURL();
    String linkText = accessUrl == null ? null : (annot.isJournalCopy() ? "Link" : (journalCopyPending ? "Access link" : journal.getName() + " link"));
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

</style>
<script type="text/javascript">

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
        <a class="button-small button-small-red" style="float:left; margin:0px 5px 0px 2px;" href="<%=h(publishUrl)%>"><%=publishButtonText%></a>
    <%}%>
    <%if(canEdit){%>
    <a style="float:left; margin-top:2px; margin-left:2px;" href="<%=h(editUrl)%>">[Edit]</a>
    <a style="float:left; margin-top:2px; margin-left:2px;" href="<%=h(deleteUrl)%>">[Delete]</a>
    <%}%>
    <%if(!showingFullDetails) {%>
    <a style="float:left; margin-top:2px; margin-left:2px;" href="<%=h(experimentDetailsUrl)%>">[More Details...]</a>
    <%}%>
</div>

<br/>
<% if(!StringUtils.isBlank(accessUrl)) {%>
    <div class="link">
       <strong><%=linkText%>: </strong>
       <span id="accessUrl" style="margin-top:5px;"><a href="<%=h(accessUrl)%>"><%=h(accessUrl)%></a></span>
       <a class="button-small button-small-green" style="margin:0px 5px 0px 2px;" href="" onclick="showShareLink(this, '<%=h(accessUrl)%>'); return false;">Share</a>
    </div>
 <% } %>

<%if(annot.getCitation() != null && annot.getPublicationLink() != null){%>
    <div class="link"><%=h(annot.getCitation())%> <strong><br />[<a href="<%=h(annot.getPublicationLink())%>" target="_blank">Publication</a>]</strong></div>
<%}%>
<%if(annot.getCitation() != null && annot.getPublicationLink() == null){%>
    <div class="link"><%=h(annot.getCitation())%> </div>
<%}%>
<%if(annot.getCitation() == null && annot.getPublicationLink() != null){%>
    <div class="link"><strong><br />[<a href="<%=h(annot.getPublicationLink())%>" target="_blank">Publication</a>]</strong></div>
<%}%>
<%if(annot.getPxid() != null){%>
    <div class="link">
        <strong>ProteomeXchange ID: </strong> <a href="http://proteomecentral.proteomexchange.org/cgi/GetDataset?ID=<%=h(annot.getPxid())%>" target="_blank"><%=h(annot.getPxid())%></a>
    </div>
<%}%>

<%if(getUser().hasSiteAdminPermission()) {
    ActionURL pxActionsUrl = new ActionURL(PublishTargetedMSExperimentsController.GetPxActionsAction.class, getContainer());
    pxActionsUrl.addParameter("id", annot.getId());
%>
<br/><div><%=link("ProteomeXchange Actions", pxActionsUrl)%></div>
<%}%>

<ul>
    <%if(annot.getOrganism() != null){%>
 <li><strong>Organism:</strong> <%=h(annot.getOrganismsNoTaxId())%></li>
    <%}%>
    <%if(annot.getInstrument() != null){%>
 <li><strong>Instrument:</strong> <%=h(annot.getInstrument())%></li>
    <%}%>
    <%if(annot.getSpikeIn() != null){%>
 <li><strong>SpikeIn:</strong>
     <%=annot.getSpikeIn() ? "Yes" : "No"%>
 </li>
    <%}%>
    <%if(annot.getKeywords() != null){%>
    <li><strong>Keywords:</strong>
        <%=h(annot.getKeywords())%>
    </li>
    <%}%>
    <%if(annot.getLabHead() != null){%>
    <li><strong>Lab head:</strong>
        <%=annot.getLabHeadName()%>
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

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script type="text/javascript">

    var SHOW_MORE_TEXT = "[Show more]";
    var SHOW_LESS_TEXT = "[Show less]";
    var MAX_CHARS = 500;

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

</script>