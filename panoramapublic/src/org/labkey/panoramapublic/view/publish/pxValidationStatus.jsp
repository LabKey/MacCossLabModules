<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.panoramapublic.model.Submission" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.query.ExperimentAnnotationsManager" %>
<%@ page import="org.labkey.panoramapublic.model.validation.Status" %>
<%@ page import="org.labkey.panoramapublic.query.DataValidationManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<labkey:errors/>

<style type="text/css">

    /* Add a CSS override for .x4-grid-row-expander. The displayed group-expand.gif gets cutoff since it is 9 x 15px
       but the CSS width and height are set to 9 x 9px. */
    .x4-grid-row-expander {
        height: 15px;
    }
    .pxv-bold {
        font-weight: bold; !important
    }
    .pxv-valid {
        color:darkgreen;
    }
    .pxv-invalid {
        color: #d70101;
    }
    .pxv-incomplete {
        color: #ef771a;
    }
    .pxv-btn-green
    {
        background-color: forestgreen;
        border-color: darkgreen;
    }
    .pxv-btn-green .x4-btn-inner-center
    {
        color: white;
    }
    .pxv-btn-orange
    {
        background-color: #fff4ea;
        border-color: #e54f19;
    }
    .pxv-btn-orange .x4-btn-inner-center
    {
        color: #e54f19;
    }
    .pxv-btn-red
    {
        background-color: rgba(255, 243, 243, 0.5);
        border-color: firebrick;
    }
    .pxv-btn-red .x4-btn-inner-center
    {
        color: #d70101;
    }
    .pxv-btn-submit
    {
        padding: 7px;
        border-radius: 4px;
        background-image: none;
    }
    .pxv-outdated-validation
    {
        padding: 5px;
        background-color: #FFF6D8;
        font-weight: bold;
        font-size: 1.1em;
    }
    .pxv-bold-underline
    {
        font-weight:bold;
        text-decoration: underline;
    }
    .pxv-margin10
    {
        margin-top:10px;
        margin-bottom:10px;
    }
    div.pxv-grid-expanded-row {
        background-color:#f1f1f1;
        padding:5px;
        margin-top:5px;
        font-size: 13px;
    }
    div.pxv-tpl-table-title {
        font-weight:bold;
        text-decoration: underline;
        margin-left:8px;
        margin-bottom:2px;
    }
    table.pxv-tpl-table {
        margin:8px;
    }
    table.pxv-tpl-table td,
    table.pxv-tpl-table th
    {
        border:1px solid slategray; padding:8px;
    }

</style>

<%
    var view = (JspView<PanoramaPublicController.PxValidationStatusBean>) HttpView.currentView();
    var bean = view.getModelBean();
    ExperimentAnnotations experimentAnnotations = bean.getExpAnnotations();
    int experimentAnnotationsId = experimentAnnotations.getId();
    boolean includeSubfolders = experimentAnnotations != null && experimentAnnotations.isIncludeSubfolders();
    int jobId = bean.getDataValidation().getJobId();
    Integer journalId = bean.getJournalId();
    var submitAction = SpringActionController.getActionName(PanoramaPublicController.PublishExperimentAction.class);
    Submission submission = bean.getSubmission();
    if (submission != null)
    {
        submitAction = submission.hasCopy() ? SpringActionController.getActionName(PanoramaPublicController.ResubmitExperimentAction.class)
                : SpringActionController.getActionName(PanoramaPublicController.UpdateSubmissionAction.class);
    }
    Status validationStatus = bean.getPxValidationStatus();
    boolean isOutdated = DataValidationManager.isValidationOutdated(bean.getDataValidation(), experimentAnnotations, getUser());
    boolean isLatest = DataValidationManager.isLatestValidation(bean.getDataValidation(), experimentAnnotations.getContainer());
%>

<div id="validationStatusDiv"/>

