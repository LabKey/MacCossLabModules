<%
    /*
     * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<PanoramaPublicController.PrivateDataSendReminderForm> view = HttpView.currentView();
    var form = view.getModelBean();
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    function submitForm()
    {
        const form = document.getElementById("send-reminders-form");
        let dataRegion = LABKEY.DataRegions['ExperimentAnnotationsTable'];
        let selectedRowIds = dataRegion.getChecked();
        console.log("Selection count: " + selectedRowIds.length);
        let selected = "";
        let separator = "";
        for (let i = 0; i < selectedRowIds.length; i++)
        {
            console.log(selectedRowIds[i]);
            selected += separator + selectedRowIds[i];
            separator = ",";
        }
        document.getElementById("selectedIdsInput").value = selected;
        form.submit();
    }
</script>

<labkey:errors/>

<div>
    <div style="margin:15px 0 15px 0;">
        A reminder message will be sent to the submitters of the selected experiments.
    </div>
    <labkey:form id="send-reminders-form" action="<%=urlFor(PanoramaPublicController.SendPrivateDataRemindersAction.class)%>" method="POST">
        <input type="hidden" name="dataRegionName" value="<%= h(form.getDataRegionName()) %>" />
        <input type="hidden" name="selectedIds" id="selectedIdsInput"/>
        <table class="lk-fields-table">
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Test Mode:</td>
                <td>
                    <input type="checkbox" name="testMode" <%=checked(form.getTestMode())%> />
                </td>
            </tr>
            <tr><td colspan=2"><%=button("Post Reminders").onClick("submitForm();")%></td></tr>
        </table>
        <br>
    </labkey:form>
</div>