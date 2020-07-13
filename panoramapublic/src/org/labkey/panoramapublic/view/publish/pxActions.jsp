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
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
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
    JspView<PanoramaPublicController.PxActionsForm> me = (JspView<PanoramaPublicController.PxActionsForm>) HttpView.currentView();
    PanoramaPublicController.PxActionsForm bean = me.getModelBean();
    ExperimentAnnotations expAnnot = bean.lookupExperiment();
%>


<div id="pxMethodsForm"></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "pxMethodsForm",
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
                    value: <%=expAnnot.getId()%>
                },
                {
                    xtype:'hiddenfield',
                    name: 'id',
                    value: <%=expAnnot.getId()%>
                },
                {
                    xtype:'hiddenfield',
                    name: 'method',
                    value: <%=q(bean.getMethod())%>
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'PX Change Log',
                    name: 'changeLog',
                    value: <%=q(bean.getChangeLog())%>
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
                                url: <%=q(new ActionURL(PanoramaPublicController.PxXmlSummaryAction.class, getContainer()).getLocalURIString())%>,
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
                                url: <%=q(new ActionURL(PanoramaPublicController.ExportPxXmlAction.class, getContainer()).getLocalURIString())%>,
                                method: 'POST',
                                params: values
                            });
                        },
                        margin: '20 30 0 0'
                    },
                    {
                        text: 'Get PX ID',
                        cls: 'labkey-button primary',
                        handler: function() {
                            form.getForm().findField('method').setValue(<%=q(PanoramaPublicController.PX_METHOD.GET_ID.toString())%>);
                            submitPxForm();
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Validate PX XML',
                        cls: 'labkey-button',
                        handler: function() {
                            form.getForm().findField('method').setValue(<%=q(PanoramaPublicController.PX_METHOD.VALIDATE.toString())%>);
                            submitPxForm();
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Submit PX XML',
                        cls: 'labkey-button primary',
                        handler: function() {
                            form.getForm().findField('method').setValue(<%=q(PanoramaPublicController.PX_METHOD.SUBMIT.toString())%>);
                            submitPxForm();
                        },
                        margin: '20 10 0 0'
                    },
                    {
                        text: 'Update PX XML',
                        cls: 'labkey-button primary',
                        handler: function() {
                            form.getForm().findField('method').setValue(<%=q(PanoramaPublicController.PX_METHOD.UPDATE.toString())%>);
                            submitPxForm();
                        },
                        margin: '20 10 0 0'
                    }
            ]
        });

        function submitPxForm() {
            var values = form.getForm().getValues();
            form.submit({
                url: <%=q(new ActionURL(PanoramaPublicController.GetPxActionsAction.class, getContainer()).getLocalURIString())%>,
                method: 'POST',
                params: values
            });
        }
    });
</script>