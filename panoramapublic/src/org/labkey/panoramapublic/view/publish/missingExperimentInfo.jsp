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
            bean.canSubmitToPx()); // don't request a PX ID.
    ActionURL editUrl = PanoramaPublicController.getEditExperimentDetailsURL(getContainer(), expAnnotations.getId(),
            PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), getContainer()));

    String incompleteSubmissionTxt = "Continue with an incomplete ProteomeXchange submission";
    String continueSubmissionText = bean.canSubmitToPx() ? incompleteSubmissionTxt : "Continue without ProteomeXchange ID";
%>

<div style="margin: 30px 20px 20px 20px">
    The following information is required for a "complete" ProteomeXchange submission.
    <% if(!resubmit || resubmit && bean.canSubmitToPx()) {%>
        <span style="margin-left:10px;"><%=link(continueSubmissionText, formUrl)%></span>
    <%}%>

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

        <br>
        <div style="color:steelblue;margin-bottom: 10px;">
            Raw data and search results used to build spectrum libraries associated with Skyline documents are required
            for a "complete" ProteomeXchange submission.
            You can click the <span style="font-weight:bold;">"<%=h(incompleteSubmissionTxt)%>"</span> link
            at the top of the page to proceed with an "incomplete" ProteomeXchange submission.
            You will see the link only if all the raw files imported into the Skyline documents have been uploaded and all
            the required experiment metadata (e.g. abstract, organism, instrument etc.) has been provided.

            <br><br>
            You do not have to upload the files used to build a spectrum library if one of the following conditions applies:
            <ul>
                <li>Raw data and search results have been uploaded to another ProteomeXchange repository</li>
                <li>The library was downloaded from a public resource</li>
                <li>The library is irrelevant to results OR was used only as supporting information</li>
            </ul>
            If one of the above applies, you can respond to the confirmation email from Panorama Public after your data has been
            copied.  In your email, please include the reason for not uploading files for a spectrum library.
            If the files are in another ProteomeXchange repository such as PRIDE or MassIVE then let us know the PXD accession of
            the data and the reviewer account details if the data in the repository is private.  For a library that was downloaded
            from a public resource please provide the URL of the resource.
            We will upgrade your submission to a "complete" ProteomeXchange submission if we are able to verify the details.
        </div>

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
        <%=button("Upload Library Source Data").href(rawFilesUrl).build()%> <span>(Drag and drop to the files browser in the Raw Data tab to upload files)</span>
    </div>
    <%}%>

</div>

