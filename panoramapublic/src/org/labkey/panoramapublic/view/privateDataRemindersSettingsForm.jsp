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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.message.PrivateDataReminderSettings" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PrivateDataReminderSettingsForm" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PanoramaPublicAdminViewAction" %>
<%@ page import="org.labkey.panoramapublic.query.JournalManager" %>
<%@ page import="org.labkey.panoramapublic.model.Journal" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController.PrivateDataReminderSettingsAction" %>
<%@ page import="org.labkey.panoramapublic.message.PrivateDataReminderSettings" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<PrivateDataReminderSettingsForm> view = HttpView.currentView();
    var form = view.getModelBean();
    ActionURL panoramaPublicAdminUrl = urlFor(PanoramaPublicAdminViewAction.class);

    List<Journal> journals = JournalManager.getJournals();

%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    function clickSendRemindersLink()
    {
        const journal = document.getElementById("journal");
        if (!journal)
        {
            alert("Cannot get journal selector element.");
            return;
        }
        const folderPath = journal.value;
        if (!folderPath)
        {
            alert("Please select a Panorama Public folder first.");
            return;
        }
        //console.log("Selected folder path: " + folderPath);
        window.location = LABKEY.ActionURL.buildURL("panoramapublic", "sendPrivateDataReminders.view", folderPath);
    }
</script>

<labkey:errors/>

<div>
    <labkey:form id="send-reminders-form" action="<%=urlFor(PrivateDataReminderSettingsAction.class)%>" method="POST">
        <table class="lk-fields-table">
            <tr>
                <td class="labkey-form-label">
                    <span><%=h(PrivateDataReminderSettings.PROP_ENABLE_REMINDER)%></span>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="checkbox" name="enabled" <%=checked(form.isEnabled())%> />
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <span><%=h(PrivateDataReminderSettings.PROP_DELAY_UNTIL_FIRST_REMINDER)%></span>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" name="delayUntilFirstReminder" value="<%=form.getDelayUntilFirstReminder()%>" />
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <span><%=h(PrivateDataReminderSettings.PROP_REMINDER_FREQUENCY)%></span>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" name="reminderFrequency" value="<%=form.getReminderFrequency()%>" />
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <span><%=h(PrivateDataReminderSettings.PROP_EXTENSION_MONTHS)%></span>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" name="extensionLength" value="<%=form.getExtensionLength()%>" />
                </td>
            </tr>
            <tr><td colspan=2">
                <%=button("Save").submit(true)%>
                <%=button("Cancel").href(panoramaPublicAdminUrl)%>
            </td></tr>
        </table>
        <hr/>
        <label for="journal">Choose Panorama Public Folder</label>:
        <select id="journal" name="journal">
            <%
                boolean isFirst = true;
                for (Journal journal : journals) {
            %>
            <option value="<%=h(journal.getProject().getPath())%>" <%=selected(isFirst)%>>
                <%=h(journal.getName())%>
            </option>
            <%
                    isFirst = false;
                }
            %>
        </select>
        <%=link("Send Reminders Now").onClick("clickSendRemindersLink();").build()%>
    </labkey:form>
    <hr/>

</div>