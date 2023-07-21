<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
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
    JspView<PanoramaPublicController.PublicationDetailsBean> me = (JspView<PanoramaPublicController.PublicationDetailsBean>) HttpView.currentView();
    var bean = me.getModelBean();
    var form = bean.getForm();
%>

<div id="publishDataDetails"></div>
<div id="publishDataForm"></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var items = [];
        if (<%=!bean.isPublic()%>) {
            let html = 'Data at ' + <%=qh(bean.getAccessUrl())%> + ' will be made public.';
            html += <%= bean.getLicense() != null ? qh("It will be available under the " + bean.getLicense().getDisplayName() + ".") : HtmlString.EMPTY_STRING %>;
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