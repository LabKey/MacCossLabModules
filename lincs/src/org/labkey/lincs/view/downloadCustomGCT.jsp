<%
    /*
     * Copyright (c) 2014-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.lincs.LincsController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors/>


<%
    JspView<LincsController.CustomGCTForm> jspView = (JspView<LincsController.CustomGCTForm>) HttpView.currentView();
    LincsController.CustomGCTForm bean = jspView.getModelBean();
    List<LincsController.SelectedAnnotation> annotations = bean.getSelectedAnnotationValues();

    ActionURL downloadGctUrl = new ActionURL(LincsController.DownloadCustomGCTReportAction.class, getContainer());
    downloadGctUrl.addParameter("fileName", bean.getCustomGctFile());
%>

<div style="margin:20px 10px 20px 10px">
<span style="font-weight:bold;"><a href="<%=h(downloadGctUrl)%>">[Download GCT]</a></span>
<div style="color:red; margin-bottom:20px;">NOTE: The file will be deleted from the server after it is downloaded.</div>
</div>

<div style="margin:20px 10px 20px 10px">
    <div style="font-weight:bold;">Selected options:</div>
    <p>Experiment type: <%=bean.getExperimentType()%></p>
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