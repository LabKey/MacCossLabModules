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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ShortURLRecord" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.targetedms.model.Journal" %>
<%@ page import="org.labkey.targetedms.query.ExperimentAnnotationsManager" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
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
    JspView<PublishTargetedMSExperimentsController.PublishExperimentFormBean> me = (JspView<PublishTargetedMSExperimentsController.PublishExperimentFormBean>) HttpView.currentView();
    PublishTargetedMSExperimentsController.PublishExperimentFormBean bean = me.getModelBean();
    PublishTargetedMSExperimentsController.PublishExperimentForm form = bean.getForm();

    String shortAccessUrl = StringUtils.trimToEmpty(form.getShortAccessUrl());

    Journal journal = bean.getForm().lookupJournal();
    int journalId = journal != null ? journal.getId() : 0;
    String journalName = journal != null ? journal.getName() : "No_Name";

    ShortURLRecord accessRecord = new ShortURLRecord();
    accessRecord.setShortURL(shortAccessUrl);

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();
    Set<Container> experimentFolders = ExperimentAnnotationsManager.getExperimentFolders(expAnnotations, getUser());

    boolean isUpdate = bean.getForm().isUpdate();
    String publishButtonText = isUpdate ? "Update" : "Submit";
    String submitUrl = isUpdate ? new ActionURL(PublishTargetedMSExperimentsController.UpdateJournalExperimentAction.class, getContainer()).getLocalURIString() :
            new ActionURL(PublishTargetedMSExperimentsController.PublishExperimentAction.class, getContainer()).getLocalURIString();
    String cancelUrl = TargetedMSController.getViewExperimentDetailsURL(bean.getForm().getId(), getContainer()).getLocalURIString();

    boolean siteAdmin = getUser().hasSiteAdminPermission();
    boolean getLabHeadUserInfo = form.isGetPxid() && expAnnotations.getLabHeadUser() == null;
%>

<div id="publishExperimentForm"></div>
<div style="margin: 30px 20px 20px 20px">
    By submitting the experiment you are granting access to <%=journalName%> to copy data as well as any
    wiki pages, custom views, custom queries, lists and R reports in the following folders:
    <ul>
    <%for(Container folder: experimentFolders) { %>
        <li><%=h(folder.getPath())%></li>
    <%}%>
    </ul>
</div>

<style>
    .helpMsg, .urlPart
    {
        font-size: 0.75em;
    }
    .bold
    {
        font-weight: bold;
    }
    div.urlMsg
    {
        margin: 5px 0 15px 0;
    }
    .red
    {
        color: red;
    }
