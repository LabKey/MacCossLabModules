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
                    <td style="width: 5px"></td>
                    <td>
                        <button id="clear-all-button-id-experiment" class="clear-all-button" onclick=clearInputFields("experiment")>Clear All</button>
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

                    <td style="width: 5px"></td>
                    <td>
                        <button id="clear-all-button-id-protein" class="clear-all-button" onclick=clearInputFields("protein")>Clear All</button>
                    </td>
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

                    <td style="width: 5px"></td>
                    <td>
                        <button id="clear-all-button-id-peptide" class="clear-all-button" onclick=clearInputFields("peptide")>Clear All</button>
                    </td>
                </tr>
                <tr style="height: 10px"></tr>
            </table>
        </div>
    </div>
    <div>
        <button id="search-button-id" class="labkey-button" onclick=handleRendering(true)>Search</button>
    </div>
    <div id="search-indicator" style="visibility: hidden;padding-left: 50%">
        <p><i class="fa fa-spinner fa-pulse"></i> Search is running, results pending...</p>
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

    //submit form via Enter key
    $('.tab-1').keypress((e) => {
        if (e.which === 13) {
            handleRendering(true);
        }
    });

    $('.tab-2').keypress((e) => {
        if (e.which === 13) {
            handleRendering(true);
        }
    });

    $('.tab-3').keypress((e) => {
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

    let clearInputFields = function(searchTabName) {
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

                clearInputFromProteinTab();
                clearInputFromPeptideTab();

                let author = document.getElementById(authorsItemId).value;
                let title = document.getElementById(titleItemId).value;
                let organism = document.getElementById(organismItemId).value;
                let instrument = document.getElementById(instrumentItemId).value;
                searchCriteriaString = "";

                if (author) {
                    expAnnotationFilters.push(createFilter(authorsItemId, author));
                    searchCriteriaString += " Author: " + author + ";";
                    updateUrlFilters(null, authorsItemId, author);
                }
                if (title) {
                    expAnnotationFilters.push(createFilter(titleItemId, title));
                    searchCriteriaString += " Title: " + title + ";";
                    updateUrlFilters(null, titleItemId, title);
                }
                if (organism) {
                    expAnnotationFilters.push(createFilter(organismItemId, organism));
                    searchCriteriaString += " Organism: " + organism + ";";
                    updateUrlFilters(null, organismItemId, organism);
                }
                if (instrument) {
                    expAnnotationFilters.push(createFilter(instrumentItemId, instrument));
                    searchCriteriaString += " Instrument: " + instrument + ";"
                    updateUrlFilters(null, instrumentItemId, instrument);
                }
            }
            else if (activeTab === proteinSearchPanelItemId) {

                clearInputFromExperimentTab();
                clearInputFromPeptideTab();

                searchCriteriaString = "";

                let protein = document.getElementById(proteinNameItemId).value;
                let exactProteinMatch = document.getElementById(exactProteinMatchesItemId).checked;

                if (protein) {
                    proteinParameters[proteinNameItemId] = protein;
                    updateUrlFilters(null, proteinNameItemId, protein);
                    searchCriteriaString += " Protein: " + protein + ";";
                }
                if (exactProteinMatch) {
                    proteinParameters[exactMatch] = exactProteinMatch;
                    updateUrlFilters(null, exactProteinMatchesItemId, exactProteinMatch);
                    searchCriteriaString += " Exact Matches Only: " + proteinParameters[exactMatch] + ";";
                }
            }
            else if (activeTab === peptideSearchPanelItemId) {

                clearInputFromExperimentTab();
                clearInputFromProteinTab();

                searchCriteriaString = "";

                let peptide = document.getElementById(peptideSequenceItemId).value;
                let exactPeptideMatch = document.getElementById(exactPeptideMatchesItemId).checked;

                if (peptide) {
                    peptideParameters[peptideSequenceItemId] = peptide;
                    updateUrlFilters(null, peptideSequenceItemId, peptide);
                    searchCriteriaString += " Peptide: " + peptide + ";";
                }
                if (exactPeptideMatch) {
                    peptideParameters[exactMatch] = exactPeptideMatch;
                    updateUrlFilters(null, exactPeptideMatchesItemId, exactPeptideMatch);
                    searchCriteriaString += " Exact Matches Only: " + peptideParameters[exactMatch] + ";";
                }
            }
        }
        // getFiltersFromUrl and add to the filters
        else {
            let context = getFiltersFromUrl();
            searchCriteriaString = "";

            if (context[authorsItemId]) {
                expAnnotationFilters.push(createFilter(authorsItemId, context[authorsItemId]));
                document.getElementById(authorsItemId).value = context[authorsItemId];
                searchCriteriaString += " Author: " + context[authorsItemId] + ";";
            }
            if (context[titleItemId]) {
                expAnnotationFilters.push(createFilter(titleItemId, context[titleItemId]));
                document.getElementById(titleItemId).value = context[titleItemId];
                searchCriteriaString += " Title: " + context[titleItemId] + ";";
            }
            if (context[organismItemId]) {
                expAnnotationFilters.push(createFilter(organismItemId, context[organismItemId]));
                document.getElementById(organismItemId).value = context[organismItemId];
                searchCriteriaString += " Organism: " + context[organismItemId] + ";";
            }
            if (context[instrumentItemId]) {
                expAnnotationFilters.push(createFilter(instrumentItemId, context[instrumentItemId]));
                document.getElementById(instrumentItemId).value = context[instrumentItemId];
                searchCriteriaString += " Instrument: " + context[instrumentItemId] + ";";
            }
            if (context[proteinNameItemId]) {
                proteinParameters[proteinNameItemId] =  context[proteinNameItemId];
                document.getElementById(proteinNameItemId).value = context[proteinNameItemId];
                searchCriteriaString += " Protein: " + context[proteinNameItemId] + ";";
            }
            if (context[exactProteinMatchesItemId]) {
                proteinParameters[exactMatch] =  context[exactProteinMatchesItemId];
                context[exactProteinMatchesItemId] === "true" ? (document.getElementById(exactProteinMatchesItemId).checked = true) : (document.getElementById(exactProteinMatchesItemId).checked = false);
                searchCriteriaString += " Exact Matches Only: " + proteinParameters[exactMatch] + ";";
            }
            if (context[peptideSequenceItemId]) {
                peptideParameters[peptideSequenceItemId] =  context[peptideSequenceItemId];
                document.getElementById(peptideSequenceItemId).value = context[peptideSequenceItemId];
                searchCriteriaString += " Peptide: " + context[peptideSequenceItemId] + ";";
            }
            if (context[exactPeptideMatchesItemId]) {
                peptideParameters[exactMatch] =  context[exactPeptideMatchesItemId];
                context[exactPeptideMatchesItemId] === "true" ? (document.getElementById(exactPeptideMatchesItemId).checked = true): (document.getElementById(exactPeptideMatchesItemId).checked = false);
                searchCriteriaString += " Exact Matches Only: " + context[exactPeptideMatchesItemId] + ";";
            }
        }

        // render search qwps if search is clicked or page is reloaded (user hit back) and there are url parameters
        if (onTabClick || expAnnotationFilters.length > 0 ||
                proteinParameters[proteinNameItemId] ||
                peptideParameters[peptideSequenceItemId]) {

            document.getElementById("search-indicator").style.visibility = "visible";

            if (expAnnotationFilters.length > 0) {
                let wp = new LABKEY.QueryWebPart({
                    renderTo: 'experiment_list_wp',
                    title: 'TargetedMS Experiment List',
                    schemaName: 'panoramapublic',
                    queryName: 'ExperimentAnnotations',
                    showFilterDescription: false,
                    containerFilter: LABKEY.Query.containerFilter.currentAndSubfolders,
                    filters: expAnnotationFilters,
                    frame: 'none',
                    showRecordSelectors: false,
                    showDeleteButton: false,
                    showExportButtons: false,//this needs to be set to false otherwise setting selectRecordSelector to false still shows the checkbox column
                    showDetailsColumn: false,

                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                        $('#search-criteria-id').empty();
                        $('#search-criteria-id').append("<b>Experiment Search criteria: </b>");
                        $('#search-criteria-id').append(searchCriteriaString);

                        // remove 'Targeted MS Experiment List' webpart if it is present on the page
                        LABKEY.Portal.getWebParts({
                            containerPath: this.containerPath,
                            pageId: 'DefaultDashboard',
                            success: function (wp) {
                                let expWebpart = wp.body.filter(webpart => webpart.name === "Targeted MS Experiment List");
                                if (expWebpart.length === 1) {
                                    LABKEY.Portal.removeWebPart({
                                        updateDOM: true,
                                        webPartId: expWebpart[0].webPartId
                                    });

                                    // setTimout() is necessary since LABKEY.QueryWebPart.success() gets called twice -
                                    // once by LABKEY.DataRegion.create, and another by via LABKEY.DataRegion.refresh;
                                    // hence, we are in this method twice - and trying to remove webparts twice
                                    // due to calls being async occasionally results in an error (something like "unable to delete webpart") since its already deleted.
                                    setTimeout(() => {}, 2000);}
                            }
                        });
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
                    frame: 'none',
                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                        $('#search-criteria-id').empty();
                        $('#search-criteria-id').append("<b>Protein Search criteria: </b>");
                        $('#search-criteria-id').append(searchCriteriaString);
                        $('#search-criteria-id').append("<p></p><p><b>" + this.title + ":</b></p>");
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
                    frame: 'none',
                    success: function () {
                        document.getElementById("search-indicator").style.visibility = "hidden";
                        $('#search-criteria-id').empty();
                        $('#search-criteria-id').append("<b>Peptide Search criteria: </b>");
                        $('#search-criteria-id').append(searchCriteriaString);
                        $('#search-criteria-id').append("<p></p><p><b>" + this.title + ":</p>");
                    }
                });
                wp.render();
            }
            else {
                document.getElementById("search-indicator").style.visibility = "hidden";
            }
        }
    };

    function getFiltersFromUrl () {
        let context = {};

        if (document.location.hash) {
            let token = document.location.hash.split('#');
            token = token[1].split('&');

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
                    case authorsItemId:
                        context[authorsItemId] = t[1];
                        break;
                    case titleItemId:
                        context[titleItemId] = t[1];
                        break;
                    case organismItemId:
                        context[organismItemId] = decodeURIComponent(token[i].slice(token[i].indexOf(':') + 1)); //handle Organism, ex. Organism:Mus musculus(taxid:10090),Homo sapiens (taxid:9606)
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
            addSelectedTabToUrl(tabId);
        }
        if (settingName) {
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

<div id="experiment_list_wp"></div>