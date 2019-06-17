<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>

<labkey:errors/>
<%
    JspView<PublishTargetedMSExperimentsController.PublishExperimentConfirmBean> me = (JspView<PublishTargetedMSExperimentsController.PublishExperimentConfirmBean>) HttpView.currentView();
    PublishTargetedMSExperimentsController.PublishExperimentConfirmBean bean = me.getModelBean();
    PublishTargetedMSExperimentsController.PublishExperimentForm form = bean.getForm();

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();

    String journal = bean.getJournal().getName();

    String labHeadName = form.getLabHeadName();
    String labHeadEmail = form.getLabHeadEmail();
    String labHeadAffiliation = form.getLabHeadAffiliation();
    if(labHeadName == null)
    {
        User labHead = expAnnotations.getLabHeadUser() != null ? expAnnotations.getLabHeadUser() : expAnnotations.getSubmitterUser();
        if(labHead != null)
        {
            labHeadName = ExperimentAnnotations.getUserName(labHead);
            labHeadEmail = labHead.getEmail();
        }
        labHeadAffiliation = expAnnotations.getLabHeadUser() != null ? expAnnotations.getLabHeadAffiliation() : expAnnotations.getSubmitterAffiliation();
    }
%>

<div>
    <%if(!form.isUpdate()) { %>
    You are giving access to <%=h(journal)%> to make a copy of your data.
    <br>
    <% } else { %>
    You are updating your submission request to <%=h(journal)%>.
    <br>
    <% } %>
    The access link is: <%=h(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/" + form.getShortAccessUrl())%>.
    <br>
    <%if(form.isKeepPrivate()) {%>
    Your data on <%=h(journal)%> will be kept private and a reviewer account will be provided to you.
    <%} else { %>
    Your data on <%=h(journal)%> will be made public.
    <% } %>
    <%if(form.isGetPxid()) { %>
        <br><br>
        A ProteomeXchange ID will be requested for your data.
        The following user information will be submitted to ProteomeXchange:
        <br>
        <span style="font-weight:bold;">Submitter:</span>
        <ul>
            <li>Name: <%=expAnnotations.getSubmitterName()%></li>
            <li>Email: <%=expAnnotations.getSubmitterUser().getEmail()%></li>
            <%if(!StringUtils.isBlank(expAnnotations.getSubmitterAffiliation())) { %>
            <li>Affiliation: <%=expAnnotations.getSubmitterAffiliation()%></li>
            <% } %>
        </ul>
        <span style="font-weight:bold;">Lab Head:</span>
        <ul>
            <li>Name: <%=labHeadName%></li>
            <%if(!StringUtils.isBlank(labHeadEmail)) { %>
            <li>Email: <%=labHeadEmail%></li>
            <% } %>
            <%if(!StringUtils.isBlank(labHeadAffiliation)) { %>
            <li>Affiliation: <%=labHeadAffiliation%></li>
            <% } %>
        </ul>
        <br>
    <% } %>
<br>
    Are you sure you want to continue?
</div>
