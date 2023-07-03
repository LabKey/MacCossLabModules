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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.CopyExperimentAction" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.CopyExperimentForm" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.GetPxActionsAction" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.model.Journal" %>
<%@ page import="org.labkey.panoramapublic.query.ExperimentAnnotationsManager" %>
<%@ page import="org.labkey.panoramapublic.model.Submission" %>
<%@ page import="org.labkey.panoramapublic.model.JournalSubmission" %>
<%@ page import="static org.labkey.panoramapublic.PanoramaPublicController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>

<%
    JspView<CopyExperimentBean> me = (JspView<CopyExperimentBean>) HttpView.currentView();
    var bean = me.getModelBean();
    CopyExperimentForm form = bean.getForm();
    ExperimentAnnotations expAnnot = form.lookupExperiment();
    Journal journal = form.lookupJournal();
    JournalSubmission js = bean.getJournalSubmission();
    Submission currentSubmission = js.getLatestSubmission();
    ExperimentAnnotations previousCopy = ExperimentAnnotationsManager.getLatestCopyForSubmission(js);
    boolean isRecopy = previousCopy != null;

    String selectedFolder = "Please select a destination folder...";
    if(form.getDestParentContainerId() != null)
    {
        Container destParent = ContainerManager.getForRowId(form.getDestParentContainerId());
        if(destParent != null)
        {
            selectedFolder = destParent.getName();
        }
    }

    ActionURL pxActionsUrl = urlFor(GetPxActionsAction.class);
    pxActionsUrl.addParameter("id", expAnnot.getId());

    ActionURL pxValidationUrl = getDataValidationCheckUrl(expAnnot.getId(), expAnnot.getContainer(), true);
    pxValidationUrl.addParameter(ActionURL.Param.returnUrl, js.getShortCopyUrl().getFullURL());
%>

<% if(previousCopy != null) { %>
<div style="margin-top:15px;">
    This experiment was last copied on <%=formatDateTime(previousCopy.getCreated())%> to the
    folder <%=link(previousCopy.getContainer().getName(), PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(previousCopy.getContainer()))%>.
</div>
<% } %>

<div style="margin-top:15px;" id="copyExperimentForm"></div>
<div style="margin-top:15px;">
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
                labelWidth: 250,
                width: 800,
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
                    width: 650,
                    value: <%=q(form.getDestContainerName())%>,
                    afterBodyEl: '<span style="font-size: 0.9em;">A new folder with this name will be created.</span>',
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
                    width: 650,
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
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Assign ProteomeXchange ID",
                    checked: <%=form.isAssignPxId()%>,
                    name: 'assignPxId',
                    boxLabel: 'This box will be checked if the user requested a ProteomeXchange ID. Admin doing the copy can override if needed.'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Use ProteomeXchange Test Database",
                    checked: <%=form.isUsePxTestDb()%>,
                    name: 'usePxTestDb',
                    boxLabel: 'Check this box for tests so that we get an ID from the ProteomeXchange test database rather than their production database.'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Assign Digital Object Identifier",
                    checked: <%=form.isAssignDoi()%>,
                    name: 'assignDoi'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Use DataCite Test API for creating DOI",
                    checked: <%=form.isUseDataCiteTestApi()%>,
                    name: 'useDataCiteTestApi',
                    boxLabel: 'Check this box for tests so that we get a DOI with the DataCite test API.'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Move and Symlink Files",
                    checked: <%=form.isMoveAndSymlink()%>,
                    name: 'moveAndSymlink',
                    boxLabel: 'Check this box to perform a move and symlink of the files instead of a full copy.'
                },
                {
                    xtype: 'textfield',
                    hidden: <%=!currentSubmission.isKeepPrivate() || isRecopy%>,
                    fieldLabel: "Reviewer Email Prefix",
                    value: <%=q(form.getReviewerEmailPrefix())%>,
                    name: 'reviewerEmailPrefix',
                    width: 650,
                    afterBodyEl: '<span style="font-size: 0.9em;">A new LabKey user account email_prefix(unique numeric suffix)@proteinms.net will be created. </span>',
                    msgTarget : 'under'
                },
                {
                    xtype: 'checkbox',
                    hidden: <%=!isRecopy%>,
                    fieldLabel: "Delete Previous Copy",
                    checked: <%=form.isDeleteOldCopy()%>,
                    name: 'deleteOldCopy'
                },

            ],
            buttonAlign: 'left',
            buttons: [{
                    text: 'Begin Copy',
                    cls: 'labkey-button primary',
                    handler: function(btn) {
                        var values = form.getForm().getValues();
                        form.submit({
                            url: <%=q(urlFor(CopyExperimentAction.class))%>,
                            method: 'POST',
                            params: values
                        });
                        btn.setDisabled(true);
                    },
                    margin: '20 10 0 10'
                },
                {
                    text: 'Validate for ProteomeXchange',
                    cls: 'labkey-button',
                    handler: function(btn) {
                        window.open(<%=q(pxValidationUrl)%>, "_blank");
                    }
                }
            ]
        });
    });
</script>