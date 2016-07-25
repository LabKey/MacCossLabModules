<%
    /*
     * Copyright (c) 2015-2016 LabKey Corporation
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
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page import="java.util.List" %>
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
    JspView<LincsController.CustomGCTForm> jspView = (JspView<LincsController.CustomGCTForm>) HttpView.currentView();
    LincsController.CustomGCTForm form = jspView.getModelBean();
    List<LincsController.SelectedAnnotation> annotations = form.getSelectedAnnotationValues();
    LincsController.GctBean gctBean = form.getCustomGctBean();

    ActionURL downloadGctUrl = new ActionURL(LincsController.DownloadCustomGCTReportAction.class, getContainer());
    String fileName = gctBean.getGctFile().getName();
    downloadGctUrl.addParameter("fileName", fileName);
%>

<script type="text/javascript">

    LABKEY.requiresCss("/lincs/lincs.css");
    LABKEY.requiresScript("/lincs/lincs.js");

    // Initialize
    Ext4.onReady(init);
    function init()
    {
        var container = LABKEY.ActionURL.getContainer();
        var assayType = container.indexOf("P100") !== -1 ? "P100" : "GCP";
        console.log("Initializing for <%=fileName%>");
        var morpheusUrl = externalHeapmapViewerLink(container, '<%=fileName%>', "morpheusLink", assayType);
        console.log("Morpheus URL: " + morpheusUrl);
    }

</script>



<div style="margin:20px 10px 20px 10px">
<span style="font-weight:bold;"><a href="<%=h(downloadGctUrl)%>">[Download GCT]</a></span>
<span style="font-weight:bold;" id="morpheusLink"></span>
<div style="color:red; margin-bottom:20px;">NOTE: The file will be deleted from the server after it is downloaded.</div>

<div style="margin:20px 10px 20px 10px">
    <div style="font-weight:bold;">Selected options:</div>
    <p>Experiment type: <%=form.getExperimentType()%></p>
    <p>
        <%for(LincsController.SelectedAnnotation annotation: annotations) { %>
            <%=annotation.getDisplayName()%>:
            <%  String comma = "";
                for(String value: annotation.getValues()) { %>
                <%=comma%><%=value%>
                <%comma = ", ";%>
            <%}%>
            </br>
        <%}%>
    </p>
</div>

<div style="margin:20px 10px 20px 10px">
    <div style="font-weight:bold;">GCT output:</div>
    <div># Probes: <%=gctBean.getProbeCount()%></div>
    <div># Replicates: <%=gctBean.getReplicateCount()%></div>
    <div># Probe Annotations: <%=gctBean.getProbeAnnotationCount()%></div>
    <div># Replicate Annotations: <%=gctBean.getReplicateAnnotationCount()%></div>
</div>