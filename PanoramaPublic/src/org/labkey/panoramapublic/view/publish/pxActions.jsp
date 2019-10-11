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
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>

<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>

<%
    JspView<PublishTargetedMSExperimentsController.PxExportForm> me = (JspView<PublishTargetedMSExperimentsController.PxExportForm>) HttpView.currentView();
    PublishTargetedMSExperimentsController.PxExportForm bean = me.getModelBean();
    ExperimentAnnotations expAnnot = bean.lookupExperiment();

    ActionURL generateXmlUrl = new ActionURL(PublishTargetedMSExperimentsController.ExportPxXmlAction.class, getContainer());
    generateXmlUrl.addParameter("id", expAnnot.getId());

    ActionURL validateXmlUrl = new ActionURL(PublishTargetedMSExperimentsController.ValidatePxXmlAction.class, getContainer());
    validateXmlUrl.addParameter("id", expAnnot.getId());
%>


<div id="pxExportForm"></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "pxExportForm",
            standardSubmit: true,
            border: false,
            frame: false,
            defaults: {
                labelWidth: 150,
                width: 500,
                labelStyle: 'background-color: #E0E6EA; padding: 5px;'
            },
            items: [
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                {
                    xtype: 'displayfield',
                    fieldLabel: "Experiment",
                    value: <%=q(expAnnot.getTitle())%>
                },
                {
                    xtype:'displayfield',
                    fieldLabel: "Experiment ID",
                    name: 'id',
                    value: <%=expAnnot.getId()%>
                },
                {
                    xtype:'hiddenfield',
                    name: 'id',
                    value: <%=expAnnot.getId()%>
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: 'Peer Reviewed',
                    name: 'peerReviewed',
                    value: <%=bean.getPeerReviewed()%>,
                    afterBodyEl: '<span style="font-size: 0.75em;margin-left:5px;">Check if data has been peer reviewed and published.</span>',
                    msgTarget : 'side'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'PubMed ID',
                    value: <%=q(bean.getPublicationId())%>,
                    name: 'publicationId'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Reference',
                    value: <%=q(bean.getPublicationReference())%>,
                    name: 'publicationReference'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Lab Head',
                    name: 'labHeadName'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Lab Head Email',
                    name: 'labHeadEmail'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Lab Head Affiliation',
                    name: 'labHeadAffiliation'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'PX Change Log',
                    name: 'changeLog'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'PX User Name',
                    name: 'pxUserName'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'PX Password',
                    name: 'pxPassword'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: 'Use PX Test DB',
                    name: 'testDatabase',
                    checked: <%=bean.isTestDatabase()%>,
                    value: <%=bean.isTestDatabase()%>,
                    afterBodyEl: '<span style="font-size: 0.75em;margin-left:5px;">Check to use the ProteomeXchange test database.</span>',
                    msgTarget : 'side'
                }
            ],
            buttonAlign: 'left',
            buttons: [
                    {
                        text: 'PX XML Summary',
                        cls: 'labkey-button',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.PxXmlSummaryAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Export PX XML',
                        cls: 'labkey-button',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.ExportPxXmlAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Validate PX XML',
                        cls: 'labkey-button',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.ValidatePxXmlAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Get PX ID',
                        cls: 'labkey-button primary',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.SavePxIdAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Submit PX XML',
                        cls: 'labkey-button primary',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.SubmitPxXmlAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Update PX XML',
                        cls: 'labkey-button primary',
                        handler: function() {
                            var values = form.getForm().getValues();
                            form.submit({
                                url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.UpdatePxXmlAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 10 0 0'
                    }
            ]
        });
    });
</script>