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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.targetedms.proteomexchange.ExperimentModificationGetter" %>
<%@ page import="org.labkey.targetedms.proteomexchange.SubmissionDataStatus" %>
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
    JspView<SubmissionDataStatus> me = (JspView<SubmissionDataStatus>) HttpView.currentView();
    SubmissionDataStatus bean = me.getModelBean();

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();

    ActionURL rawFilesUrl = urlProvider(ProjectUrls.class).getBeginURL(getContainer(), TargetedMSController.FolderSetupAction.RAW_FILES_TAB);
    ActionURL formUrl = PublishTargetedMSExperimentsController.getPublishExperimentURL(expAnnotations.getId(), getContainer(),
            true,  // keep private.
            false); // don't request a PX ID.
    ActionURL editUrl = TargetedMSController.getEditExperimentDetailsURL(getContainer(), expAnnotations.getId(),
            TargetedMSController.getViewExperimentDetailsURL(expAnnotations.getId(), getContainer()));

%>

<div style="margin: 30px 20px 20px 20px">
    The following information is required for getting a ProteomeXchange ID for your submission. <span style="margin-left:10px;"><%=link("Continue Without ProteomeXchange ID", formUrl)%></span>

    <% if(bean.hasMissingMetadata()) { %>
    <div style="margin-top:10px;margin-bottom:20px;">
        <span style="font-weight:bold;">Missing experiment metadata:</span>
        <ul>
            <%for(String missing: bean.getMissingMetadata()) {%>
            <li><%=h(missing)%></li>
            <%}%>
        </ul>
        <%=button("Update Experiment Metadata").href(editUrl).build()%>
    </div>
    <%}%>

    <% if(bean.hasInvalidModifications()) { %>
    <div style="margin-top:10px;margin-bottom:20px;">
        <span style="font-weight:bold;">The following modifications do not have a Unimod ID:</span>
        <table class="table-condensed table-striped table-bordered" style="margin-top:1px; margin-bottom:2px;">
            <thead>
            <tr>
                <th>Skyline Document</th>
                <th>Modification</th>
            </tr>
            </thead>
            <tbody>
            <%for(ExperimentModificationGetter.PxModification mod: bean.getInvalidMods()) {%>
            <tr>
                <td>
                    <%for(String run: mod.getSkylineDocs()){%>
                    <%=h(run)%><br/>
                    <%}%>
                </td>
                <td>
                    <%=h(mod.getName())%><br/>
                </td>
            </tr>
            <%}%>
        </table>
        Please choose from a list of Unimod modifications in Skyline and re-upload your document.
        If you do not find your modifications in Skyline's list of Unimod modifications please contact the Skyline / Panorama team.
    </div>
    <%}%>

    <% if(bean.hasMissingRawFiles()) { %>
    <div style="margin-top:10px;">
        <span style="font-weight:bold;">Missing raw data:</span>
        <table class="table-condensed table-striped table-bordered" style="margin-top:1px; margin-bottom:5px;">
            <thead>
            <tr>
            <th>Skyline Document</th>
            <th>Missing Raw Data</th>
            </tr>
            </thead>
            <tbody>
            <%for(SubmissionDataStatus.MissingRawData missingData: bean.getMissingRawData()) {%>
            <tr>
                <td>
                    <%for(String run: missingData.getSkyDocs()){%>
                        <%=h(run)%><br/>
                    <%}%>
                </td>
                <td>
                    <%for(String file: missingData.getRawData()){%>
                    <%=h(file)%><br/>
                     <%}%>
                </td>
            </tr>
            <%}%>
            </tbody>
        </table>
        <%=button("Upload Raw Data").href(rawFilesUrl).build()%> <span>(Drag and drop to the files browser in the Raw Data tab to upload files)</span>
    </div>
    <%}%>

</div>
