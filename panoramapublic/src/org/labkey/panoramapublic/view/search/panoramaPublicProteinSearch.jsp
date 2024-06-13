<%
/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.protein.ProteinService" %>
<%@ page import="org.labkey.api.protein.search.ProteinSearchForm" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ProteinSearchForm> me = (JspView<ProteinSearchForm>) HttpView.currentView();
    ProteinSearchForm bean = me.getModelBean();
%>
<labkey:form action="<%=ProteinService.get().getProteinSearchUrl(getContainer())%>">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Protein name *<%= helpPopup("Protein name", "Required to search for proteins. You may use the name as specified by the FASTA file, or an annotation, such as a gene name, that has been loaded from an annotations file. You may comma separate multiple names.") %></td>
            <td nowrap><input size="20" type="text" id="identifierInput" name="identifier" value="<%= h(bean.getIdentifier()) %>"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Exact matches only<%= helpPopup("Exact matches only", "If checked, the search will only find proteins with an exact name match. If not checked, proteins that start with the name entered will also match, but the search may be significantly slower.") %></td>
            <td nowrap><input type="checkbox" name="exactMatch" <%=checked(bean.isExactMatch())%>/></td>
        </tr>
        <!-- Always include subfolders in search -->
        <input type="hidden" name="includeSubfolders" value="<%=bean.isIncludeSubfolders()%>"/>
        <!-- Do not show protein groups -->
        <input type="hidden" name="showProteinGroups" value="<%=bean.isShowProteinGroups()%>"/>
        <input type="hidden" name="showMatchingProteins" value="<%=bean.isShowMatchingProteins()%>"/>
        <tr>
            <td colspan="2" style="padding-top: 10px;">
                <labkey:button text="Search" />
            </td>
        </tr>
    </table>
</labkey:form>

