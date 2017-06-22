<%
/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="org.labkey.targetedms.query.JournalManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TargetedMSController.ExperimentAnnotationsDetails> me = (JspView<TargetedMSController.ExperimentAnnotationsDetails>) HttpView.currentView();
    ExperimentAnnotations bean = me.getModelBean().getExperimentAnnotations();
    ActionURL editUrl = TargetedMSController.getEditExperimentDetailsURL(getContainer(), bean.getId(),
            TargetedMSController.getViewExperimentDetailsURL(bean.getId(), getContainer()));
    ActionURL deleteUrl = TargetedMSController.getDeleteExperimentURL(getContainer(), bean.getId(), getContainer().getStartURL(getUser()));

    ActionURL publishUrl = PublishTargetedMSExperimentsController.getPublishExperimentURL(bean.getId(), getContainer());
    Container experimentContainer = bean.getContainer();
    final boolean canEdit = (!bean.isJournalCopy() || getUser().isSiteAdmin()) && experimentContainer.hasPermission(getUser(), InsertPermission.class);
    // User needs to be the folder admin to publish an experiment.
    final boolean canPublish = me.getModelBean().isCanPublish();
    final boolean showingFullDetails = me.getModelBean().isFullDetails();
    boolean journalCopyPending = JournalManager.isCopyPending(bean);

    ActionURL experimentDetailsUrl = new ActionURL(TargetedMSController.ShowExperimentAnnotationsAction.class, getContainer());
    experimentDetailsUrl.addParameter("id", bean.getId());
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
 #citation
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
 a.banner-button-small{
     display: block;
     float:left;
     margin: 15px 0 0 15px;
     padding: 2px 9px 0 9px;
     height: 15px;
     color: #fff;
     border-radius: 5px;
     font-size: 75%;
     font-weight: bold;
     border: 1px solid #6d0019;
     text-shadow: -1px -1px #6d0019;
     box-shadow: 0 2px #ccc;
     text-align: center;
     background: #a90329; /* Old browsers */
     /* IE9 SVG, needs conditional override of 'filter' to 'none' */
     background: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiA/Pgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgdmlld0JveD0iMCAwIDEgMSIgcHJlc2VydmVBc3BlY3RSYXRpbz0ibm9uZSI+CiAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkLXVjZ2ctZ2VuZXJhdGVkIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPgogICAgPHN0b3Agb2Zmc2V0PSIwJSIgc3RvcC1jb2xvcj0iIzczYTBlMiIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiMyMTVkYTAiIHN0b3Atb3BhY2l0eT0iMSIvPgogIDwvbGluZWFyR3JhZGllbnQ+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgZmlsbD0idXJsKCNncmFkLXVjZ2ctZ2VuZXJhdGVkKSIgLz4KPC9zdmc+);
     background: -moz-linear-gradient(top,  #a90329 0%, #6d0019 100%); /* FF3.6+ */
     background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,#a90329), color-stop(100%,#6d0019)); /* Chrome,Safari4+ */
     background: -webkit-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* Chrome10+,Safari5.1+ */
     background: -o-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* Opera 11.10+ */
     background: -ms-linear-gradient(top,  #a90329 0%,#6d0019 100%); /* IE10+ */
     background: linear-gradient(to bottom,  #a90329 0%,#6d0019 100%); /* W3C */
     filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#a90329', endColorstr='#6d0019',GradientType=0 ); /* IE6-8 */
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
<div id="title"><%=h(bean.getTitle())%></div>
<div>
    <%if(canPublish){%>
        <a class="banner-button-small" style="float:left; margin-top:2px; margin-left:2px;" href="<%=h(publishUrl)%>">Publish</a>
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
<%if(bean.getCitation() != null && bean.getPublicationLink() != null){%>
    <div id="citation"><%=h(bean.getCitation())%> <strong><br />[<a href="<%=h(bean.getPublicationLink())%>" target="_blank">Publication</a>]</strong></div>
<%}%>
<%if(bean.getCitation() != null && bean.getPublicationLink() == null){%>
    <div id="citation"><%=h(bean.getCitation())%> </div>
<%}%>
<%if(bean.getCitation() == null && bean.getPublicationLink() != null){%>
    <div id="citation"><strong><br />[<a href="<%=h(bean.getPublicationLink())%>" target="_blank">Publication</a>]</strong></div>
<%}%>
<ul>
    <%if(bean.getOrganism() != null){%>
 <li><strong>Organism:</strong> <%=h(bean.getOrganism())%></li>
    <%}%>
    <%if(bean.getInstrument() != null){%>
 <li><strong>Instrument:</strong> <%=h(bean.getInstrument())%></li>
    <%}%>
    <%if(bean.getSpikeIn() != null){%>
 <li><strong>SpikeIn:</strong>
     <%=bean.getSpikeIn() ? "Yes" : "No"%>
 </li>
    <%}%>
</ul>
    <%if(bean.getAbstract() != null){%>
<div class="descriptionBox"><legend>Abstract</legend><div class="content"><%=h(bean.getAbstract())%></div></div>
    <%}%>
    <%if(bean.getExperimentDescription() != null){%>
<div class="descriptionBox"><legend>Experiment Description</legend><div class="content"><%=h(bean.getExperimentDescription())%></div> </div>
    <%}%>
    <%if(bean.getSampleDescription() != null){%>
<div class="descriptionBox"><legend>Sample Description</legend><div class="content"><%=h(bean.getSampleDescription())%></div></div>
    <%}%>

<div style="text-align: center; margin-top:15px;">
    <span>Created on <%=h(SimpleDateFormat.getInstance().format(bean.getCreated()))%> </span>
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