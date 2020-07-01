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
<%@ page import="org.labkey.panoramapublic.proteomexchange.SubmissionDataStatus" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicManager" %>
<%@ page import="org.labkey.panoramapublic.proteomexchange.ExperimentModificationGetter" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.Link" %>
<%@ page import="org.labkey.panoramapublic.model.JournalExperiment" %>
<%@ page import="org.labkey.panoramapublic.query.JournalManager" %>
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
    JspView<SubmissionDataStatus> me = (JspView<SubmissionDataStatus>) HttpView.currentView();
    SubmissionDataStatus bean = me.getModelBean();

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();
    JournalExperiment je = JournalManager.getLastPublishedRecord(expAnnotations.getId());
    boolean resubmit = je != null;

    ActionURL rawFilesUrl = PanoramaPublicManager.getRawDataTabUrl(getContainer());
    ActionURL formUrl = PanoramaPublicController.getPublishExperimentURL(expAnnotations.getId(), getContainer(),
            true,  // keep private.
            false); // don't request a PX ID.
    ActionURL editUrl = PanoramaPublicController.getEditExperimentDetailsURL(getContainer(), expAnnotations.getId(),
            PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), getContainer()));

%>

<div style="margin: 30px 20px 20px 20px">
    The following information is required for getting a ProteomeXchange ID for your submission.
    <% if(!resubmit) {%> <span style="margin-left:10px;"><%=link("Continue Without ProteomeXchange ID", formUrl)%></span> <%}%>

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

    <% if(bean.hasMissingLibrarySourceFiles()) { %>
    <div style="margin-top:10px;">
        <span style="font-weight:bold;">Missing files for spectrum libraries:</span>
        <table class="table-condensed table-striped table-bordered" style="margin-top:1px; margin-bottom:5px;">
            <thead>
            <tr>
            <th>Library</th>
            <th>Skyline Document</th>
            <th>Missing Raw Data</th>
            <th>Missing ID Files</th>
            </tr>
            </thead>
            <tbody>
            <%for(Map.Entry<String, SubmissionDataStatus.MissingLibrarySourceFiles> missingFiles: bean.getMissingLibFiles().entrySet()) {%>
            <tr>
                <td>
                    <%=h(missingFiles.getKey())%>
                </td>
                <td>
                    <%for(String skyDoc: missingFiles.getValue().getSkyDocs()){%>
                    <%=h(skyDoc)%><br/>
                    <%}%>
                </td>
                <td>
                    <%for(String spectrumSourceFile: missingFiles.getValue().getSpectrumSourceFiles()){%>
                    <%=h(spectrumSourceFile)%><br/>
                    <%}%>
                </td>
                <td>
                    <%for(String idFile: missingFiles.getValue().getIdFiles()){%>
                    <%=h(idFile)%><br/>
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

