<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>

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
    }
%>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
</head>

<style>

    * {
        padding: 0;
        outline: 0 none;
        box-sizing: border-box;
    }

    .active-tabs {
        position: relative;
        margin: 10px auto;
    }

    .search-panel-btn {
        background: lightgray;
        color: black;
        cursor: pointer;
        display: block;
        float: left;
        font-family: "Roboto",Arial,Helvetica,sans-serif;
        font-weight: normal;
        font-size: 13px;
        height: 25px;
        margin-right: 1px;
        text-align: center;
        width: 150px;
        opacity: 0.8;
        transition: all 0.4s;
        border-top-right-radius: 8px;
        border-top-left-radius: 8px;
    }

    .search-panel-btn:hover {
        transform: translateY(-5px);
        opacity: 1;
    }

    .active-tabs input:checked + label {
        background: whitesmoke;
        opacity: 1;
        transform: translateY(-5px);
        box-shadow: 1px 0 0 0 rgba(0,0,0,0.3);
        color: black;
    }

    .tabs-container {
        width: 100%;
        position: relative;
        float: left;
        top: -5px;
        background: #fff;
    }

    .tab-1 ,
    .tab-2 ,
    .tab-3 {
        position: absolute;
        top: 0;
        left: 0;
        opacity: 0;
        visibility: hidden;
        transition: all 0.4s;
        border: 1px solid lightgray;
    }

    .search-panel-btn-1:checked ~ .tabs-container .tab-1 ,
    .search-panel-btn-2:checked ~ .tabs-container .tab-2 ,
    .search-panel-btn-3:checked ~ .tabs-container .tab-3 {
        position: relative;
        visibility: visible;
        top: 0;
        left: 0;
        opacity: 1;
    }
</style>
<html>
<body>

<form>
<div class="active-tabs">
    <input style="visibility: hidden" type="radio" name="active_tabs" id="expSearchPanel" class="search-panel-btn-1" checked >
    <label for="expSearchPanel" class="search-panel-btn" >Experiment Search</label>

    <input style="visibility: hidden" type="radio" name="active_tabs" id="proteinSearchPanel" class="search-panel-btn-2">
    <label for="proteinSearchPanel" class="search-panel-btn" >Protein Search</label>

    <input style="visibility: hidden" type="radio" name="active_tabs" id="peptideSearchPanel" class="search-panel-btn-3">
    <label for="peptideSearchPanel" class="search-panel-btn" >Peptide Search</label>

    <div class="tabs-container">
        <div class="tab-1">
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
                    <td nowrap><input class="bootstrap-tagsinput" size="20" type="text" id="Authors" name="Authors" value=""/></td>

                    <td style="width: 5px"></td>
                    <td><input class="bootstrap-tagsinput" size="20" type="text" id="Title" name="Title" value=""/></td>

                    <td style="width: 5px"></td>
                    <td nowrap>
                        <div id="input-picker-div-organism" class="scrollable-dropdown-menu">
                            <input class="tags" size="20" type="text" id="Organism" name="Organism" placeholder="Enter Organism" value=""/>
                        </div>
                    </td>

                    <td style="width: 5px"></td>
                    <td nowrap>
                        <div id="input-picker-div-instrument" class="scrollable-dropdown-menu">
                            <input class="tags" size="20" type="text" id="Instrument" name="Instrument" placeholder="Enter Instrument" value=""/>
                        </div>
                    </td>
                </tr>
                <tr>

                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
        <div class="tab-2">
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

                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
        <div class="tab-3">
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

                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
    </div>
    <div>
        <button id="search-button-id" type="submit" class="labkey-button" onclick=handleRendering(true)>Search</button>
    </div>
    <div id="search-indicator" style="visibility: hidden">
        <p><i class="fa fa-spinner fa-pulse"></i> Search is running, results pending...</p>
    </div>