</style>
<script type="text/javascript">

    var urlFixedPre = <%=q(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/")%>;
    var urlFixedPost = <%=q(ShortURLRecord.URL_SUFFIX)%>;

    var accessUrlMessage = '<div class="urlMsg">';
    accessUrlMessage += '<span class="helpMsg"><nobr>You may change the text above to a more convenient, easy to remember string.</nobr></span>';
    accessUrlMessage += '<br/>';
    accessUrlMessage += '<span class="helpMsg">This is the link that should be included in the manuscript to view your supplementary data on the Panorama server is. The full link is: </span>';
    accessUrlMessage += '</br>';
    accessUrlMessage += '<span class="bold">' + urlFixedPre + '</span>';
    accessUrlMessage += '<span class="bold" id="span_short_access_url">' + <%=q(accessRecord.getShortURL())%> + '</span>';
    accessUrlMessage += '<span class="bold">' + urlFixedPost + '</span>';
    accessUrlMessage += '</div>';


    Ext4.onReady(function(){

        var journalStore = Ext4.create('Ext.data.Store', {
            fields: ['journalId','name'],
            data:   [
                {"journalId": 0, "name": "Please select a target..."},
                <%for(Journal j: bean.getJournalList()){%>
                {"journalId":<%=j.getId()%>,"name":<%=q(j.getName())%>},
                <%}%>
            ]
        });

        var shortAccessUrlSpan;

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "publishExperimentForm",
            standardSubmit: true,
            border: false,
            frame: false,
            defaults: {
                labelWidth: 150,
                width: 600,
                labelStyle: 'background-color: #E0E6EA; padding: 5px;'
            },
            items: [
                { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF },
                {
                    xtype: 'hidden',
                    name: 'update',
                    value: <%=bean.getForm().isUpdate()%>
                },
                {
                    xtype: 'hidden',
                    name: 'id',
                    value: <%=bean.getExperimentAnnotations().getId()%>
                },
                {
                    xtype: 'displayfield',
                    fieldLabel: "Experiment",
                    value: <%=q(bean.getExperimentAnnotations().getTitle())%>
                },

                // If the user is updating an existing entry, don't allow them to choose a journal
                <%if(bean.getForm().isUpdate()) { %>
                    {
                        xtype: 'displayfield',
                        fieldLabel: "Submit To",
                        value: <%=q(journal.getName())%>
                    },
                    {
                        xtype: 'hidden',
                        name: 'journalId',
                        value: <%=journalId%>
                    },
                <%} else { %>
                    {
                        xtype: 'combobox',
                        name: 'journalId',
                        fieldLabel: 'Submit To',
                        queryMode: 'local',
                        forceSelection: 'true',
                        allowBlank: false,
                        displayField: 'name',
                        valueField: 'journalId',
                        editable: false,
                        inputValue: true,
                        triggerAction: 'all',
                        store: journalStore,
                        value: <%=journalId%>
                    },
                <%}%>
                {
                    xtype: 'textfield',
                    name: 'shortAccessUrl',
                    value: <%=q(shortAccessUrl)%>,
                    fieldLabel: 'Access Link',
                    beforeBodyEl: '<span class="urlPart">' + urlFixedPre + '</span>',
                    afterBodyEl: urlFixedPost + accessUrlMessage,
                    msgTarget : 'side',
                    inputWidth: '200',
                    enableKeyEvents: true,
                    listeners: {
                        'keyup': function(field)
                        {
                            var newUrl = field.getValue();
                            // console.log("Access URL changed to " + newUrl);
                            if(!shortAccessUrlSpan)
                            {
                                shortAccessUrlSpan = Ext4.get('span_short_access_url');
                            }
                            shortAccessUrlSpan.dom.innerHTML = newUrl;
                        }
                    }
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Keep Private",
                    hidden: false,
                    checked: <%=form.isKeepPrivate()%>,
                    name: 'keepPrivate',
                    boxLabel: 'Check this box to keep your data on Panorama Public private. Reviewer account details will be provided.'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Get ProteomeXchange ID",
                    hidden: <%=!siteAdmin%>,
                    checked: <%=form.isGetPxid()%>,
                    name: 'getPxid',
                    boxLabel: 'Check this box to get a ProteomeXchange ID for your data.'
                },
                {
                    xtype: 'textfield',
                    fieldLabel: "Lab Head Name",
                    hidden: <%=!getLabHeadUserInfo%>,
                    name: 'labHeadName',
                    value: <%=q(form.getLabHeadName())%>,
                    afterBodyEl: '<div class="helpMsg red">Please enter a lab head name to submit to ProteomeXchange. If a name is not entered the name of the submitter will be used.</div>',
                    msgTarget : 'side',
                    inputWidth: '200',
                },
                {
                    xtype: 'textfield',
                    fieldLabel: "Lab Head Email",
                    hidden: <%=!getLabHeadUserInfo%>,
                    name: 'labHeadEmail',
                    value: <%=q(form.getLabHeadEmail())%>,
                    inputWidth: '200',
                },
                {
                    xtype: 'textfield',
                    fieldLabel: "Lab Head Affiliation",
                    hidden: <%=!getLabHeadUserInfo%>,
                    name: 'labHeadAffiliation',
                    value: <%=q(form.getLabHeadAffiliation())%>,
                    inputWidth: '200',
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Skip Raw Data Check",
                    hidden: <%=!siteAdmin%>,
                    checked: false,
                    name: 'skipRawDataCheck'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Skip Meta Data Check",
                    hidden: <%=!siteAdmin%>,
                    checked: false,
                    name: 'skipMetaDataCheck'
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Skip Modifications Check",
                    hidden: <%=!siteAdmin%>,
                    checked: false,
                    name: 'skipModCheck'
                }
            ],
            buttonAlign: 'left',
            buttons: [{
                text: <%=q(publishButtonText)%>,
                cls: 'labkey-button primary',
                handler: function() {
                    var values = form.getForm().getValues();
                    console.log(values);
                    form.submit({
                        url: <%=q(submitUrl)%>,
                        method: 'GET',
                        params: values
                        });
                    }
                },
                {
                    text: 'Cancel',
                    cls: 'labkey-button',
                    hrefTarget: '_self',
                    href: <%=q(cancelUrl)%>
                }]
        });
    });
</script>