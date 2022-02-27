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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.DataLicense" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>

<labkey:errors/>
<%
    JspView<PanoramaPublicController.PanoramaPublicRequest> me = (JspView<PanoramaPublicController.PanoramaPublicRequest>) HttpView.currentView();
    PanoramaPublicController.PanoramaPublicRequest bean = me.getModelBean();
    PanoramaPublicController.PublishExperimentForm form = bean.getForm();

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();

    String journal = bean.getJournal().getName();
    DataLicense license = DataLicense.resolveLicense(form.getDataLicense());

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
    <%if(form.isResubmit()) { %>
    This experiment has already been copied by <%=h(journal)%>. If you click OK a request will be submitted to make a new copy.
    <br>
    <% } else if(form.isUpdate()) { %>
    You are updating your submission request to <%=h(journal)%>.
    <br>
    <% } else { %>
    You are giving access to <%=h(journal)%> to make a copy of your data.
    <% } %>
    The access link is: <%=h(ShortURLRecord.renderShortURL(form.getShortAccessUrl()))%>.
    <br>
    <%if(form.isKeepPrivate()) {%>
    Your data on <%=h(journal)%> will be kept private
    <%if(!form.isResubmit()) { %> and a reviewer account will be provided to you.<% } else {%>
    . The reviewer account details will be the same as before.<% } %>
    <%} else { %>
    Your data on <%=h(journal)%> will be made public.
    <% } %>
    <%if(form.isGetPxid()) { %>
        <br><br>
        <%if(!form.isResubmit()) { %>
            A ProteomeXchange ID will be requested for your data.
        <% } else { %>
            The ProteomeXchange ID assigned to your data will remain the same as before.
        <% } %>
        <%if(form.isIncompletePxSubmission()) { %>
        <br> The data will be submitted as "supported by repository but incomplete data and/or metadata" when it is made public on ProteomeXchange.
        <%}%>
        <br>
        The following user information will be submitted to ProteomeXchange<%if(form.isKeepPrivate()) { %> when this data is made public<%}%>:
        <br>
        <span style="font-weight:bold;">Submitter:</span>
        <ul>
            <li>Name: <%=h(expAnnotations.getSubmitterName())%></li>
            <li>Email: <%=h(expAnnotations.getSubmitterUser().getEmail())%></li>
            <%if(!StringUtils.isBlank(expAnnotations.getSubmitterAffiliation())) { %>
            <li>Affiliation: <%=h(expAnnotations.getSubmitterAffiliation())%></li>
            <% } %>
        </ul>
        <span style="font-weight:bold;">Lab Head:</span>
        <ul>
            <li>Name: <%=h(labHeadName)%></li>
            <%if(!StringUtils.isBlank(labHeadEmail)) { %>
            <li>Email: <%=h(labHeadEmail)%></li>
            <% } %>
            <%if(!StringUtils.isBlank(labHeadAffiliation)) { %>
            <li>Affiliation: <%=h(labHeadAffiliation)%></li>
            <% } %>
        </ul>
        <br>

    <% } %>
    <div style="font-weight:bold;font-style:italic;margin-top:10px;">
        <%if(form.isKeepPrivate()) { %>
            After you make your data public it will be available on Panorama Public
        <% } else { %>
            Your data on Panorama Public will be available
        <% } %>
        under the <%=license.getDisplayLinkHtml()%> license
    </div>
<br>
    Are you sure you want to continue?
</div>
<div>
<labkey:form action="<%=getActionURL().clone().deleteParameters()%>" method="POST">
    <%= button("Cancel").href(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(expAnnotations.getContainer())) %>
    <%= button("OK").disableOnClick(true).submit(true) %>
    <input type="hidden" name="update" value="<%=form.isUpdate()%>"/>
    <input type="hidden" name="validationId" value="<%=h(form.getValidationId())%>"/>
    <input type="hidden" name="resubmit" value="<%=form.isResubmit()%>"/>
    <input type="hidden" name="requestConfirmed" value="true"/>
    <input type="hidden" name="id" value="<%=form.getId()%>"/>
    <input type="hidden" name="journalId" value="<%=form.getJournalId()%>"/>
    <input type="hidden" name="shortAccessUrl" value="<%=h(form.getShortAccessUrl())%>"/>
    <input type="hidden" name="keepPrivate" value="<%=form.isKeepPrivate()%>"/>
    <input type="hidden" name="getPxid" value="<%=form.isGetPxid()%>"/>
    <input type="hidden" name="incompletePxSubmission" value="<%=form.isIncompletePxSubmission()%>"/>
    <input type="hidden" name="labHeadName" value="<%=h(form.getLabHeadName())%>"/>
    <input type="hidden" name="labHeadEmail" value="<%=h(form.getLabHeadEmail())%>"/>
    <input type="hidden" name="labHeadAffiliation" value="<%=h(form.getLabHeadAffiliation())%>"/>
    <input type="hidden" name="dataLicense" value="<%=h(form.getDataLicense())%>"/>
</labkey:form>
</div>
