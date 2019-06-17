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
<%@ page import="org.labkey.targetedms.PublishTargetedMSExperimentsController" %>
<%@ page import="org.labkey.targetedms.model.Journal" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    PublishTargetedMSExperimentsController.JournalForm form = (PublishTargetedMSExperimentsController.JournalForm) __form;
    Journal journal = form.lookupJournal();
%>

Are you sure you want to delete the following Journal?
<br>
    <ul>
        <li>
            <div style="margin-bottom:10px;">
                <span style="font-weight:bold;"><%=h(journal.getName())%></span>
            </div>
        </li>
    </ul>

This action will also delete the following:
<br>
<ul>
    <li>Security group: <span style="font-weight:bold;"><%=h(SecurityManager.getGroup(journal.getLabkeyGroupId()).getName())%></span></li>
    <li>Project: <span style="font-weight:bold;"><%=h(journal.getProject().getName())%></span> and all the data it contains.</li>
</ul>
