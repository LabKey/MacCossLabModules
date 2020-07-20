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
    JspView<PanoramaPublicController.PreSubmissionCheckForm> me = (JspView<PanoramaPublicController.PreSubmissionCheckForm>) HttpView.currentView();
    PanoramaPublicController.PreSubmissionCheckForm bean = me.getModelBean();
    SubmissionDataStatus status = bean.getValidationStatus();

    ExperimentAnnotations expAnnotations = status.getExperimentAnnotations();
    JournalExperiment je = JournalManager.getLastPublishedRecord(expAnnotations.getId());
    boolean requestPxId = status.canSubmitToPx(); // Request a PX ID if the data validates for a PX submission.
    boolean doIncompletePxSubmission = status.isIncomplete();

    ActionURL rawFilesUrl = PanoramaPublicManager.getRawDataTabUrl(getContainer());
    boolean keepPrivate = je == null ? true : je.isKeepPrivate();
    int journalId = je == null ? 0 : je.getJournalId();

    ActionURL submitUrl = je == null ? new ActionURL(PanoramaPublicController.PublishExperimentAction.class, getContainer()) // Data has not yet been submitted.
            : (je.getCopied()) == null ?
            new ActionURL(PanoramaPublicController.UpdateJournalExperimentAction.class, getContainer()) // Data submitted but not copied yet.
            :
            new ActionURL(PanoramaPublicController.RepublishJournalExperimentAction.class, getContainer()); // Data has been copied to Panorama Public.  This is a re-submit.

    ActionURL editUrl = PanoramaPublicController.getEditExperimentDetailsURL(getContainer(), expAnnotations.getId(),
            PanoramaPublicController.getViewExperimentDetailsURL(expAnnotations.getId(), getContainer()));

    String continueSubmissionText;
    String continueIncomplete = "Continue with an incomplete ProteomeXchange submission";
    if(status.canSubmitToPx())
    {
        continueSubmissionText = bean.isNotSubmitting() ? "Data is valid for an \"incomplete\" ProteomeXchange submission." : continueIncomplete;
    }
    else
    {
        continueSubmissionText = bean.isNotSubmitting() ? "Data cannot be submitted to ProteomeXchange.": "Continue without a ProteomeXchange ID";
    }
%>

<div style="margin: 30px 20px 20px 20px">
    <% if(bean.isNotSubmitting()) {%>
        <%=h(continueSubmissionText)%> <br><br>
    <% } %>
    The following information is required for a "complete" ProteomeXchange submission. <span style="margin-left:10px;">
    <% if(!bean.isNotSubmitting()) {%> <%=link(continueSubmissionText).onClick("submitForm();")%></span> <% } %>

    <% if(status.hasMissingMetadata()) { %>
    <div style="margin-top:10px;margin-bottom:20px;">
        <span style="font-weight:bold;">Missing experiment metadata:</span>
        <ul>
            <%for(String missing: status.getMissingMetadata()) {%>
            <li><%=h(missing)%></li>
            <%}%>
        </ul>
        <%=button("Update Experiment Metadata").href(editUrl).build()%>
    </div>
    <%}%>

    <% if(status.hasInvalidModifications()) { %>
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
            <%for(ExperimentModificationGetter.PxModification mod: status.getInvalidMods()) {%>
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

    <% if(status.hasMissingRawFiles()) { %>
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
            <%for(SubmissionDataStatus.MissingRawData missingData: status.getMissingRawData()) {%>
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

    <% if(status.hasMissingLibrarySourceFiles()) { %>
    <div style="margin-top:10px;">
        <span style="font-weight:bold;">Missing files for spectrum libraries:</span>

        <br>
        <div style="color:steelblue;margin-bottom: 10px;">
            Raw data and search results used to build spectrum libraries associated with Skyline documents are required
            for a "complete" ProteomeXchange submission.
            You can click the <span style="font-weight:bold;">"<%=h(continueIncomplete)%>"</span> link
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
            <%for(Map.Entry<String, SubmissionDataStatus.MissingLibrarySourceFiles> missingFiles: status.getMissingLibFiles().entrySet()) {%>
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
<div id="publishExperimentForm"></div>
<script type="text/javascript">
    var publishForm;
    Ext4.onReady(function()
    {
        publishForm = Ext4.create('Ext.form.Panel', {
            renderTo: "publishExperimentForm",
            standardSubmit: true,
            border: false,
            frame: false,
            items: [
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                {
                    xtype: 'hidden',
                    name: 'id',
                    value: <%=expAnnotations.getId()%>
                },
                {
                    xtype: 'hidden',
                    name: 'journalId',
                    value: <%=journalId%>
                },
                {
                    xtype: 'hidden',
                    name: 'keepPrivate',
                    value: <%=keepPrivate%>
                },
                {
                    xtype: 'hidden',
                    name: 'getPxid',
                    value: <%=requestPxId%>,
                },
                {
                    xtype: 'hidden',
                    name: 'incompletePxSubmission',
                    value: <%=doIncompletePxSubmission%>,
                }
            ]
        });
    });

    function submitForm()
    {
        if(publishForm)
        {
            var values = publishForm.getForm().getValues();
            // console.log(values);
            publishForm.submit({url: <%=q(submitUrl.getLocalURIString())%>, method: 'GET', params: values});
        }
        else
        {
            alert("Could not continue with the request. Please contact the server administrator.");
        }
    }
</script>

