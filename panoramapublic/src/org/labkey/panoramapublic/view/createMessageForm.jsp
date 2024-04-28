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
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicNotification" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    function submitForm()
    {
        const form = document.getElementById("post-message-form");
        let dataRegion = LABKEY.DataRegions['ExperimentAnnotationsTable'];
        let selectedRowIds = dataRegion.getChecked();
        // console.log("Selection count: " + selectedRowIds.length);
        let selected = "";
        let separator = "";
        for (let i = 0; i < selectedRowIds.length; i++)
        {
            // console.log(selectedRowIds[i]);
            selected += separator + selectedRowIds[i];
            separator = ",";
        }
        document.getElementById("selectedIdsInput").value = selected;
        form.submit();
    }
</script>
<%
    JspView<PanoramaPublicController.PanoramaPublicMessageForm> view = (JspView<PanoramaPublicController.PanoramaPublicMessageForm>) HttpView.currentView();
    var form = view.getModelBean();
%>

<labkey:errors/>

<div>
    <div style="margin:15px 0 15px 0;">
        Enter a message that will be posted to the support message threads of the selected experiments submitted to Panorama Public.
        Experiments can be selected in the "Panorama Public Experiments" grid below.
        <br/>
        Use Markdown syntax (<a href="https://markdown-it.github.io/">https://markdown-it.github.io</a>).
    </div>
    <labkey:form id="post-message-form" action="<%=urlFor(PanoramaPublicController.PostPanoramaPublicMessageAction.class)%>">
        <input type="hidden" name="dataRegionName" value="<%= h(form.getDataRegionName()) %>" />
        <input type="hidden" name="selectedIds" id="selectedIdsInput"/>
        <table class="lk-fields-table">
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Test Mode:</td>
                <td>
                    <input type="checkbox" name="testMode" selected="<%=form.getTestMode()%>" />
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Message Title (prefix):</td>
                <td>
                    <input type="text" name="messageTitle" size="60" value="<%=h(form.getMessageTitle())%>" />
                    <br>
                    e.g. "Reviewer Password".  The title of the posted message will be:
                    <br>
                    <b>Reviewer Password: https://panoramaweb.org/expt-short-url.url</b>
                    <br>
                    where "expt-short-url.url" is the short URL assigned to the experiment
                </td>
            </tr>
            <tr>
                <td class="labkey-form-label" style="text-align:center;">Message:</td>
                <td>
                    <textarea name="message" rows="8" cols="60"><%=h(form.getMessage())%></textarea>
                    <br>
                    Use Markdown syntax - <a href="https://markdown-it.github.io/">https://markdown-it.github.io</a>
                    <br/>
                    <%=h(PanoramaPublicNotification.PLACEHOLDER_SHORT_URL)%> will be replaced with the Short URL for the data.
                    <br/>
                    <%=h(PanoramaPublicNotification.PLACEHOLDER_MESSAGE_THREAD_URL)%> will be replaced with a link to the message thread.
                    <br/>
                    <%=h(PanoramaPublicNotification.PLACEHOLDER_RESPOND_TO_MESSAGE_URL)%> will be replaced with a link to respond to the message thread.
                    <br/>
                    <%=h(PanoramaPublicNotification.PLACEHOLDER_MAKE_DATA_PUBLIC_URL)%> will be replaced with a link to make the data public.
                </td>
            </tr>
            <tr><td colspan=2"><%=button("Post Message").onClick("submitForm();")%></td></tr>
        </table>
        <br>
    </labkey:form>
</div>