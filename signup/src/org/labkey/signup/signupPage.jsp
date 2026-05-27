<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.signup.SignUpController.BeginAction" %>
<%@ page import="org.labkey.signup.SignUpController.SignupForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    SignupForm form = (SignupForm)HttpView.currentModel();
    String contextPath = request.getContextPath();
%>

<labkey:errors/>

<labkey:form method="post" action="<%=urlFor(BeginAction.class)%>" layout="horizontal" autoComplete="off" style="max-width:600px;">
    <labkey:input name="firstName" id="firstName" label="First Name" isRequired="true" size="50" value="<%=form.getFirstName()%>"/>
    <labkey:input name="lastName" id="lastName" label="Last Name" isRequired="true" size="50" value="<%=form.getLastName()%>"/>
    <labkey:input name="organization" id="organization" label="Organization" isRequired="true" size="50" value="<%=form.getOrganization()%>"/>
    <labkey:input name="email" id="email" label="Email" isRequired="true" size="50" value="<%=form.getEmail()%>"/>
    <labkey:input name="emailConfirm" id="emailConfirm" label="Confirm Email" isRequired="true" size="50" value="<%=form.getEmailConfirm()%>"/>

    <%-- Verification: standalone full-width block --%>
    <div style="margin-top:20px;"><strong>Verification</strong></div>
    <p style="margin:8px 0 4px 0;">Please enter the characters shown below (case-insensitive).</p>
    <p style="margin:0 0 8px 0;"><a id="kaptchaReload" href="#">Get a new image.</a></p>
    <img id="kaptchaImg" src="<%=h(contextPath)%>/kaptcha.jpg" alt="Captcha" width="200" height="50" style="border: 1px solid #ccc; display:block; margin-bottom:6px;"/>
    <input type="text" id="kaptchaText" name="kaptchaText" aria-label="Verification code" style="width:200px;"/>

    <div style="margin-top:20px; clear:both;">
        <button type="submit" class="btn btn-default labkey-button">Register</button>
    </div>
</labkey:form>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    document.getElementById("kaptchaReload").addEventListener("click", function(e) {
        e.preventDefault();
        var img = document.getElementById("kaptchaImg");
        img.src = img.src.split("?")[0] + "?ts=" + new Date().getTime();
    });
</script>
