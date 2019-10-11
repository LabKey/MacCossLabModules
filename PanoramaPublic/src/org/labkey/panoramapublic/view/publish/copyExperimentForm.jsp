<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.targetedms.model.Journal" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.data.Container" %>
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
    JspView<PublishTargetedMSExperimentsController.CopyExperimentForm> me = (JspView<PublishTargetedMSExperimentsController.CopyExperimentForm>) HttpView.currentView();
    PublishTargetedMSExperimentsController.CopyExperimentForm bean = me.getModelBean();
    ExperimentAnnotations expAnnot = bean.lookupExperiment();
    Journal journal = bean.lookupJournal();
    String selectedFolder = "Please select a destination folder...";
    if(bean.getDestParentContainerId() != null)
    {
        Container destParent = ContainerManager.getForRowId(bean.getDestParentContainerId());
        if(destParent != null)
        {
            selectedFolder = destParent.getName();
        }
    }

    ActionURL pxActionsUrl = new ActionURL(PublishTargetedMSExperimentsController.GetPxActionsAction.class, getContainer());
    pxActionsUrl.addParameter("id", expAnnot.getId());
%>


<div id="copyExperimentForm"></div>
<div>
    <%=link("ProteomeXchange Actions", pxActionsUrl)%>
</div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var folderTreeStore = Ext4.create('Ext.data.TreeStore', {
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                extraParams: {move: false, requiredPermission: <%=q(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>, showContainerTabs: false}
            },
            root: {
                expanded: false
            },
            folderSort: false,
            autoLoad: true,
            defaultRootId: <%=ContainerManager.getRoot().getRowId()%>
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "copyExperimentForm",
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
                    xtype:'hidden',
                    name: 'id',
                    value: <%=expAnnot.getId()%>
                },
                {
                    xtype:'hidden',
                    name: 'journalId',
                    value: <%=journal.getId()%>
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Folder name',
                    name: 'destContainerName',
                    allowBlank: false,
                    width: 450,
                    value: <%=q(bean.getDestContainerName())%>,
                    afterBodyEl: '<span style="font-size: 0.75em;">A new folder with this name will be created.</span>',
                    msgTarget : 'under'
                },
                {
                    xtype: 'hidden',
                    name: 'destParentContainerId',
                    id: 'destParentContainer_Input'
                },
                {
                    xtype: 'displayfield',
                    fieldLabel: 'Destination',
                    value: <%=q(selectedFolder)%>,
                    width: 450,
                    id: 'destParentContainer_DisplayField',
                    margin: '20 0 0 0'
                },
                {
                    xtype: 'treepanel',
                    fieldLabel: 'Destination',
                    store: folderTreeStore,
                    rootVisible: false,
                    enableDrag: false,
                    useArrows : false,
                    autoScroll: true,
                    title : '',
                    border: true,
                    width: 450,
                    height:150,
                    listeners: {
                        select: function(node, record, index, eOpts){
                            //console.log("the record is...");
                            //console.log(record.get('id'));
                            //console.log(record.get('text'));

                            var displayField = Ext4.ComponentQuery.query('#destParentContainer_DisplayField')[0];
                            displayField.setValue(record.get('text'));

                            var hiddenField = Ext4.ComponentQuery.query('#destParentContainer_Input')[0];
                            hiddenField.setValue(record.get('id'));
                        }
                    }
                }
            ],
            buttonAlign: 'left',
            buttons: [{
                text: 'Begin Copy',
                handler: function() {
                    var values = form.getForm().getValues();
                    form.submit({
                        url: <%=q(new ActionURL(PublishTargetedMSExperimentsController.CopyExperimentAction.class, getContainer()).getLocalURIString())%>,
                        method: 'POST',
                        params: values
                    });
                },
                margin: '20 0 0 0'
            }]
        });
    });
</script>