<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExperimentAnnotations> me = (JspView<ExperimentAnnotations>) HttpView.currentView();
    ExperimentAnnotations bean = me.getModelBean();
    ActionURL editUrl = TargetedMSController.getEditExperimentDetailsURL(getContainer(), bean.getId(),
            TargetedMSController.getViewExperimentDetailsURL(bean.getId(), getContainer()));
    final boolean canEdit = bean.getContainer().hasPermission(getUser(), InsertPermission.class);
    String createdBy = UserManager.getDisplayName(bean.getCreatedBy(), UserManager.getUser(bean.getCreatedBy()));
%>
<style>
 #title
 {
     font-size: 17px;
     font-weight: bold;
     float:left;
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

</style>
<div id="annotationContainer">
<div id="title"><%=h(bean.getTitle())%></div>
    <%if(canEdit){%>
    <a class="banner-button-small" style="float:left; margin-top:2px; margin-left:2px;" href="<%=h(editUrl)%>" target="_blank">Edit  Experiment</a>
    <%}%>
    <br />
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
<div class="descriptionBox"><legend>Abstract</legend><div class="content"><p><%=h(bean.getAbstract())%></p></div></div>
    <%}%>
    <%if(bean.getExperimentDescription() != null){%>
<div class="descriptionBox"><legend>Experiment Description</legend><div class="content"><p><%=h(bean.getExperimentDescription())%></p></div> </div>
    <%}%>
    <%if(bean.getSampleDescription() != null){%>
<div class="descriptionBox"><legend>Sample Description</legend><div class="content"><p><%=h(bean.getSampleDescription())%></p></div>    </div>
    <%}%>

    <div style="text-align: center; margin-top:15px;">
<span>Created by <%=h(createdBy)%> on <%=h(bean.getCreated())%> </span>
        </div>

</div>

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script type="text/javascript">

    var READ_MORE_TEXT = "Read more";
    var READ_LESS_TEXT = "Close";
    var BASE_SLIDE_TIME = 100;
    var LINE_THRESHOLD = 3.0;

    function adjustContent(element) {
        var newP = $("<p />").html($("<a />").text(READ_MORE_TEXT).click(function() {
            var content = $(this).parent().prev();
            var smallHeight = content.data("smallheight");
            var fullHeight = content.data("fullheight") + 10;
            var expand = content.height() < fullHeight;
            var slideTime = fullHeight - smallHeight + BASE_SLIDE_TIME;
            content.animate(
                    {height: expand ? fullHeight : smallHeight},
                    {queue: false, duration: slideTime}
            );
            $(this).fadeOut({
                        queue: false, duration: slideTime / 2, always: function() {
                            $(this).text(expand ? READ_LESS_TEXT : READ_MORE_TEXT);
                            $(this).fadeIn({queue: false, duration: slideTime / 2});
                        }}
            );
        }));
        var lineThresholdHeight = parseInt(element.css("line-height", "120%").css("line-height")) * LINE_THRESHOLD + 1;
        if (element.height() > lineThresholdHeight) {
            element.after(newP)
                    .data("fullheight", element.height()).data("smallheight", lineThresholdHeight)
                    .css("overflow", "hidden").height(lineThresholdHeight);
        }
    }

    $(document).ready(function()
    {
        $(".content").each(function() {adjustContent($(this));});
    });
    window.onresize = function(){ location.reload(); }
</script>