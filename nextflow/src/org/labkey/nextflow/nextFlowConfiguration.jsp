<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.ButtonBuilder" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.nextflow.NextFlowConfiguration" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    NextFlowConfiguration form = (NextFlowConfiguration) HttpView.currentModel();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<labkey:form method="POST">
<labkey:errors />
<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label"><label for="nextFlowConfigFilePath">NextFlow Config File Path</label></td>
        <td><labkey:input type="text" name="nextFlowConfigFilePath" id="nextFlowConfigFilePath" size="64" value="<%=h(form.getNextFlowConfigFilePath())%>" /></td>
    </tr>
    <tr>
        <td class="labkey-form-label"><label for="accountNameInput">AWS Account Name</label></td>
        <td><labkey:input type="text" name="accountName" id="accountNameInput" size="64" value="<%=h(form.getAccountName())%>" /></td>
    </tr>
    <tr>
        <td class="labkey-form-label"><label for="identityInput">AWS Identity</label></td>
        <td><labkey:input type="text" name="identity" id="identityInput" size="64" value="<%= h(form.getIdentity()) %>" /></td>
    </tr>
    <tr>
        <td class="labkey-form-label"><label for="credentialInput">AWS Credential</label></td>
        <td><labkey:input type="password" name="credential" id="credentialInput" size="64" placeholder='<%= form.getCredential() != null ? "value already set, overwrite to replace" : "" %>' /></td>
    </tr>
    <tr>
        <td class="labkey-form-label"><label for="s3BucketPathInput">AWS S3 Bucket Path</label></td>
        <td><labkey:input type="text" name="s3BucketPath" id="s3BucketPathInput" size="64" value="<%=h(form.getS3BucketPath())%>" /></td>
    </tr>
    <tr>
        <td class="labkey-form-label"><label for="apiKeyInput">API Key (optional)</label></td>
        <td><labkey:input type="password" name="apiKey" id="apiKeyInput" size="64" placeholder='<%= form.getApiKey() != null ? "value already set, overwrite to replace" : "" %>' /></td>
    </tr>
</table>
    <%= new ButtonBuilder("Save").submit(true).primary(true).enabled(hasAdminOpsPerms) %>
    <%= new ButtonBuilder("Delete").onClick("deleteConfig()").enabled(hasAdminOpsPerms) %>
    <%= new ButtonBuilder("Cancel").href(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
</labkey:form>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    function deleteConfig() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('nextflow', 'DeleteNextFlowConfiguration.api'),
            method: 'POST',
            success: function (response) {
                window.location = <%= q(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
            },
            failure: function (response) {
                alert('Failed to delete configuration');
            }
        });
    }
</script>