</div>
</form>

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
        console.log("active tab = ", context.searchTab);
        activeTab = context.searchTab ? context.searchTab : expSearchPanelItemId;
        document.getElementById(activeTab).checked= true;
        handleRendering(false);
    });

    $ (function() {
        let instrUrl = LABKEY.ActionURL.buildURL('PanoramaPublic', 'completeInstrument.api');
        initAutoComplete(instrUrl, "input-picker-div-instrument", true);

        let organismUrl = LABKEY.ActionURL.buildURL('PanoramaPublic', 'completeOrganism.api');
        initAutoComplete(organismUrl, "input-picker-div-organism", false);

        document.getElementById(expSearchPanelItemId).addEventListener("click", function() {
            activeTab = expSearchPanelItemId;
            updateUrlFilters(activeTab);
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

    let handleRendering = function (onClick) {

        console.log("activeTab = ", activeTab);

        let expAnnotationFilters = [];
        let proteinParameters = {};
        let peptideParameters = {};

        // render experiment list webpart
        // add filters in qwp and in the url for back button
        if (onClick) {

            updateUrlFilters(activeTab);

            if (activeTab === expSearchPanelItemId) {
                let author = document.getElementById(authorsItemId).value;
                let title = document.getElementById(titleItemId).value;
                let organism = document.getElementById(organismItemId).value;
                let instrument = document.getElementById(instrumentItemId).value;
                console.log(author + ", " + title + ", " + organism + ", " + instrument);

                if (author) {
                    expAnnotationFilters.push(LABKEY.Filter.create(authorsItemId, author, LABKEY.Filter.Types.CONTAINS));
                    updateUrlFilters(null, authorsItemId, author);
                }
                if (title) {
                    expAnnotationFilters.push(LABKEY.Filter.create(titleItemId, title, LABKEY.Filter.Types.CONTAINS));
                    updateUrlFilters(null, titleItemId, title);
                }
                if (organism) {
                    expAnnotationFilters.push(LABKEY.Filter.create(organismItemId, organism, LABKEY.Filter.Types.CONTAINS));
                    updateUrlFilters(null, organismItemId, organism);
                }
                if (instrument) {
                    expAnnotationFilters.push(LABKEY.Filter.create(instrumentItemId, instrument, LABKEY.Filter.Types.CONTAINS));
                    updateUrlFilters(null, instrumentItemId, instrument);
                }
            }
            else if (activeTab === proteinSearchPanelItemId) {
                let protein = document.getElementById(proteinNameItemId).value;
                let exactProteinMatch = document.getElementById(exactProteinMatchesItemId).checked;
                console.log(protein + ", " + exactProteinMatch);

                if (protein) {
                    proteinParameters[proteinNameItemId] = protein;
                    updateUrlFilters(null, proteinNameItemId, protein);
                }
                if (exactProteinMatch) {
                    proteinParameters[exactMatch] = exactProteinMatch;
                    updateUrlFilters(null, exactProteinMatchesItemId, exactProteinMatch);
                }
            }
            else if (activeTab === peptideSearchPanelItemId) {
                let peptide = document.getElementById(peptideSequenceItemId).value;
                let exactPeptideMatch = document.getElementById(exactPeptideMatchesItemId).checked;

                if (peptide) {
                    peptideParameters[peptideSequenceItemId] = peptide;
                    updateUrlFilters(null, peptideSequenceItemId, peptide);
                }
                if (exactPeptideMatch) {
                    peptideParameters[exactMatch] = exactPeptideMatch;
                    updateUrlFilters(null, exactPeptideMatchesItemId, exactPeptideMatch);
                }
                console.log(peptide + ", " + exactPeptideMatch);
            }
        }
        // getFiltersFromUrl and add to the filters
        else {
            let context = getFiltersFromUrl();
            if (context[authorsItemId]) {
                expAnnotationFilters.push(LABKEY.Filter.create(authorsItemId, context[authorsItemId], LABKEY.Filter.Types.CONTAINS));
                document.getElementById(authorsItemId).value = context[authorsItemId];
            }
            if (context[titleItemId]) {
                expAnnotationFilters.push(LABKEY.Filter.create(titleItemId, context[titleItemId], LABKEY.Filter.Types.CONTAINS));
                document.getElementById(titleItemId).value = context[titleItemId];
            }
            if (context[organismItemId]) {
                expAnnotationFilters.push(LABKEY.Filter.create(organismItemId, context[organismItemId], LABKEY.Filter.Types.CONTAINS));
                document.getElementById(organismItemId).value = context[organismItemId];
            }
            if (context[instrumentItemId]) {
                expAnnotationFilters.push(LABKEY.Filter.create(instrumentItemId, context[instrumentItemId], LABKEY.Filter.Types.CONTAINS));
                document.getElementById(instrumentItemId).value = context[instrumentItemId];
            }
            if (context[proteinNameItemId]) {
                proteinParameters[proteinNameItemId] =  context[proteinNameItemId];
                document.getElementById(proteinNameItemId).value = context[proteinNameItemId];
            }
            if (context[exactProteinMatchesItemId]) {
                proteinParameters[exactMatch] =  context[exactProteinMatchesItemId];
                document.getElementById(exactProteinMatchesItemId).value = context[exactProteinMatchesItemId];
            }
            if (context[peptideSequenceItemId]) {
                peptideParameters[peptideSequenceItemId] =  context[peptideSequenceItemId];
                document.getElementById(peptideSequenceItemId).value = context[peptideSequenceItemId];
            }
            if (context[exactPeptideMatchesItemId]) {
                peptideParameters[exactMatch] =  context[exactPeptideMatchesItemId];
                document.getElementById(exactPeptideMatchesItemId).value = context[exactPeptideMatchesItemId];
            }
        }

        // render search qwps if search is clicked or page is reloaded (user hit back) and there are url parameters
        if (onClick || expAnnotationFilters.length > 0 ||
                proteinParameters[proteinNameItemId] ||
                peptideParameters[peptideSequenceItemId]
        ) {

            document.getElementById("search-indicator").style.visibility = "visible";

            if (expAnnotationFilters.length > 0) {
                let wp = new LABKEY.QueryWebPart({
                    renderTo: 'experiment_list_wp',
                    title: 'TargetedMS Experiment List',
                    schemaName: 'panoramapublic',
                    viewName: 'search',
                    queryName: 'ExperimentAnnotations',
                    showFilterDescription: false,
                    containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                    filters: expAnnotationFilters,
                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                    }
                });
                wp.render();
            }
            else if (proteinParameters[proteinNameItemId]) {
                let wp = new LABKEY.QueryWebPart({
                    renderTo: 'experiment_list_wp',
                    title: 'The searched protein appeared in the following experiments',
                    schemaName: 'panoramapublic',
                    queryName: 'proteinSearch',
                    showFilterDescription: false,
                    containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                    parameters: proteinParameters,
                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                    }
                });
                wp.render();
            }
            else if (peptideParameters[peptideSequenceItemId]) {
                let wp = new LABKEY.QueryWebPart({
                    renderTo: 'experiment_list_wp',
                    title: 'The searched peptide appeared in the following experiments',
                    schemaName: 'panoramapublic',
                    queryName: 'peptideSearch',
                    showFilterDescription: false,
                    containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                    parameters: peptideParameters,
                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                    }
                });
                wp.render();
            }
        }
    };

    function getFiltersFromUrl () {
        let context = {};

        if (document.location.hash) {
            var token = document.location.hash.split('#');
            token = token[1].split('&');

            for (let i = 0; i < token.length; i++) {
                var t = token[i].split(':');
                t[0] = decodeURIComponent(t[0]);
                if (t.length > 1) {
                    t[1] = decodeURIComponent(t[1]);
                }
                switch (t[0]) {
                    case 'searchTab':
                        context.searchTab = t[1];
                        break;
                    case authorsItemId:
                        context[authorsItemId] = t[1];
                        break;
                    case titleItemId:
                        context[titleItemId] = t[1];
                        break;
                    case organismItemId:
                        context[organismItemId] = t[1];
                        break;
                    case instrumentItemId:
                        context[instrumentItemId] = t[1];
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
        if (tabId) {
            this.activeTab = tabId;
            if (window.location.href.includes('#searchTab')) {
                clearHistory();
            }
            addSelectedTabToUrl(tabId);
        }
        if (settingName) {
            if (window.location.href.includes(settingName)) {
                clearHistory();
                addSelectedTabToUrl(this.activeTab);
            }
            if (window.location.href.includes('#')) {
                window.location.href = window.location.href + '&' + settingName + ':' + elementId;
            }
            else {
                window.location.href = window.location.href + '#' + settingName + ':' + elementId;
            }
        }
    }

    function clearHistory () {
        history.pushState("", document.title, window.location.pathname
                + window.location.search);
    }

    function addSelectedTabToUrl (tabId) {
        window.location.href = window.location.href + '#searchTab:' + tabId;
    }

    function checkAndFillValuesFromUrl (itemId, comp) {
        let context = getFiltersFromUrl();
        if (context[itemId]) {
            comp.setValue(context[itemId]);
        }
    }
</script>
</body>

<div id="experiment_list_wp"></div>
<div id="instrument_render_id"></div>
<div id="organism_render_id"></div>

</html>

