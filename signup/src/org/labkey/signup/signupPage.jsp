<%
/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.signup.SignUpController.BeginAction" %>
<%@ page import="org.labkey.signup.SignUpController.SignupForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    SignupForm form = (SignupForm)HttpView.currentModel();
    ActionURL url = urlFor(BeginAction.class);
%>

<!-- Display errors here -->
<labkey:errors/>

<form action="<%=h(url)%>" method=post>
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
