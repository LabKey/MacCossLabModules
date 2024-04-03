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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager" %>
<%@ page import="static org.apache.fop.render.pdf.extensions.PDFDictionaryType.Catalog" %>
<%@ page import="org.labkey.panoramapublic.query.CatalogEntryManager.CatalogEntryType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("PanoramaPublic/css/slideshow.css");
        dependencies.add("PanoramaPublic/js/slideshow.js");
    }
%>

<!-- The HTML, CSS and JavaScript to display the slideshow have been copied from the Wiki page on PanoramaWeb. -->

<labkey:errors/>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    Ext4.onReady(function() {

        let maxEntries = LABKEY.ActionURL.getParameter("maxEntries");
        if (!maxEntries) maxEntries = 3;

        let entryType = LABKEY.ActionURL.getParameter("entryType");
        if (!entryType) entryType = <%=qh(CatalogEntryType.Approved.toString())%>;

        const maxEntriesEl = document.getElementById("input_max_entries");
        if (maxEntriesEl)
        {
            maxEntriesEl.value = maxEntries;
        }

        const entryTypeElId = "entry_type_" + entryType;
        const entryTypeEl = document.getElementById(entryTypeElId);
        if (entryTypeEl)
        {
            entryTypeEl.checked = true;
        }

        if (appendSlidesContainer("slideshowPlaceholder")) {
            initSlides(maxEntries, entryType);
        }

        window.onresize = function() {
            setDescSize(false);
        }
    });

    function viewSlideshow()
    {
        let maxEntries = document.getElementById("input_max_entries").value;
        if (!maxEntries) maxEntries = 3;

        let entryType;
        const radioEls = document.getElementsByName("input_entry_type");
        for (let i = 0; i < radioEls.length; i++)
        {
            if (radioEls[i].checked)
            {
                entryType = radioEls[i].value;
                break;
            }
        }
        if (!entryType) entryType = <%=qh(CatalogEntryType.Approved.toString())%>;

        const queryParams = { "maxEntries": maxEntries, "entryType": entryType };
        const url =LABKEY.ActionURL.buildURL('panoramapublic', 'viewSlideshow.view', null, queryParams);
        document.location.href = url;
    }

</script>
<div>
    <table class="lk-fields-table">
        <tbody>
        <tr>
            <td class="labkey-form-label" style="text-align:center;">Max. entries:</td>
            <td><input id="input_max_entries" type="text"/></td>
        </tr>
        <tr>
            <td class="labkey-form-label" style="text-align:center;">InputType:</td>
            <td>
                <% for (CatalogEntryType entryType: CatalogEntryType.values()) { %>
                <span style="margin-right: 15px;"><input type="radio" id="entry_type_<%=h(entryType.toString())%>" name="input_entry_type" value="<%=h(entryType.toString())%>">
                <label for="entry_type_<%=h(entryType.toString())%>"><%=h(entryType.toString())%></label>
                </span>
                <% } %>
            </td>
        </tr>
        </tbody>
    </table>
    <%=button("View").style("margin:15px 0 20px 0").onClick("viewSlideshow()").build()%>
</div>
<div id="slideshowPlaceholder"></div>