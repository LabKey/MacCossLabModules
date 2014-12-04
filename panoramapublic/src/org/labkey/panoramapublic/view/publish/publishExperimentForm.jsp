<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.gwt.client.util.StringUtils" %>
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>

<%
    JspView<PublishTargetedMSExperimentsController.PublishExperimentFormBean> me = (JspView<PublishTargetedMSExperimentsController.PublishExperimentFormBean>) HttpView.currentView();
    PublishTargetedMSExperimentsController.PublishExperimentFormBean bean = me.getModelBean();
    PublishTargetedMSExperimentsController.PublishExperimentForm form = bean.getForm();

    String shortAccessUrl = StringUtils.nullToEmpty(form.getShortAccessUrl());
    String shortCopyUrl = StringUtils.nullToEmpty(form.getShortCopyUrl());

    Journal journal = bean.getForm().lookupJournal();
    int journalId = journal != null ? journal.getId() : 0;

    ShortURLRecord accessRecord = new ShortURLRecord();
    accessRecord.setShortURL(shortAccessUrl);

    ShortURLRecord copyRecord = new ShortURLRecord();
    copyRecord.setShortURL(shortCopyUrl);

    ExperimentAnnotations expAnnotations = bean.getExperimentAnnotations();
    Set<Container> experimentFolders = ExperimentAnnotationsManager.getExperimentFolders(expAnnotations, getUser());

    boolean isUpdate = bean.getForm().isUpdate();
    String publishButtonText = isUpdate ? "Update" : "Publish";
    String submitUrl = isUpdate ? new ActionURL(PublishTargetedMSExperimentsController.UpdateJournalExperimentAction.class, getContainer()).getLocalURIString() :
            new ActionURL(PublishTargetedMSExperimentsController.PublishExperimentAction.class, getContainer()).getLocalURIString();
    String cancelUrl = TargetedMSController.getViewExperimentDetailsURL(bean.getForm().getId(), getContainer()).getLocalURIString();
%>

<div id="publishExperimentForm"></div>
<div style="margin: 30px 20px 20px 20px">
    By publishing the experiment to the selected journal, you are providing them access to copy data as well as any
    wiki pages, custom views, custom queries, lists and R reports in the following folders:
    <ul>
    <%for(Container folder: experimentFolders) { %>
        <li><%=h(folder.getPath())%></li>
    <%}%>
    </ul>

</div>

<style>
    .helpMsg
    {
        font-size: 0.75em;
    }
    .bold
    {
        font-weight: bold;
    }
</style>
<script type="text/javascript">

    var accessUrlMessage = '<span class="helpMsg">';
    accessUrlMessage += 'This is the URL that should be included in your manuscript. </span><br/>';
    accessUrlMessage += '<span class="bold">' + <%=q(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/")%> + '</span>';
    accessUrlMessage += '<span class="bold" id="span_short_access_url">' + <%=q(accessRecord.getShortURL())%> + '</span>';
    accessUrlMessage += '<span class="bold">' + <%=q(ShortURLRecord.URL_SUFFIX)%> + '</span>';
    accessUrlMessage += '</br>';
    accessUrlMessage += '<span class="helpMsg">';
    accessUrlMessage += 'You may change the text above to a more convenient, easy to remember string. ';
    accessUrlMessage += '<br/>';

    // accessUrlMessage += 'Once the journal has copied your data, this URL will redirect to the journal\'s copy of the data.';
    accessUrlMessage += '</span>';

    var copyUrlMessage = '<span class="helpMsg">This is the URL that should be provided to the journal editor for copying your data.</span></br>';
    copyUrlMessage += '<span class="bold">' + <%=q(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/")%> + '</span>';
    copyUrlMessage += '<span class="bold" id="span_short_copy_url">' + <%=q(copyRecord.getShortURL())%> + '</span>';
    copyUrlMessage += '<span class="bold">' + <%=q(ShortURLRecord.URL_SUFFIX)%> + '</span>';
    copyUrlMessage += '</br>';
    copyUrlMessage += '<span class="helpMsg">';
    copyUrlMessage += 'You may change the text above to a more convenient, easy to remember string.';
    copyUrlMessage += '</span>';

    Ext4.onReady(function(){

        var journalStore = Ext4.create('Ext.data.Store', {
            fields: ['journalId','name'],
            data:   [
                {"journalId": 0, "name": "Please select a journal..."},
                <%for(Journal j: bean.getJournalList()){%>
                {"journalId":<%=j.getId()%>,"name":<%=q(j.getName())%>},
                <%}%>
            ]
        });

        var shortAccessUrlSpan;
        var shortCopyUrlSpan;

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "publishExperimentForm",
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
                        fieldLabel: "Journal",
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
                        fieldLabel: 'Journal',
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
                    fieldLabel: 'Access URL',
                    afterBodyEl: accessUrlMessage,
                    msgTarget : 'under',
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
                    xtype: 'textfield',
                    name: 'shortCopyUrl',
                    value: <%=q(shortCopyUrl)%>,
                    fieldLabel: 'Copy URL',
                    afterBodyEl: copyUrlMessage,
                    msgTarget : 'under',
                    enableKeyEvents: true,
                    listeners: {
                        'keyup': function(field)
                        {
                            var newUrl = field.getValue();
                            // console.log("Copy URL changed to " + newUrl);
                            if(!shortCopyUrlSpan)
                            {
                                shortCopyUrlSpan = Ext4.get('span_short_copy_url');
                            }
                            shortCopyUrlSpan.dom.innerHTML = newUrl;
                        }
                    }
                }

            ],
            buttonAlign: 'left',
            buttons: [{
                text: <%=q(publishButtonText)%>,
                handler: function() {
                    var values = form.getForm().getValues();
                    console.log(values);
                    form.submit({
                        url: <%=q(submitUrl)%>,
                        method: 'POST',
                        params: values
                        });
                    }
                },
                {
                    text: 'Cancel',
                    hrefTarget: '_self',
                    href: <%=q(cancelUrl)%>
                }]
        });
    });
</script>