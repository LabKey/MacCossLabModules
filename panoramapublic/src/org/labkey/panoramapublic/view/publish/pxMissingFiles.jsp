<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.model.validation.Status" %>
<%@ page import="org.json.old.JSONObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("PanoramaPublic/css/pxValidation.css");
        dependencies.add("PanoramaPublic/js/pxValidation.js");
    }
%>
<labkey:errors/>

<%
    var view = (JspView<PanoramaPublicController.PxValidationStatusBean>) HttpView.currentView();
    var bean = view.getModelBean();
    ExperimentAnnotations experimentAnnotations = bean.getExpAnnotations();
    int experimentAnnotationsId = experimentAnnotations.getId();
    boolean includeSubfolders = experimentAnnotations.isIncludeSubfolders();
    Status validationStatus = bean.getPxValidationStatus();
    JSONObject json = validationStatus != null ? validationStatus.toJSON() : new JSONObject();
%>

<div id="missingFilesDiv"/>

<script type="text/javascript">

    Ext4.onReady(function() {

        const json = <%=json%>;

        let items = [];
        if (json['skylineDocuments'] && json['skylineDocuments'].length > 0) {
            items.push(
                    {
                        xtype: 'pxv-skydocs-grid',
                        experimentAnnotationsId: <%=experimentAnnotationsId%>,
                        json: json,
                        showUploadButton: false,
                        expandNodes: true,
                        padding: '10 0 0 0',
            });
        }

        if (json['spectrumLibraries'] && json['spectrumLibraries'].length > 0) {
            items.push(
                    {
                        xtype: 'pxv-speclibs-grid',
                        experimentAnnotationsId: <%=experimentAnnotationsId%>,
                        includeSubfolders: <%=includeSubfolders%>,
                        json: json,
                        showUploadButton: false,
                        expandNodes: true,
                        showLibInfo: true,
                        padding: '10 0 0 0',
            });
        }

        Ext4.create('Ext.panel.Panel', {
            bodyStyle: {border: '0px', padding: '0px'},
            renderTo: 'missingFilesDiv',
            items: items
        });
    });

</script>