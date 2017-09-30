<%@ page import="org.springframework.validation.BindingResult" %>
<%@ page import="org.labkey.skylinetoolsstore.SkylineToolsStoreController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Object errorAttribute = request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "form");
    if (errorAttribute != null)
    {
%><p class="labkey-error"><%= h(errorAttribute.toString()) %></p><%
    }

    final String suppTarget = (String)request.getAttribute(BindingResult.MODEL_KEY_PREFIX + "supptarget");
%>

<form action="<%= h(urlFor(SkylineToolsStoreController.InsertSupplementAction.class)) %>" enctype="multipart/form-data" method="post">
    <p>
        Browse to the supplementary file you would like to upload.<br/><br/>
        <input type="file" size="50" name="suppFile" /><br /><br />
        <input type="hidden" name="supptarget" value="<%= h(suppTarget) %>" />
        <input type="submit" value="Upload Supplementary File" />
    </p>
</form>

<br />
<%= PageFlowUtil.generateBackButton() %>
