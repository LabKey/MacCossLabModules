<%
    /*
     * Copyright (c) 2022 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("PanoramaPublic/js/ExpAnnotAutoComplete.js");
        dependencies.add("PanoramaPublic/js/bootstrap-tagsinput.min.js");
        dependencies.add("PanoramaPublic/js/typeahead.bundle.min.js");
        dependencies.add("/PanoramaPublic/css/bootstrap-tagsinput.css");
        dependencies.add("/PanoramaPublic/css/typeahead-examples.css");
        dependencies.add("/PanoramaPublic/css/PanoramaWebSearch.css");
    }
%>

<div class="active-tabs">
    <input style="visibility: hidden" type="radio" name="active_tabs" id="expSearchPanel" class="search-panel-btn-1" checked >
    <label for="expSearchPanel" class="search-panel-btn" >Experiment Search</label>

    <input style="visibility: hidden" type="radio" name="active_tabs" id="proteinSearchPanel" class="search-panel-btn-2">
    <label for="proteinSearchPanel" class="search-panel-btn" >Protein Search</label>

    <input style="visibility: hidden" type="radio" name="active_tabs" id="peptideSearchPanel" class="search-panel-btn-3">
    <label for="peptideSearchPanel" class="search-panel-btn" >Peptide Search</label>

    <div class="tabs-container">
        <div class="tab-1 search-tab">
            <table class="lk-fields-table">
                <tr style="height: 10px"></tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td>Author:</td>
                    <td style="width: 5px"></td>
                    <td>Title:</td>
                    <td style="width: 5px"></td>
                    <td>Organism:</td>
                    <td style="width: 5px"></td>
                    <td>Instrument:</td>
                </tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td nowrap><input class="tags bootstrap-tagsinput" size="20" type="text" id="Authors" name="Authors" value=""/></td>

                    <td style="width: 5px"></td>
                    <td><input class="tags bootstrap-tagsinput" size="20" type="text" id="Title" name="Title" value=""/></td>

                    <td style="width: 5px"></td>
                    <td nowrap>
                        <div id="input-picker-div-organism" class="scrollable-dropdown-menu">
                            <input class="tags organism" size="20" type="text" id="Organism" name="Organism" placeholder="Enter Organism" value=""/>
                        </div>
                    </td>

                    <td style="width: 5px"></td>
                    <td nowrap>
                        <div id="input-picker-div-instrument" class="scrollable-dropdown-menu">

                            <%-- Placeholder text is needed here otherwise the input size becomes variable once a value is selected from auto-complete dropdown;
                            however, hiding the placeholder text by setting 'input::placeholder' styling to be transparent in PanoramaWebSearch.css.
                            Also, placeholder text is 20 characters long (with added spaces) to keep it consistent with other inputs above.
                            --%>
                            <input class="tags instrument" type="text" id="Instrument" name="Instrument" placeholder="  Enter Instrument  " value=""/>
                        </div>
                    </td>
                    <td style="width: 25px"></td>
                    <td>
                        <button id="clear-all-button-id-experiment" class="clear-all-button" onclick="clearInputFieldsAndResetURL('experiment');">Clear All</button>
                    </td>
                </tr>
                <tr>

                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
        <div class="tab-2 search-tab">
            <table class="lk-fields-table">
                <tr style="height: 10px"></tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td>Protein:<%=helpPopup("Protein", "Required to search for proteins. You may use the name as specified by the FASTA file, or an annotation, such as a gene name, that has been loaded from an annotations file.")%></td>
                </tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td nowrap><input class="bootstrap-tagsinput" size="20" type="text" id="proteinLabel" name="proteinLabel" value="" /></td>

                    <td style="width: 10px"></td>

                    <td>Exact Matches Only:<%=helpPopup("Exact Matches Only", "If checked, the search will only find proteins with an exact name match. If not checked, proteins that contain the name entered will also match, but the search may be significantly slower.")%></td>
                    <td style="padding-top: 0.75%; padding-left: 5px"><labkey:checkbox id="exactProteinMatches" name="exactProteinMatches" value=""/></td>

                    <td style="width: 25px"></td>
                    <td>
                        <button id="clear-all-button-id-protein" class="clear-all-button" onclick="clearInputFieldsAndResetURL('protein');">Clear All</button>
                    </td>
                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
        <div class="tab-3 search-tab">
            <table class="lk-fields-table">
                <tr style="height: 10px"></tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td>Peptide Sequence:<%=helpPopup("Peptide Sequence", "Enter the peptide sequence to find.")%></td>
                </tr>
                <tr>
                    <td style="width: 5px"></td>
                    <td nowrap><input class="bootstrap-tagsinput" size="20" type="text" id="peptideSequence" name="peptideSequence" value=""/></td>

                    <td style="width: 10px"></td>

                    <td>Exact Matches Only:<%=helpPopup("Exact Matches Only", "If checked, the search will match the peptides exactly; if unchecked, it will match any peptide that contain the specified sequence.")%></td>
                    <td style="padding-top: 0.75%; padding-left: 5px"><labkey:checkbox id="exactPeptideMatches" name="exactPeptideMatches" value=""/></td>

                    <td style="width: 25px"></td>
                    <td>
                        <button id="clear-all-button-id-peptide" class="clear-all-button" onclick="clearInputFieldsAndResetURL('peptide');">Clear All</button>
                    </td>
                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
    </div>
    <div>
        <button id="search-button-id" class="labkey-button" onclick="handleRendering('true');">Search</button>
    </div>
    <div id="search-criteria-id"/>

</div>

<script>
    const expSearchPanelItemId = 'expSearchPanel';
    const authorsItemId = 'Authors';
    const titleItemId = 'Title';
    const organismItemId = 'Organism';
    const instrumentItemId = 'Instrument';
    const exactMatch = 'exactMatch';

    const proteinSearchPanelItemId = 'proteinSearchPanel';
    const proteinNameItemId = 'proteinLabel';
    const exactProteinMatchesItemId = 'exactProteinMatches';

    const peptideSearchPanelItemId = 'peptideSearchPanel';
    const peptideSequenceItemId = 'peptideSequence';
    const exactPeptideMatchesItemId = 'exactPeptideMatches';

    let activeTab = undefined;

    $(document).ready(function() {
        let context = getFiltersFromUrl();
        activeTab = context.searchTab ? context.searchTab : expSearchPanelItemId;
        document.getElementById(activeTab).checked= true;
        handleRendering(false);
    });

    $ (function() {
        let instrUrl = LABKEY.ActionURL.buildURL('PanoramaPublic', 'completeInstrument.api');
        initAutoComplete(instrUrl, "input-picker-div-instrument", true, true /* allow free input */);

        let organismUrl = LABKEY.ActionURL.buildURL('PanoramaPublic', 'completeOrganism.api');
        initAutoComplete(organismUrl, "input-picker-div-organism", false, true /* allow free input */);

        document.getElementById(expSearchPanelItemId).addEventListener("click", function() {
            activeTab = expSearchPanelItemId;
            addSelectedTabToUrl(activeTab)
        });

        document.getElementById(proteinSearchPanelItemId).addEventListener("click", function() {
            activeTab = proteinSearchPanelItemId;
            updateUrlFilters(activeTab);
        });

        document.getElementById(peptideSearchPanelItemId).addEventListener("click", function() {
            activeTab = peptideSearchPanelItemId;
            updateUrlFilters(activeTab);
        });
    });

    //submit form via Enter key
    $('.search-tab').keypress((e) => {
        if (e.which === 13) {
            handleRendering(true);
        }
    });

    let clearInputFromExperimentTab = function () {
        document.getElementById(authorsItemId).value = "";
        document.getElementById(titleItemId).value = "";

        $('.organism').tagsinput('removeAll');
        $('.instrument').tagsinput('removeAll');
    };

    let resetUrl = function () {
        let currentURL = window.location.href;
        let newURL = currentURL.substring(0, currentURL.indexOf("#"));
        location.replace(newURL);
    };

    let clearInputFromProteinTab = function () {
        document.getElementById(proteinNameItemId).value = "";
        document.getElementById(exactProteinMatchesItemId).checked = false;
    };

    let clearInputFromPeptideTab = function () {
        document.getElementById(peptideSequenceItemId).value = ""
        document.getElementById(exactPeptideMatchesItemId).checked = false;
    };

    let createFilter = function(itemId, filterText) {
        let arr = filterText.split(',');
        let filterArr = [];
        for (let i = 0; i < arr.length; i++)
        {
            filterArr.push(arr[i].trim());
        }
        if (filterArr.length > 1)
        {
            return LABKEY.Filter.create(itemId, filterArr.join(';'), LABKEY.Filter.Types.CONTAINS_ONE_OF);
        }
        else
        {
            return LABKEY.Filter.create(itemId, filterText, LABKEY.Filter.Types.CONTAINS);
        }
    };

    let clearInputFieldsAndResetURL = function(searchTabName) {
        switch (searchTabName) {
            case "experiment":
                clearInputFromExperimentTab();
                break;
            case "peptide":
                clearInputFromPeptideTab();
                break;
            case "protein":
                clearInputFromProteinTab();
                break;
            default:
                break;
        }
        resetUrl();
    };

    let handleRendering = function (onTabClick) {

        let expAnnotationFilters = [];
        let proteinParameters = {};
        let peptideParameters = {};
        let searchCriteriaString = "";

        // render experiment list webpart
        // add filters in qwp and in the url for back button
        if (onTabClick) {

            updateUrlFilters(activeTab);

            if (activeTab === expSearchPanelItemId) {

                addSelectedTabToUrl(activeTab);
                clearInputFromProteinTab();
                clearInputFromPeptideTab();

                let author = document.getElementById(authorsItemId).value;
                let title = document.getElementById(titleItemId).value;
                let organism = document.getElementById(organismItemId).value;
                let instrument = document.getElementById(instrumentItemId).value;

                let expSearchParams = "";
                if (author) {
                    expAnnotationFilters.push(createFilter(authorsItemId, author));
                    expSearchParams += "Targeted MS Experiment List." + "authors~containsoneof" + "=" + encodeURIComponent(author) + "&";
                }
                if (title) {
                    expAnnotationFilters.push(createFilter(titleItemId, title));
                    expSearchParams += "Targeted MS Experiment List." + "title~containsoneof" + "=" + encodeURIComponent(title) + "&";
                }
                if (organism) {
                    expAnnotationFilters.push(createFilter(organismItemId, organism));
                    expSearchParams += "Targeted MS Experiment List." + "organism~containsoneof" + "=" + encodeURIComponent(organism.replaceAll(",", ";")) + "&";
                }
                if (instrument) {
                    expAnnotationFilters.push(createFilter(instrumentItemId, instrument));
                    expSearchParams += "Targeted MS Experiment List." + "instrument~containsoneof" + "=" + encodeURIComponent(instrument.replaceAll(",", ";"));
                }
                if (expSearchParams !== "") {
                    location.replace(window.location.href + "?" + expSearchParams);
                }
                else {
                    resetUrl();
                }
            }
            else if (activeTab === proteinSearchPanelItemId) {

                clearInputFromExperimentTab();
                clearInputFromPeptideTab();

                searchCriteriaString = "";

                let protein = document.getElementById(proteinNameItemId).value;
                let exactProteinMatch = document.getElementById(exactProteinMatchesItemId).checked;

                proteinParameters[proteinNameItemId] = protein;
                updateUrlFilters(null, proteinNameItemId, protein);
                searchCriteriaString += "'" + protein + "'";

                if (exactProteinMatch) {
                    proteinParameters[exactMatch] = exactProteinMatch;
                    updateUrlFilters(null, exactProteinMatchesItemId, exactProteinMatch);
                    searchCriteriaString += " with Exact Match ";
                }
            }
            else if (activeTab === peptideSearchPanelItemId) {

                clearInputFromExperimentTab();
                clearInputFromProteinTab();

                searchCriteriaString = "";

                let peptide = document.getElementById(peptideSequenceItemId).value;
                let exactPeptideMatch = document.getElementById(exactPeptideMatchesItemId).checked;

                peptideParameters[peptideSequenceItemId] = peptide;
                updateUrlFilters(null, peptideSequenceItemId, peptide);
                searchCriteriaString += "'" + peptide + "'";

                if (exactPeptideMatch) {
                    peptideParameters[exactMatch] = exactPeptideMatch;
                    updateUrlFilters(null, exactPeptideMatchesItemId, exactPeptideMatch);
                    searchCriteriaString += " with Exact Match ";
                }
            }
        }
        // getFiltersFromUrl and add to the filters
        else {
            let context = getFiltersFromUrl();
            searchCriteriaString = "";

            if (activeTab === expSearchPanelItemId) {
                parseUrlQueryParams()
            }
            if (context[proteinNameItemId]) {
                proteinParameters[proteinNameItemId] =  context[proteinNameItemId];
                document.getElementById(proteinNameItemId).value = context[proteinNameItemId];
                searchCriteriaString += "'" + context[proteinNameItemId] + "'";
            }
            if (context[exactProteinMatchesItemId]) {
                proteinParameters[exactMatch] =  context[exactProteinMatchesItemId];
                context[exactProteinMatchesItemId] === "true" ? (document.getElementById(exactProteinMatchesItemId).checked = true) : (document.getElementById(exactProteinMatchesItemId).checked = false);
                searchCriteriaString += " with Exact Match ";
            }
            if (context[peptideSequenceItemId]) {
                peptideParameters[peptideSequenceItemId] =  context[peptideSequenceItemId];
                document.getElementById(peptideSequenceItemId).value = context[peptideSequenceItemId];
                searchCriteriaString += "'" + context[peptideSequenceItemId] + "'";
            }
            if (context[exactPeptideMatchesItemId]) {
                peptideParameters[exactMatch] =  context[exactPeptideMatchesItemId];
                document.getElementById(exactPeptideMatchesItemId).checked = context[exactPeptideMatchesItemId] === "true";
                searchCriteriaString += " with Exact Match ";
            }
        }

        // render search qwps if search is clicked or page is reloaded (user hit back) and there are url parameters
        // also, handle empty inputs
        if (onTabClick || (expAnnotationFilters.length > 0 || activeTab === expSearchPanelItemId) ||
                (proteinParameters[proteinNameItemId] || activeTab === proteinSearchPanelItemId) ||
                (peptideParameters[peptideSequenceItemId] || activeTab === peptideSearchPanelItemId)) {

            if (expAnnotationFilters.length > 0 || activeTab === expSearchPanelItemId) {

                LABKEY.Portal.getWebParts({
                    containerPath: this.containerPath,
                    pageId: 'DefaultDashboard',
                    success: function (wp) {
                        let expWebpart = wp.body.filter(webpart => webpart.name === "Targeted MS Experiment List");
                        if (expWebpart.length === 1) {
                            let wp = new LABKEY.QueryWebPart({
                                renderTo: 'webpart_'+ expWebpart[0].webPartId,
                                title: 'Panorama Public Experiments',
                                schemaName: 'panoramapublic',
                                queryName: 'ExperimentAnnotations',
                                containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                                filters: expAnnotationFilters,
                                showRecordSelectors: false,
                                showDeleteButton: false,
                                showExportButtons: false,//this needs to be set to false otherwise setting selectRecordSelector to false still shows the checkbox column
                                showDetailsColumn: false,
                                dataRegionName: "Targeted MS Experiment List",
                                success: function () {
                                }
                            });
                        }
                    }
                });
            }
            else if (proteinParameters[proteinNameItemId] || activeTab === proteinSearchPanelItemId) {

                LABKEY.Portal.getWebParts({
                    containerPath: this.containerPath,
                    pageId: 'DefaultDashboard',
                    success: function (wp) {
                        let expWebpart = wp.body.filter(webpart => webpart.name === "Targeted MS Experiment List");
                        if (expWebpart.length === 1) {
                            let wp = new LABKEY.QueryWebPart({
                                renderTo: 'webpart_'+ expWebpart[0].webPartId,
                                title: 'Panorama Public Experiments',
                                schemaName: 'panoramapublic',
                                queryName: 'proteinSearch',
                                showFilterDescription: true,
                                containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                                parameters: proteinParameters,
                                success: function () {
                                    //Make the filter description user-friendly (ex: from "?proteinLabel = xyz" to using "Protein = xyz")
                                    let els = document.getElementsByClassName('lk-region-context-action');
                                    if (els && els.length > 0) {
                                        for (let i = 0; i < els.length; i++) {
                                            let txt = els[i].textContent;
                                            if (txt.startsWith('proteinLabel')) {
                                                txt = txt.replace('proteinLabel', 'Protein');
                                            }
                                            else if (txt.startsWith('exactMatch')) {
                                                txt = txt.replace('exactMatch', 'Exact Match');
                                            }
                                            document.getElementsByClassName('lk-region-context-action')[i].textContent = txt;
                                        }
                                    }
                                }
                            });
                        }
                    }
                });
            }
            else if (peptideParameters[peptideSequenceItemId] || activeTab === peptideSearchPanelItemId) {
                LABKEY.Portal.getWebParts({
                    containerPath: this.containerPath,
                    pageId: 'DefaultDashboard',
                    success: function (wp) {
                        let expWebpart = wp.body.filter(webpart => webpart.name === "Targeted MS Experiment List");
                        if (expWebpart.length === 1) {
                            let wp = new LABKEY.QueryWebPart({
                                renderTo: 'webpart_'+ expWebpart[0].webPartId,
                                title: 'Panorama Public Experiments',
                                schemaName: 'panoramapublic',
                                queryName: 'peptideSearch',
                                showFilterDescription: true,
                                containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                                parameters: peptideParameters,
                                success: function () {
                                    //Make the filter description user-friendly (ex: from "?peptideSequence = rrr" to using "Peptide Sequence = rrr")
                                    let els = document.getElementsByClassName('lk-region-context-action');
                                    if (els && els.length > 0) {
                                        for (let i = 0; i < els.length; i++) {
                                            let txt = els[i].textContent;
                                            if (txt.startsWith('peptideSequence')) {
                                                txt = txt.replace('peptideSequence', 'Peptide Sequence');
                                            }
                                            else if (txt.startsWith('exactMatch')) {
                                                txt = txt.replace('exactMatch', 'Exact Match');
                                            }
                                            document.getElementsByClassName('lk-region-context-action')[i].textContent = txt;
                                        }
                                    }
                                }
                            });
                        }
                    }
                });
            }
        }
    };

    function parseUrlQueryParams() {
        if (document.location.hash.includes('?')) {
            var query = document.location.hash.split('?')[1];
            query.split("&").forEach(function (part) {
                var item = part.split("=");
                var name = decodeURIComponent(item[0]);
                var value = decodeURIComponent(item[1]);
                if (name.endsWith("List.authors~containsoneof")) {
                    document.getElementById(authorsItemId).value = value;
                }
                if (name.endsWith("List.title~containsoneof")) {
                    document.getElementById(titleItemId).value = value;
                }
                if (name.endsWith("List.organism~containsoneof")) {
                    document.getElementById(organismItemId).value = value;
                }
                if (name.endsWith("List.instrument~containsoneof")) {
                    document.getElementById(instrumentItemId).value = value;
                }
            });
        }
    }

    function getFiltersFromUrl () {
        let context = {};

        if (document.location.hash) {
            let token = document.location.hash.split('#');
            if (token[1].includes(expSearchPanelItemId)) {
                token = token[1].split('?');
            }
            else {
                token = token[1].split('&');
            }

            for (let i = 0; i < token.length; i++) {
                let t = token[i].split(':');
                t[0] = decodeURIComponent(t[0]);
                if (t.length > 1) {
                    t[1] = decodeURIComponent(t[1]);
                }
                switch (t[0]) {
                    case 'searchTab':
                        context.searchTab = t[1];
                        break;
                    case proteinNameItemId:
                        context[proteinNameItemId] = t[1];
                        break;
                    case exactProteinMatchesItemId:
                        context[exactProteinMatchesItemId] = t[1];
                        break;
                    case peptideSequenceItemId:
                        context[peptideSequenceItemId] = t[1];
                        break;
                    case exactPeptideMatchesItemId:
                        context[exactPeptideMatchesItemId] = t[1];
                        break;
                    default:
                        context[t[0]] = t[1];
                }
            }
        }
        return context;
    }

    function updateUrlFilters (tabId, settingName, elementId) {
        if (tabId && tabId !== expSearchPanelItemId) {
            this.activeTab = tabId;
            addSelectedTabToUrl(tabId);
        }
        if (settingName && activeTab !== expSearchPanelItemId) {
            if (window.location.href.includes(settingName)) {
                addSelectedTabToUrl(this.activeTab);
            }
            if (window.location.href.includes('#')) {
                location.replace(window.location.href + '&' + settingName + ':' + elementId);
            }
            else {
                location.replace(window.location.href + '#' + settingName + ':' + elementId);
            }
        }
    }

    function addSelectedTabToUrl (tabId) {
        location.replace(window.location.pathname + '#searchTab:' + tabId);
    }

</script>