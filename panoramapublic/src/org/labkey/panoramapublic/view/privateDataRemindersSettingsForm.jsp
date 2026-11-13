<%
    /*
     * Copyright (c) 2008-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.security.Encryption" %>
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

    Integer stopRemindersAfter = PrivateDataReminderSettings.get().getStopRemindersAfter();
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
        window.location = LABKEY.ActionURL.buildURL("panoramapublic", "sendPrivateDataReminders.view", folderPath);
    }

    function clickSearchPublicationsLink()
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
        window.location = LABKEY.ActionURL.buildURL("panoramapublic", "searchPublications.view", folderPath);
    }

    function clickStoppedRemindersLink()
    {
        const journal = document.getElementById("journal");
        if (!journal || !journal.value)
        {
            alert("Please select a Panorama Public folder first.");
            return;
        }
        const params = {
            schemaName: "panoramapublic",
            "query.queryName": "PrivateDataNoResponse",
            "query.containerFilterName": "CurrentAndSubfolders"
        };
        // With "Stop reminders after" empty, reminders never stop. The link then adds no filter, and the grid
        // lists every private dataset with ReminderCount > 0.
        <% if (stopRemindersAfter != null) { %>
        params["query.ReminderCount~gte"] = <%=stopRemindersAfter%>;
        <% } %>
        window.location = LABKEY.ActionURL.buildURL("query", "executeQuery.view", journal.value, params);
    }

    function showValidationResult(result, message, detail)
    {
        while (result.firstChild)
        {
            result.removeChild(result.firstChild);
        }
        result.appendChild(document.createTextNode(message));

        if (!detail)
        {
            return;
        }

        result.appendChild(document.createTextNode(" "));
        const link = document.createElement("a");
        link.href = "#";
        link.textContent = "Details";
        // NCBI's response is encoded because it is third party text.
        link.addEventListener("click", function (e)
        {
            e.preventDefault();
            Ext4.Msg.alert("NCBI response", Ext4.String.htmlEncode(detail));
        });
        result.appendChild(link);
    }

    function validateNcbiApiKey()
    {
        const input = document.getElementsByName("ncbiApiKey")[0];
        const result = document.getElementById("ncbiApiKeyValidationResult");
        result.style.color = "";
        result.textContent = "Checking with NCBI...";

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("panoramapublic", "validateNcbiApiKey.api"),
            method: "POST",
            // An empty value requests a check of the saved key, which this form never displays.
            jsonData: {ncbiApiKey: input ? input.value : ""},
            success: LABKEY.Utils.getCallbackWrapper(function (response)
            {
                result.style.color = response.valid ? "green" : "red";
                showValidationResult(result, response.message, response.detail);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function ()
            {
                result.style.color = "red";
                showValidationResult(result, "Could not reach the server to validate the key.", null);
            })
        });
    }
</script>

<labkey:errors/>

<div>
    <labkey:form id="send-reminders-form" action="<%=urlFor(PrivateDataReminderSettingsAction.class)%>" method="POST">
        <table class="lk-fields-table">
            <tr>
                <td class="labkey-form-label">
                    <label for="enabled"><%=h(PrivateDataReminderSettings.PROP_ENABLE_REMINDER)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="checkbox" id="enabled" name="enabled" <%=checked(form.isEnabled())%> />
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="reminderTime"><%=h(PrivateDataReminderSettings.PROP_REMINDER_TIME)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="reminderTime" name="reminderTime" value="<%=h(form.getReminderTime())%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Reminders will be sent daily at the specified time (e.g. <%=h(PrivateDataReminderSettings.DEFAULT_REMINDER_TIME)%>), if enabled.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="delayUntilFirstReminder"><%=h(PrivateDataReminderSettings.PROP_DELAY_UNTIL_FIRST_REMINDER)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="delayUntilFirstReminder" name="delayUntilFirstReminder" value="<%=form.getDelayUntilFirstReminder()%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Number of months after data submission before sending the first reminder.
                        <br/>
                        Does not apply to a dataset with a saved publication match the submitter has not dismissed. Its reminders follow the "Publication Found" message at the reminder frequency.
                        <br/>
                        Entering 0 will send a reminder the next time the job runs.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="reminderFrequency"><%=h(PrivateDataReminderSettings.PROP_REMINDER_FREQUENCY)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="reminderFrequency" name="reminderFrequency" value="<%=form.getReminderFrequency()%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Interval in months between reminder messages after the first one.
                        <br/>
                        Entering 0 will send a reminder the next time the job runs.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="extensionLength"><%=h(PrivateDataReminderSettings.PROP_EXTENSION_LENGTH)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="extensionLength" name="extensionLength" value="<%=form.getExtensionLength()%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Number of months the private status of a dataset can be extended at the submitter's request.
                        <br/>
                        An extension cannot be requested for a dataset that has a saved publication match the submitter has not dismissed. A dataset whose publication is found during an extension still gets reminders about the publication.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="stopRemindersAfter"><%=h(PrivateDataReminderSettings.PROP_STOP_REMINDERS_AFTER)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="stopRemindersAfter" name="stopRemindersAfter" value="<%=h(form.getStopRemindersAfter())%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Number of reminders without a response, after which reminders stop. An extension request or a
                        dismissed publication suggestion starts the count again. Publication searches continue.
                        <br/>
                        Leave empty to send reminders until the submitter responds.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="enablePublicationSearch"><%=h(PrivateDataReminderSettings.PROP_ENABLE_PUBLICATION_SEARCH)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="checkbox" id="enablePublicationSearch" name="enablePublicationSearch" <%=checked(form.isEnablePublicationSearch())%> />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        When enabled, the system will search PubMed Central and PubMed for publications associated with private datasets.
                        <br/>
                        If a publication is found, submitters will be notified.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <label for="publicationSearchFrequency"><%=h(PrivateDataReminderSettings.PROP_PUBLICATION_SEARCH_FREQUENCY)%></label>
                </td>
                <td>
                    <input style="padding:0 10px 0 0;" type="text" id="publicationSearchFrequency" name="publicationSearchFrequency" value="<%=form.getPublicationSearchFrequency()%>" />
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Number of months between publication searches for a private dataset.
                        <br/>
                        After a user dismisses a publication suggestion, the submitter is notified again only if a
                        different publication is found.
                    </div>
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label">
                    <% if (Encryption.isEncryptionPassPhraseSpecified()) { %>
                    <label for="ncbiApiKey"><%=h(PrivateDataReminderSettings.PROP_NCBI_API_KEY)%></label>
                    <% } else { %>
                    <span><%=h(PrivateDataReminderSettings.PROP_NCBI_API_KEY)%></span>
                    <% } %>
                </td>
                <td>
                    <% if (!Encryption.isEncryptionPassPhraseSpecified()) { %>
                    <div style="margin: 4px 0 6px 0;"><%=h(PrivateDataReminderSettings.NCBI_API_KEY_REQUIRES_ENCRYPTION)%></div>
                    <% } else { %>
                    <input style="padding:0 10px 0 0; width: 360px;" type="password" id="ncbiApiKey" name="ncbiApiKey" autocomplete="new-password"
                           data-key-saved="<%=form.isNcbiApiKeySet()%>"
                           placeholder="<%=h(form.isNcbiApiKeySet() ? "A key is saved. Enter a new key to replace it." : "No key saved.")%>" />
                    <%=button("Validate").onClick("validateNcbiApiKey(); return false;")%>
                    <span id="ncbiApiKeyValidationResult" style="margin-left: 8px;"></span>
                    <div style="font-size: 0.9em; color: #4682B4; margin: 4px 0 6px 0;">
                        Optional. An NCBI API key raises the request rate limit for PubMed/PMC searches from 3 to 10 per second.
                        <br/>
                        Create one under Account settings at ncbi.nlm.nih.gov. The saved key is not displayed. Leaving this
                        blank keeps the key that is already saved.
                        <% if (form.isNcbiApiKeySet()) { %>
                        <br/>
                        <label><input type="checkbox" name="clearNcbiApiKey" value="true" /> Remove the saved key</label>
                        <% } %>
                    </div>
                    <% } %>
                </td>
            </tr>
            <tr><td colspan="2">
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
        <%=link("Search Publications").onClick("clickSearchPublicationsLink();").build()%>
        <%=link("Stopped Reminders").onClick("clickStoppedRemindersLink();").build()%>
    </labkey:form>
    <hr/>

</div>