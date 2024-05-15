<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
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

<!-- Display the title and permanent link of the data they are making public -->
<div style="margin-bottom:20px;">
    <table>
        <tr><td class="labkey-form-label" style="width:150px;">Title: </td><td style="padding:3px;"><%=h(bean.getExperimentTitle())%></td></tr>
        <tr><td class="labkey-form-label" style="width:150px;">Permanent Link: </td><td style="padding:3px;"><%=link(bean.getAccessUrl(), bean.getAccessUrl()).clearClasses().build()%></td></tr>
    </table>
</div>
<div id="publishDataForm"></div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    Ext4.onReady(function(){

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
                    xtype: 'textfield',
                    name: 'pubmedId',
                    fieldLabel: "PubMed ID",
                    value: <%=q(form.getPubmedId())%>
                },
                {
                    xtype: 'component',
                    hidden: <%=bean.isPeerReviewed()%>,
                    html: '<div style="color:red;">Publication link and citation are not required if a PubMed ID is provided</div>'
                },
                {
                    xtype: 'label',
                    hidden: <%=bean.isPeerReviewed()%>,
                    text: '------------------------------------------------- OR -------------------------------------------------',
                    style: {'text-align': 'center', 'margin': '10px 0 10px 0'}
                },
                {
                    xtype: 'textfield',
                    hidden: <%=bean.isPeerReviewed()%>,
                    name: 'link',
                    fieldLabel: "Publication Link",
                    value: <%=q(form.getLink())%>
                },
                {
                    xtype: 'textarea',
                    hidden: <%=bean.isPeerReviewed()%>,
                    name: 'citation',
                    fieldLabel: "Citation",
                    value: <%=q(form.getCitation())%>
                },
                {
                    xtype: 'label',
                    hidden: <%=bean.isPublic()%>,
                    text: '------------------------------------------------- OR -------------------------------------------------',
                    style: {'text-align': 'center', 'margin': '10px 0 10px 0'}
                },
                {
                    xtype: 'checkbox',
                    hidden: <%=bean.isPublic()%>,
                    name: 'unpublished',
                    fieldLabel: "Unpublished",
                    checked: <%=form.isUnpublished()%>,
                    boxLabel: 'Check this box if a manuscript for the data is not yet published, or if the manuscript has been ' +
                            'accepted for publication but the publication link is not yet available',
                },
                {
                    xtype: 'hidden',
                    name: 'detailsConfirmed',
                    value: <%=form.isConfirmed()%>,
                }
            ],
            buttonAlign: 'left',
            buttons: [{
                text: "Continue",
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