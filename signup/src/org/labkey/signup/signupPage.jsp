<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.signup.SignUpController" %>
<%@ page import="org.labkey.signup.SignUpManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    SignUpController.SignupForm form = (SignUpController.SignupForm)HttpView.currentModel();
    ActionURL url = new ActionURL(SignUpController.BeginAction.class, getContainer());
%>

<!-- Display errors here -->
<labkey:errors/>

<form action="<%=h(url.getLocalURIString())%>" method=post>
    <labkey:csrf/>
    <table>
        <tr>
            <td class="labkey-form-label"><label for="firstName">First Name</label> *</td>
            <td nowrap><input size="20" type="text" id="firstName" name="firstName" value="<%=h(form.getFirstName())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="lastName">Last Name</label> *</td>
            <td nowrap><input size="20" type="text" id="lastName" name="lastName" value="<%=h(form.getLastName())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="organization">Organization</label> *</td>
            <td nowrap><input size="20" type="text" id="organization" name="organization" value="<%=h(form.getOrganization())%>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="email">Email</label> *</td>
            <td nowrap><input size="20" type="text" id="email" name="email" value="<%=h(form.getEmail())%>"/></td>
        </tr>
        <tr>
            <td colspan="2"><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>
