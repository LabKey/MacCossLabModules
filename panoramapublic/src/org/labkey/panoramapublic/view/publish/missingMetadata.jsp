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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.query.DataValidationManager" %>
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
    JspView<PanoramaPublicController.MissingMetadataBean> me = (JspView<PanoramaPublicController.MissingMetadataBean>) HttpView.currentView();
    PanoramaPublicController.MissingMetadataBean bean = me.getModelBean();
    DataValidationManager.MissingMetadata missingMetadata = bean.getMissingMetadata();

    ExperimentAnnotations expAnnotations = bean.getExpAnnotations();

    ActionURL noPxSubmissionUrl = getActionURL().clone().replaceParameter("skipPxCheck", "true").replaceParameter("getPxid", "false");

    ActionURL editUrl = PanoramaPublicController.getEditExperimentDetailsURL(getContainer(), expAnnotations.getId(),
            PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), getContainer()));
%>

<div>
    The following information is required
    <% if(bean.isPxSubmission() && !missingMetadata.hasAlwaysRequiredFields()) {%>
    for a ProteomeXchange submission.
    <% } else { %>
    for submitting data to Panorama Public.
    <% } %>

    <% if(bean.getMissingMetadata().count() > 0) { %>
        <div style="font-weight:bold; margin-top:10px;">Missing experiment metadata:</div>
        <ul>
            <%for(String missing: missingMetadata.getMessages()) {%>
            <li><%=h(missing)%></li>
            <%}%>
        </ul>
        <%=button("Update Experiment Metadata").href(editUrl).build()%>
        </br> </br>
        <% if (!bean.getMissingMetadata().hasAlwaysRequiredFields()) { %>
          <!-- Allow continuing without a PXD only if none of the "always required" fields are missing -->
          <%=link("Submit without a ProteomeXchange ID", noPxSubmissionUrl)%></span>
        <% } %>
    </div>
    <%}%>
</div>