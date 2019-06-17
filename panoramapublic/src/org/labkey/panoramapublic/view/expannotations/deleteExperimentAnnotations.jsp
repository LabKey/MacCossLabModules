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
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.targetedms.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.targetedms.query.ExperimentAnnotationsManager" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    TargetedMSController.SelectedExperimentIds deleteForm = (TargetedMSController.SelectedExperimentIds) __form;
    int[] experimentAnnotationIds = deleteForm.getIds();
%>
<p>Are you sure you want to delete the following
    <%if(experimentAnnotationIds.length > 1){%>experiments<%} else {%> experiment<%}%>?<br>
    <ul>
    <%for(int experimentAnnotationId: experimentAnnotationIds) {%>
        <li>
            <%
                ExperimentAnnotations annotations = ExperimentAnnotationsManager.get(experimentAnnotationId);
                if(annotations == null)  continue;
            %>
            <div style="margin-bottom:10px;">
                <span style="font-weight:bold;"><%=h(annotations.getTitle())%></span> in folder <%=h(annotations.getContainer().getPath())%>
            </div>
        </li>
    <%}%>
    </ul>
</p>