<%
/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
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
    JspView<PanoramaPublicController.PublicationDetailsBean> me = HttpView.currentView();
    var bean = me.getModelBean();
    var form = bean.getForm();
%>

<div id="publishDataDetails"></div>
<div id="publishDataForm"></div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    Ext4.onReady(function(){

        var items = [];
        if (<%=!bean.isPublic()%>) {
            let html = 'Data at ' + <%=qh(bean.getAccessUrl())%> + ' will be made public.';
            html += <%= qh(bean.getLicense() != null ? " It will be available under the " + bean.getLicense().getDisplayName() + " license." : "") %>;
            items.push({xtype: 'component', style: 'margin: 5px 0 5px 0', html: html});
        }
        if (<%=form.hasPubmedId()%>) {
            items.push({xtype: 'component', html: '<b>PubMed ID:</b> ' + <%=qh(form.getPubmedId())%>});
        }
        if (<%=form.hasLinkAndCitation()%>) {
            items.push({xtype: 'component', html: '<b>Link:</b> ' + <%=qh(form.getLink())%>});
            items.push({xtype: 'component', html: '<b>Citation:</b> <i>' + <%=q(form.getHtmlCitation())%> + '</i>'});
        }
        else {
            items.push({xtype: 'component', html: 'Publication details were not entered.'});
        }
        Ext4.create('Ext.Panel', {
            renderTo: 'publishDataDetails',
            border: false,
            frame: false,
            margin: '0 0 10 0',
            items: items
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "publishDataForm",
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
                    name: 'id',
                    value: <%=form.getId()%>
                },
                {
                    xtype: 'hidden',
                    name: 'pubmedId',
                    value: <%=q(form.getPubmedId())%>
                },
                {
                    xtype: 'hidden',
                    name: 'link',
                    value: <%=q(form.getLink())%>
                },
                {
                    xtype: 'hidden',
                    name: 'citation',
                    value: <%=q(form.getCitation())%>
                },
                {
                    xtype: 'hidden',
                    name: 'unpublished',
                    value: <%=form.isUnpublished()%>
                },
                {
                    xtype: 'hidden',
                    name: 'confirmed',
                    value: true
                }
            ],
            buttonAlign: 'left',
            buttons: [
                {
                    text: "OK",
                    cls: 'labkey-button primary',
                    handler: function(button) {
                        button.setDisabled(true);
                        form.submit({
                            url: <%=q(urlFor(PanoramaPublicController.MakePublicAction.class))%>,
                            method: 'POST'
                        });
                    }
                },
                {
                    text: 'Cancel',
                    cls: 'labkey-button',
                    hrefTarget: '_self',
                    href: <%=q(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()))%>
                }]
        });
    });
</script>