<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishTargetedMSExperimentsController.CreateJournalGroupForm> view = (JspView<PublishTargetedMSExperimentsController.CreateJournalGroupForm>) HttpView.currentView();
    PublishTargetedMSExperimentsController.CreateJournalGroupForm form = view.getModelBean();
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>


<div style="width: 700px">
    <p>
        Creating a new journal will create a project for the journal. It will also create a new security group for the journal.
        All members of the journal should be added to this group.
        Users will be able to select a journal by name when publishing an experiment.
    </p>
</div>

<labkey:errors/>

<form method="post"><labkey:csrf/>
    <table>
        <tr>
            <td class="labkey-form-label"><label for="journalNameTextField">Journal Name</label><%= helpPopup("Journal Name", "The name of the journal")%></td>
            <td><input name="journalName" id="journalNameTextField" value="<%=h(form.getJournalName())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="groupNameTextField">Group Name</label><%= helpPopup("Group Name", "The name of the security group that will be created for the journal. All journal members should be added to this group.")%></td>
            <td><input name="groupName" id="groupNameTextField" value="<%=h(form.getGroupName())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="projectNameTextField">Project Name</label><%= helpPopup("Project Name", "The name of the project that will be created for the journal.")%></td>
            <td><input name="projectName" id="projectNameTextField" value="<%=h(form.getProjectName())%>"/></td>
        </tr>
        <tr>
            <td></td>
            <td><%=button("Submit").submit(true) %></td>
        </tr>
    </table>
</form>
