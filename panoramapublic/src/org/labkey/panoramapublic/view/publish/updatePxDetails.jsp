<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
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
    PanoramaPublicController.PxDetailsForm form = ((JspView<PanoramaPublicController.PxDetailsForm>) HttpView.currentView()).getModelBean();
    ExperimentAnnotations expAnnot = form.lookupExperiment();
    ActionURL cancelUrl = form.getReturnActionURL(PanoramaPublicController.getViewExperimentDetailsURL(expAnnot.getId(), expAnnot.getContainer()));
%>
<div style="margin-top:15px;margin-bottom:15px;color:red;bont-weight:bold" id="updateDetailsForm">
    This form should be used only if:
    <ul>
        <li>A ProteomeXchange ID from the test database was accidentally assigned to the experiment and has to be changed</li>
        <li>User submitted an "incomplete" submission before we were setup to handle spectrum library data and we now need to upgrade the submission to a "complete" submission</li>
    </ul>
</div>
<div style="margin-top:15px;" id="updateDetailsForm"></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "updateDetailsForm",
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
                    xtype: 'textfield',
                    fieldLabel: 'ProteomeXchange ID',
                    name: 'pxId',
                    width: 650,
                    value: <%=q(form.getPxId())%>
                },
                {
                    xtype: 'checkbox',
                    fieldLabel: "Incomplete ProteomeXchange Submission",
                    checked: <%=form.isIncompletePxSubmission()%>,
                    name: 'incompletePxSubmission',
                    boxLabel: 'This box will be checked if the user requested an "incomplete" ProteomeXchange submission. Admin can override if needed.'
                },
            ],
            buttonAlign: 'left',
            buttons: [
                {
                    text: 'Update',
                    cls: 'labkey-button primary',
                    handler: function() {
                        var values = form.getForm().getValues();
                        form.submit({
                            url: <%=q(new ActionURL(PanoramaPublicController.UpdatePxDetailsAction.class, getContainer()).getLocalURIString())%>,
                            method: 'POST',
                            params: values
                        });
                    },
                    margin: '20 10 0 10'
                },
                {
                    text: 'Cancel',
                    cls: 'labkey-button',
                    hrefTarget: '_self',
                    href: <%=q(cancelUrl.getLocalURIString())%>
                }
            ]
        });
    });
</script>