<script type="text/javascript">

    // Links that helped:
    // https://docs.sencha.com/extjs/4.2.2/extjs-build/examples/build/KitchenSink/ext-theme-neptune/#row-expander-grid
    // https://docs.sencha.com/extjs/4.2.5/#!/api/Ext.grid.Panel
    // Column: https://docs.sencha.com/extjs/4.2.1/#!/api/Ext.grid.column.Column
    // https://docs.sencha.com/extjs/4.2.1/#!/api/Ext.grid.column.Column-cfg-renderer
    // Ext.XTemplate: https://docs.sencha.com/extjs/4.2.1/#!/api/Ext.XTemplate
    // Auto resize column: http://extjs-intro.blogspot.com/2014/04/extjs-grid-panel-column-resize.html
    // Flex column property: https://stackoverflow.com/questions/8241682/what-is-meant-by-the-flex-property-of-any-extjs4-layout/8242860
    // https://forum.sencha.com/forum/showthread.php?247657-4-1-1-How-to-set-style-for-grid-cell-content
    // Combining templates: https://stackoverflow.com/questions/5006273/extjs-xtemplate

    var htmlEncode = Ext4.util.Format.htmlEncode;
    const PX_COMPLETE = 3;
    const PX_INCOMPLETE = 2;
    const validationStatusDiv = document.getElementById("validationStatusDiv");
    const parameters = LABKEY.ActionURL.getParameters();
    let forSubmit = true;
    if (LABKEY.ActionURL.getParameter("forSubmit") !== undefined) {
        forSubmit = LABKEY.ActionURL.getParameter("forSubmit") === 'true';
    }
    const returnUrl = LABKEY.ActionURL.buildURL(LABKEY.ActionURL.getController(), LABKEY.ActionURL.getAction(),
            LABKEY.ActionURL.getContainer(), LABKEY.ActionURL.getParameters());

    Ext4.onReady(function() {

        const json = <%=validationStatus.toJSON()%>;
        Ext4.create('Ext.panel.Panel', {
            bodyStyle: {border: '0px', padding: '0px'},
            renderTo: 'validationStatusDiv',
            items: [validationInfo(json), skylineDocsInfo(json), modificationsInfo(json), spectralLibrariesInfo(json)]
        });
    });

    // -----------------------------------------------------------
    // Displays the main validation summary panel
    // -----------------------------------------------------------
    function validationInfo(json) {

        function getStatusCls(statusId) {
            return (statusId === PX_COMPLETE ? 'pxv-valid' : statusId === PX_INCOMPLETE ? 'pxv-incomplete' : (statusId !== -1 ? 'pxv-invalid' : '')) + ' pxv-bold';
        }

        function getMissingMetadataFields(missingFields) {
            var list = '<ul>';
            for (var i = 0; i < missingFields.length; i++) {
                list += '<li>' + htmlEncode(missingFields[i]) + '</li>';
            }
            list += '</ul>';
            return list;
        }

        function problemSummary(json) {
            var problems = '';
            if (json["missingMetadata"]) {
                var updateMedataLink = LABKEY.ActionURL.buildURL('panoramapublic', 'showUpdateExperimentAnnotations', LABKEY.ActionURL.getContainer(), {id: <%=experimentAnnotationsId%>});
                problems += '<li>Missing metadata: [' + link("Update Metadata", updateMedataLink, 'pxv-bold') + ']' + getMissingMetadataFields(json["missingMetadata"]) + '</li>';
            }
            if (json["sampleFilesValid"] === false) problems += '<li>Missing raw data files</li>';
            if (json["modificationsValid"] === false) problems += '<li>Modifications without a Unimod ID</li>';
            if (json["specLibsComplete"] === false) problems += '<li>Incomplete spectral library information</li>';
            return '</br>Problems found: <ul>' + problems + '</ul>';
        }

        function getStatusValidHtml() {
            return 'The data is valid for a "complete" ProteomeXchange submission.  ' +
                    'You can view the validation details below.';
        }

        function getIncompleteDataHtml(json, validationOutdated) {
            let message = 'The data can be assigned a ProteomeXchange ID but it is not valid for a "complete" ProteomeXchange submission. ' +
                    problemSummary(json) +
                    'You can view the validation details in the tables below. ';
            if (!validationOutdated) {
                message += 'For a "complete" submission try submitting after fixing the problems reported. ' +
                        'Otherwise, you can continue with an incomplete submission.';
            }
            return message;
        }

        function getStatusInvalidHtml(json, validationOutdated) {
            let message = 'The data cannot be assigned a ProteomeXchange ID. ' +
                    problemSummary(json) +
                    'You can view the validation details in the tables below. ';
            if (!validationOutdated) {
                message += 'Try submitting the data after fixing the problems reported. ' +
                        'Otherwise, you can submit the data without a ProteomeXchange ID.';
            }
            return message;
        }

        function getStatusDetails(statusId, json, validationOutdated) {
            var html =  statusId === PX_COMPLETE ? getStatusValidHtml(json)
                    : statusId === PX_INCOMPLETE ? getIncompleteDataHtml(json, validationOutdated) : getStatusInvalidHtml(json, validationOutdated);
            return '<div>' + html + '</div>';
        }

        function getButtonText(statusId) {
            return statusId === PX_COMPLETE ? "Continue Submission"
                    : statusId === PX_INCOMPLETE ? "Continue with an Incomplete PX Submission" : "Submit without a ProteomeXchange ID";
        }

        function getButtonCls(statusId) {
            var cls = "pxv-btn-submit";
            return cls + (statusId === PX_COMPLETE ? " pxv-btn-green"  : statusId === PX_INCOMPLETE ? " pxv-btn-orange" : " pxv-btn-red");
        }

        function getButtonLink(statusId, json) {
            var params = {id: json["experimentAnnotationsId"], validationId: json["id"], "doSubfolderCheck": false};
            if (statusId < PX_INCOMPLETE) { params["getPxid"] = false; }
            else { params["getPxid"] = true; }

            <% if(journalId != null) { %>
            {params["journalId"] = <%=journalId%>;}
            <% }%>

            return LABKEY.ActionURL.buildURL('panoramapublic', <%=qh(submitAction)%>, LABKEY.ActionURL.getContainer(), params);
        }

        if (json["validation"]) {

            const validationJson = json["validation"];
            const statusId = validationJson["statusId"];
            const outdated = <%=isOutdated%>;
            const latest = <%=isLatest%>;

            var components = [{xtype: 'component', margin: '0 0 5 0', html: getStatusDetails(statusId, validationJson, outdated)}];
            if (forSubmit === true && !outdated)
            {
                components.push({
                    xtype: 'button',
                    text: getButtonText(statusId),
                    cls: getButtonCls(statusId),
                    href: getButtonLink(statusId, validationJson),
                    hrefTarget: '_self'
                });
            }

            let allComponents = [
                {
                    xtype:   'component',
                    padding: '0, 5, 5, 5',
                    cls:     getStatusCls(validationJson['statusId']),
                    html:    '<span style="font-size: 1.25em;">Status: ' + htmlEncode(validationJson["status"]) + '</span>'
                },
                {xtype: 'component', padding: '0, 5, 5, 5', html: 'Validation date: ' + htmlEncode(validationJson["date"])},
                {
                    xtype:   'panel',
                    padding: '0, 5, 10, 5',
                    border: false,
                    layout: {type: 'anchor', align: 'left'},
                    items: components
                },
                {xtype: 'component', padding: '10, 5, 0, 5', html: experimentLink() + "&nbsp;"
                            + link("[View validation log]", LABKEY.ActionURL.buildURL('pipeline-status', 'details', LABKEY.ActionURL.getContainer(), {rowId: <%=jobId%>}),
                                    'labkey-text-link', true)},
            ];

            if (outdated) {
                let text = 'These validation results are outdated.';
                if (latest) {
                    text += ' Please click the button below to re-run validation.'
                }
                allComponents.unshift({
                    xtype: 'component',
                    style: {margin: '10px 0 15px; 0'},
                    html: '<%=button("Start Data Validation").href(PanoramaPublicController.getSubmitPxValidationJobUrl(experimentAnnotations, getContainer())).usePost().build().getHtmlString()%>'
                });
                allComponents.unshift({
                    xtype: 'label',
                    cls: 'pxv-outdated-validation labkey-error',
                    text:  text
                });
            }

            return {
                xtype:  'panel',
                cls: 'pxv-summary-panel',
                bodyStyle: {border: '0px'},
                layout: {type: 'anchor', align: 'left'},
                style:  {margin: '10px'},
                items: allComponents
            };
        }
        return {xtype: 'label', text: 'Missing JSON property "validation"'};
    }

    function link(text, href, cssCls, sameTab, style) {
        const cls = cssCls ? ' class="' + cssCls + '" ' : '';
        let target = ' target="_blank" ';
        let rel = ' rel="noopener noreferrer" ';
        if (sameTab && sameTab === true) {
            target = "";
            rel = "";
        }
        return '<a ' + cls + (style ? ' style="' + style + '" ' : "") + ' href="' + htmlEncode(href) + '" ' + target + rel + ' >' + htmlEncode(text) + '</a>';
    }

    function documentLink(documentName, containerPath, runId) {
        // If there is no container path it means that the document or the containing container was deleted. Do not render a link in that case
        return containerPath ? link(documentName, LABKEY.ActionURL.buildURL('targetedms', 'showPrecursorList', containerPath, {id: runId})) : documentName;
    }

    function experimentLink() {
        return link("[View experiment details]", LABKEY.ActionURL.buildURL('panoramapublic', 'showExperimentAnnotations',
                LABKEY.ActionURL.getContainer(), {id: <%=experimentAnnotationsId%>}), 'labkey-text-link', true);
    }

    function unimodLink(unimodId) {
        return link("UNIMOD:" + unimodId, "https://www.unimod.org/modifications_view.php?editid1=" + unimodId, "labkey-text-link-noarrow",
                false, "margin-right: 0px; padding-right: 5px; color:green");
    }

    function missing() {
        return '<span class="pxv-invalid pxv-bold">MISSING</span>';
    }

    function assignUnimodLink(dbModId, modType, experimentAnnotationsId) {
        if (!modType) return;
        const modTypeUpper = modType.toUpperCase();
        const action = modTypeUpper === 'STRUCTURAL' ? 'structuralModToUnimodOptions' : 'matchToUnimodIsotope';
        const params = {
            'id': experimentAnnotationsId,
            'modificationId': dbModId,
            'returnUrl': returnUrl
        };

        var href = LABKEY.ActionURL.buildURL('panoramapublic', action, LABKEY.ActionURL.getContainer(), params);
        return '<span style="margin-left:5px;">'  + link("Find Match", href, 'labkey-text-link', true) + '</span>';
    }

    // -----------------------------------------------------------
    // Displays the modifications validation grid
    // -----------------------------------------------------------
    function modificationsInfo(json) {

        if (json["modifications"]) {
            const modificationsStore = Ext4.create('Ext.data.Store', {
                storeId: 'modificationsStore',
                fields:  ['id', 'skylineModName', 'unimodId', 'unimodName', 'matchAssigned', 'valid', 'modType', 'dbModId', 'documents', 'unimodMatches'],
                data:    json,
                proxy:   { type: 'memory', reader: { type: 'json', root: 'modifications' }},
                sorters: [
                    {
                        property: 'modType',
                        direction: 'DESC' // Structural modifications first
                    },
                    {
                        property: 'id',
                        direction: 'ASC'
                    }
                ]
            });

            if (modificationsStore.getCount() === 0) {
                return Ext4.create('Ext.Panel', {
                    title: 'Modifications',
                    padding: 10,
                    items: [{xtype: 'component', html: "No modifications found", padding: 10}]
                });
            }

            var hasInferred = modificationsStore.find('matchAssigned', true) != -1;

            var grid = Ext4.create('Ext.grid.Panel', {
                store:    modificationsStore,
                storeId: 'modificationsStore',
                cls: 'pxv-modifications-panel',
                padding:  '0 10 10 10',
                disableSelection: true,
                collapsible: true,
                animCollapse: false,
                viewConfig: {enableTextSelection: true},
                title: 'Modifications',
                columns: {
                    items: [
                        {
                            text: 'Name',
                            dataIndex: 'skylineModName',
                            flex: 4,
                            renderer: function (v) { return htmlEncode(v); }
                        },
                        {
                            text: 'Unimod Match',
                            dataIndex: 'unimodId',
                            flex: 2,
                            renderer: function (value, metadata, record) {
                                if (value) return unimodLink(value);
                                else if (record.data['unimodMatches']) {
                                    var ret = ''; var sep = '';
                                    var matches = record.data['unimodMatches'];
                                    var isotopic = record.data['modType'] === "Isotopic" ? true : false;
                                    for (var i = 0; i < matches.length; i++) {
                                        ret += sep + "**" + unimodLink(matches[i]['unimodId']);
                                        sep = isotopic ? '</br>' : '<b> + </b>';
                                    }
                                    return ret;
                                }
                                else return missing() + assignUnimodLink(record.data['dbModId'], record.data['modType'], <%=experimentAnnotationsId%>);
                            }
                        },
                        {
                            text: 'Unimod Name',
                            dataIndex: 'unimodName',
                            flex: 3,
                            renderer: function (value, metadata, record) {
                                if (value) return htmlEncode(value);
                                else if (record.data['unimodMatches']) {
                                    var ret = ''; var sep = '';
                                    var matches = record.data['unimodMatches'];
                                    var isotopic = record.data['modType'] === "Isotopic" ? true : false;
                                    for (var i = 0; i < matches.length; i++) {
                                        ret += sep + htmlEncode(matches[i]['name']);
                                        sep = isotopic ? '</br>' : ' + ';
                                    }
                                    return ret;
                                }
                                else return '';
                            }
                        },
                        {
                            text: 'Type',
                            flex: 2,
                            dataIndex: 'modType',
                            renderer: function (v) { return htmlEncode(v); }
                        },
                        {
                            text: 'Document Count',
                            flex: 1,
                            dataIndex: 'documents',
                            renderer: function (v) { return v.length; }
                        }],
                    defaults: {
                        sortable: false,
                        hideable: false
                    }
                },
                plugins: [{
                    ptype: 'rowexpander',
                    rowBodyTpl: new Ext4.XTemplate(

                            '<div class="pxv-grid-expanded-row">',

                            // Skyline documents with this modification
                            '<div class="pxv-tpl-table-title">Skyline documents with the modification</div>',
                            '<table class="pxv-tpl-table">',
                            '<tpl for="documents">',
                            '<tr> <td>{[this.docLink(values)]}</td> <td>{[this.peptidesLink(values, parent.dbModId, parent.modType)]}</td> </tr>',
                            '</tpl>',
                            '</table>',
                            '</div>',
                            '</div>',
                            {
                                docLink: function (doc) {
                                    return documentLink(doc.name, doc.container, doc.runId);
                                },
                                peptidesLink: function (doc, dbModId, modType) {
                                    if (!modType) return;
                                    if (!doc.container) return "Deleted";
                                    var modTypeUpper = modType.toUpperCase();
                                    var params = {
                                        'schemaName': 'panoramapublic'
                                    };
                                    if (modTypeUpper === 'STRUCTURAL') {
                                        params['query.queryName'] = 'PeptideStructuralModification';
                                        params['query.StructuralModId/Id~eq'] = dbModId;
                                        params['query.PeptideGroupId/RunId~eq'] = doc.runId;
                                    }
                                    else {
                                        params['query.queryName'] = 'PrecursorIsotopeModification';
                                        params['query.IsotopeModId/Id~eq'] = dbModId;
                                        params['query.PeptideId/PeptideGroupId/RunId~eq'] = doc.runId;
                                    }
                                    return link('[PEPTIDES]', LABKEY.ActionURL.buildURL('query', 'executeQuery', doc.container, params));
                                },
                                compiled: true
                            }
                    )
                }]
            });
            if (hasInferred) {
                var noteHtml = "Unimod Ids starting with <strong>**</strong> in the Unimod Match column were assigned based on the formula, "
                        + "modification site(s) and terminus in the modification definition.";
                var note = {
                    xtype: 'component',
                    padding: 10,
                    margin: '15 10 5 10',
                    cls: 'labkey-error alert alert-warning',
                    html: '<em>' + noteHtml + '</em>'
                };
                return {xtype: 'panel', border: 0, items: [note, grid]};
            }
            else { return grid; }
        }
        return {xtype: 'label', text: 'Missing JSON property "modifications"'};
    }

    // -----------------------------------------------------------
    // Displays the sample files validation grid
    // -----------------------------------------------------------
    function skylineDocsInfo(json) {
        if (json["skylineDocuments"]) {
            var skylineDocsStore = Ext4.create('Ext.data.Store', {
                storeId: 'skylineDocsStore',
                fields: ['id', 'runId', 'name', 'container', 'rel_container', 'valid', 'sampleFiles'],
                data: json,
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json',
                        root: 'skylineDocuments'
                    }
                },
                sorters: [
                    {
                        property: 'container',
                        direction: 'ASC'
                    },
                    {
                        property: 'runId',
                        direction: 'ASC'
                    }
                ]
            });

            return Ext4.create('Ext.grid.Panel', {
                store: skylineDocsStore,
                storeId: 'skylineDocsStore',
                cls: 'pxv-skydocs-panel',
                padding: 10,
                disableSelection: true,
                viewConfig: {enableTextSelection: true},
                title: 'Skyline Document Sample Files',
                columns: [
                    {
                        text: 'Name',
                        dataIndex: 'name',
                        flex: 1, // https://stackoverflow.com/questions/8241682/what-is-meant-by-the-flex-property-of-any-extjs4-layout/8242860
                        sortable: false,
                        hideable: false,
                        renderer: function (value, metadata, record) {
                            return documentLink(value, record.get('container'), record.get('runId'));
                        }
                    },
                    {
                        text: 'Sample File Count',
                        dataIndex: 'sampleFiles',
                        width: 150,
                        sortable: false,
                        hideable: false,
                        renderer: function (value, metadata, record) {
                            var url = LABKEY.ActionURL.buildURL('targetedms', 'showReplicates', record.get('container'), {id: record.get('runId')});
                            return link(value.length, url);
                        }
                    },
                    {
                        text: 'Status',
                        sortable: false,
                        hideable: false,
                        width: 150,
                        dataIndex: 'valid',
                        renderer: function (value, metadata) {
                            metadata.tdCls = (value ? 'pxv-valid' : 'pxv-invalid') + ' pxv-bold';
                            return value ? "COMPLETE" : "INCOMPLETE";
                        }
                    },
                    <% if (includeSubfolders) { %>
                    {
                        text: 'Folder',
                        sortable: false,
                        hideable: false,
                        width: 250,
                        dataIndex: 'rel_container',
                        renderer: function (value, metadata, record) {
                            metadata.style = 'text-align: left';
                            return htmlEncode(value);
                        }
                    },
                    <% } %>
                ],
                plugins: [{
                    ptype: 'rowexpander',
                    rowBodyTpl: new Ext4.XTemplate(
                            // Sample files in the document
                            '<div class="pxv-grid-expanded-row">',
                            '{[this.ambiguousFilesMsg(values.sampleFiles)]}',
                            '{[this.uploadButton(values)]}',
                            '{[this.renderTable(values.sampleFiles)]}',
                            '</div>',
                            {
                                renderTable: function(sampleFiles) {
                                    if (!sampleFiles || sampleFiles.length === 0) {
                                        return "Skyline document does not contain any imported results"
                                    }
                                    return sampleFilesTableTpl.apply({files: sampleFiles, tblCls: 'sample-files-status'});
                                },
                                uploadButton: function(values) {
                                    if (values['container'] && values['valid'] === false) {
                                        // If container value is missing it means that the document or the containing container was deleted.
                                        return '<div style="margin: 8px;">'
                                                + link('Upload Files', LABKEY.ActionURL.buildURL('project', 'begin', values['container'], {pageId: 'Raw Data'}), 'labkey-button')
                                                + '</div>';
                                    }
                                    return "";
                                },
                                ambiguousFilesMsg: function(sampleFiles) {
                                    let ambiguousFilesMsg = "";
                                    if (sampleFiles && sampleFiles.length > 0) {
                                        var hasAmbiguous = false;
                                        for (const file of sampleFiles) {
                                            if (file['ambiguous'] === true) {
                                                hasAmbiguous = true;
                                                break;
                                            }
                                        }
                                        if (hasAmbiguous) {
                                            ambiguousFilesMsg = '<div class="pxv-invalid"><em>Files marked as ambiguous have the same name in one or more documents in the folder but are different ' +
                                                    'files based on the imported file path and the acquired time on the mass spectrometer. Click the View Files link in ' +
                                                    'the table below to view sample files with the same name. ' +
                                                    '<br>' +
                                                    'File names imported into Skyline documents being submitted must have unique names if they are different files.' + '</em></div>';
                                        }
                                    }
                                    return ambiguousFilesMsg;
                                },
                                compiled:true
                            }
                    )
                }],
                collapsible: true,
                animCollapse: false
            });
        }
        return {xtype: 'label', text: 'Missing JSON for property "skylineDocuments"'};
    }

    // -----------------------------------------------------------
    // Displays the spectral libraries validation grid
    // -----------------------------------------------------------
    function spectralLibrariesInfo(json) {
        if (json["spectrumLibraries"]) {
            var specLibStore = Ext4.create('Ext.data.Store', {
                storeId: 'specLibStore',
                fields: ['id', 'libName', 'libType', 'fileName', 'size', 'valid', 'status', 'specLibInfo', 'specLibInfoId', 'spectrumFiles', 'idFiles', 'documents'],
                data: json,
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json',
                        root: 'spectrumLibraries'
                    }
                },
                sorters: [
                    {
                        property: 'valid',
                        direction: 'ASC'
                    },
                    {
                        property: 'libType',
                        direction: 'ASC'
                    },
                    {
                        property: 'fileName',
                        direction: 'ASC'
                    }
                ]
            });

            if (specLibStore.getCount() === 0) {
                return Ext4.create('Ext.Panel', {
                    title: 'Spectral Libraries',
                    padding: 10,
                    items: [{xtype: 'component', html: "No spectral libraries found", padding: 10}]
                });
            }

            return Ext4.create('Ext.grid.Panel', {
                store: specLibStore,
                storeId: 'specLibStore',
                cls: 'pxv-speclibs-panel',
                padding: 10,
                disableSelection: true,
                viewConfig: {enableTextSelection: true},
                title: 'Spectral Libraries',
                columns: [
                    {
                        text: 'Name',
                        dataIndex: 'libName',
                        flex: 3,
                        sortable: false,
                        hideable: false,
                        renderer: function (v) {
                            return htmlEncode(v);
                        }
                    },
                    {
                        text: 'File Name',
                        dataIndex: 'fileName',
                        flex: 3,
                        sortable: false,
                        hideable: false,
                        renderer: function (v) {
                            return htmlEncode(v);
                        }
                    },
                    {
                        text: 'File Size',
                        dataIndex: 'size',
                        width: 100,
                        sortable: false,
                        hideable: false
                    },
                    {
                        text: 'Spectrum Files',
                        dataIndex: 'spectrumFiles',
                        width: 100,
                        sortable: false,
                        hideable: false,
                        renderer: function (value) {
                            return value.length;
                        }
                    },
                    {
                        text: 'Peptide Id Files',
                        dataIndex: 'idFiles',
                        width: 100,
                        sortable: false,
                        hideable: false,
                        renderer: function (value) {
                            return value.length;
                        }
                    },
                    {
                        text: 'Documents',
                        dataIndex: 'documents',
                        width: 100,
                        sortable: false,
                        hideable: false,
                        renderer: function (value) {
                            return value.length;
                        }
                    },
                    {
                        text: 'Status',
                        dataIndex: 'valid',
                        sortable: false,
                        hideable: false,
                        width: 100,
                        renderer: function (value, metadata) {
                            metadata.tdCls = (value === true ? 'pxv-valid' : 'pxv-invalid') + ' pxv-bold';
                            return value === true ? 'COMPLETE' : 'INCOMPLETE';
                        }
                    },
                    {
                        text: '',
                        sortable: false,
                        hideable: false,
                        width: 125,
                        renderer: function (value, metadata, record) {
                            metadata.style = 'text-align: center';
                            if (record.get('valid') === false) {
                                return link('[Upload]', LABKEY.ActionURL.buildURL('project', 'begin', LABKEY.ActionURL.getContainer(), {pageId: 'Raw Data'}));
                            }
                            return "";
                        }
                    }
                ],
                plugins: [{
                    ptype: 'rowexpander',
                    rowBodyTpl: new Ext4.XTemplate(

                            '<div class="pxv-grid-expanded-row">',
                            '<div>{[this.renderLibraryStatus(values)]}</div>',
                            // Spectrum files
                            '<tpl if="spectrumFiles.length &gt; 0">','{[this.renderTable(values.spectrumFiles, "lib-spectrum-files-status", "Spectrum Files")]}', '</tpl>',
                            // Peptide Id files
                            '<tpl if="idFiles.length &gt; 0">', '{[this.renderTable(values.idFiles, "lib-id-files-status", "Peptide Id Files")]}', '</tpl>',
                            // Skyline documents with the library
                            '<div class="pxv-tpl-table-title" style="margin-bottom:5px;">Skyline documents with the library</div>',
                            '<ul>',
                            '<tpl for="documents">',
                            '<li>{[this.docLink(values)]}</li>',
                            '</tpl>',
                            '</ul>',
                            '</div>',
                            {
                                renderLibraryStatus: function (values) {
                                    let specLibInfoHtml = '';
                                    if (values.specLibInfo) {
                                        const params = {
                                            'schemaName': 'panoramapublic',
                                            'queryName': 'SpectralLibraries',
                                            'viewName': 'SpectralLibrariesInfo',
                                            'query.SpecLibInfoId~eq': values.specLibInfoId,
                                            'returnUrl': returnUrl
                                        };

                                        const href = LABKEY.ActionURL.buildURL('query', 'executeQuery', LABKEY.ActionURL.getContainer(), params);
                                        specLibInfoHtml = '<div style="margin-bottom:5px;">'
                                                + '<span class="pxv-bold">Library Information: </span><span style="margin-right: 10px;">' + htmlEncode(values.specLibInfo)+ '</span>'
                                                + link("[View Library Info]", href, 'pxv-bold', true)
                                                + '</div>';
                                    }
                                    let statusHtml = '';
                                    if (values.valid === false) {
                                        const cls = values.valid === true ? '' : 'pxv-invalid';
                                        statusHtml = '<div><span class="' + cls + '">' + htmlEncode(values.status) + '</span></div>';
                                    }

                                    return (statusHtml || specLibInfoHtml) ? '<div style="margin-bottom:20px;">' + specLibInfoHtml + statusHtml + '</div>' : '';
                                },
                                renderTable: function(dataFiles, tblCls, title) {
                                    return libSourceFilesTableTpl.apply({files: dataFiles, tblCls: tblCls, title: title});
                                },
                                docLink: function (doc) {
                                    return documentLink(doc.name, doc.container, doc.runId);
                                },
                                compiled:true
                            }
                    )
                }],
                collapsible: true,
                animCollapse: false
            });
        }
        return {xtype: 'label', text: 'Missing JSON for property "spectrumLibraries"'};
    }

    var sampleFilesTableTpl = new Ext4.XTemplate(
            '<table class="{tblCls} pxv-tpl-table">',
            '<thead><tr><th>Replicate</th><th>File</th><th>Status</th><th>Path</th><tr></thead>',
            '<tpl for="files">',
            '<tr> <td>{replicate:htmlEncode}</td> <td>{name:htmlEncode}</td> {[this.renderStatus(values)]}  <td>{[this.renderPath(values)]}</td></tr>', // tdTpl.apply(['{name}']),
            '</tpl>',
            '</table>',
            '<div>{container}</div>',
            {
                renderStatus: function (values)
                {
                    var ambigousFilesLink;
                    if (values.ambiguous === true && values.container) {
                        let params = {
                            'schemaName': 'panoramapublic',
                            'query.queryName': 'ExperimentSampleFiles',
                            'query.File~eq': values.name
                        };
                        const href = LABKEY.ActionURL.buildURL('query', 'executeQuery', values.container, params)
                        ambigousFilesLink = link(" View Files", href, 'labkey-text-link');
                    }
                    return renderFileStatus(values, true, ambigousFilesLink)
                },
                renderPath: function (values) { return htmlEncode(values.path);},
                compiled:true, disableFormats:true
            }
    );

    var libSourceFilesTableTpl = new Ext4.XTemplate(
            '<tpl if="title.length &gt; 0">', '<div class="pxv-tpl-table-title">{title:htmlEncode}</div>', '</tpl>',
            '<table class="{tblCls} pxv-tpl-table">',
            '<thead><tr><th>File</th><th>Status</th><th>Path</th><tr></thead>',
            '<tpl for="files">',
            '<tr> <td>{name:htmlEncode}</td> {[this.renderStatus(values)]}  <td>{path:htmlEncode}</td></tr>',
            '</tpl>',
            '</table>',
            {
                renderStatus: function (values) { return renderFileStatus(values, false) },
                compiled:true, disableFormats:true
            }
    );

    renderFileStatus: function renderFileStatus(values, isSampleFile, link) {
        const cls = (values.found === true ? 'pxv-valid' : 'pxv-invalid') + ' pxv-bold';
        let status = '';
        if (values.found === true) status = "FOUND";
        if (values.found === false) status = "MISSING";
        if (values.ambiguous === true) status = "AMBIGUOUS";
        return '<td><span class="' + cls + '">' + htmlEncode(status) + (link ? '<span style="margin-left: 5px;">' + link + '</span>' : "") + '</span></td>';
    }

    var headerRowTpl = new Ext4.XTemplate(
            '<thead> <tr> <tpl for="."> <th>{.}</th> </tpl> </tr> </thead>',
            { compiled:true, disableFormats:true }
    );

</script>