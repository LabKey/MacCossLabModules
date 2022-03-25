<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.panoramapublic.model.Submission" %>
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
        font-weight: bold;
    }
    .pxv-invalid {
        color:red;
        font-weight: bold;
    }
    .pxv-btn-submit
    {
        background-image: none;
    }
    .pxv-btn-green
    {
        background-color: darkgreen !important;
    }
    .pxv-btn-orange
    {
        background-color: darkorange !important;
    }
    .pxv-btn-red
    {
        background-color: firebrick !important;
    }

    .pxv-btn-submit .x4-btn-inner-center
    {
        color:white !important;
    }
    .pxv-outdated-validation
    {
        padding: 5px;
        background-color: #FFF6D8;
        font-weight: bold;
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
    int experimentAnnotationsId = bean.getDataValidation().getExperimentAnnotationsId();
    int jobId = bean.getDataValidation().getJobId();
    Integer journalId = bean.getJournalId();
    var submitAction = SpringActionController.getActionName(PanoramaPublicController.PublishExperimentAction.class);
    Submission submission = bean.getSubmission();
    if (submission != null)
    {
        submitAction = submission.hasCopy() ? SpringActionController.getActionName(PanoramaPublicController.ResubmitExperimentAction.class)
                : SpringActionController.getActionName(PanoramaPublicController.UpdateSubmissionAction.class);
    }
    var jobStatus = PipelineService.get().getStatusFile(jobId);
    var onPageLoadMsg = jobStatus != null ? (String.format("Data validation job is %s. This page will automatically refresh with the validation progress.",
            jobStatus.isActive() ? (PipelineJob.TaskStatus.waiting.matches(jobStatus.getStatus()) ? "in the queue" : "running") : "complete"))
            : "Could not find job status for job with Id " + jobId;
%>

<div>
    <div class="alert alert-info" id="onPageLoadMsgDiv"><%=h(onPageLoadMsg)%></div>
    <span class="pxv-bold-underline", style="margin-right:5px;">Job Status: </span> <span id="jobStatusSpan"></span>
    <span style="margin-left:10px;"><%=link("[View Pipeline Job]", PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(getContainer(), jobId))%></span>
</div>

<div style="margin-top:10px;" id="validationProgressDiv"></div>
<div style="margin-top:10px;"><div id="validationStatusDiv"></div></div>

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

    var jobStatusSpan = document.getElementById("jobStatusSpan");
    var validationProgressDiv = document.getElementById("validationProgressDiv");
    var validationStatusDiv = document.getElementById("validationStatusDiv");
    var parameters = LABKEY.ActionURL.getParameters();
    var forSubmit = true;
    if (LABKEY.ActionURL.getParameter("forSubmit") !== undefined) {
        forSubmit = LABKEY.ActionURL.getParameter("forSubmit") === 'true';
    }
    var lastJobStatus = "";
    const FIVE_SEC = 5000;

    Ext4.onReady(makeRequest);

    function makeRequest() {
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('panoramapublic', 'pxValidationStatusApi.api', null, parameters),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(displayStatus),
            failure: function () {
                onFailure("Request was unsuccessful. The server may be unavailable. Please try reloading the page.")
            }
        });
    }

    function onFailure(message)
    {
        setTimeout(alert(message), 500);
    }

    function displayStatus(json) {

        if (json) {

            if (json["error"])
            {
                onFailure("There was an error: " + json["error"]);
                return;
            }
            const jobStatus = json["jobStatus"];
            const validationProgress = json["validationProgress"];
            const validationStatus = json["validationStatus"];
            if (!(validationProgress || validationStatus))
            {
                onFailure("Unexpected JSON response returned by the server.");
                return;
            }

            if (validationProgress) {
                validationProgressDiv.innerHTML = getValidationProgressHtml(validationProgress);
            }
            if (validationStatus) {
                validationProgressDiv.innerHTML = ""; // Remove the job progress text
                displayValidationStatus(validationStatus); // Display the full status details
            }

            if (jobStatus) {
                if (lastJobStatus !== jobStatus) {
                    jobStatusSpan.innerHTML = jobStatus;
                    lastJobStatus = jobStatus;
                }

                const jobStatusLc = jobStatus.toLowerCase();
                if (!(jobStatusLc === "complete" || jobStatusLc === "error" || jobStatusLc === "cancelled" || jobStatusLc === "cancelling")) {
                    // If task is not yet complete then schedule another request.
                    setTimeout(makeRequest, FIVE_SEC);
                }
                else {
                    var onPageLoadMsgDiv = document.getElementById("onPageLoadMsgDiv");
                    if (onPageLoadMsgDiv) {
                        onPageLoadMsgDiv.innerHTML = "";
                        onPageLoadMsgDiv.classList.remove('alert');
                        onPageLoadMsgDiv.classList.remove('alert-info');

                        if (jobStatusLc === "error") {
                            onPageLoadMsgDiv.innerHTML = "There were errors while running the pipeline job. Please " +
                                    '<%=link("view the pipeline job log", PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(getContainer(), jobId))
                                .clearClasses().addClass("alert-link")%>' + " for details.";
                            onPageLoadMsgDiv.classList.add('alert', 'alert-warning', 'labkey-error');
                        }
                    }
                }
            }
            else {
                jobStatusSpan.innerHTML = "UNKNOWN"; // The job may have been deleted. That is why we did not get the job status in the response.
            }
        }
        else {
            onFailure("Server did not return a valid response.");
        }
    }

    function getValidationProgressHtml(validationProgress) {
        var html = "";
        if (validationProgress) {
            for (var i = 0; i < validationProgress.length; i++) {
                html += htmlEncode(validationProgress[i]) + "</br>";
            }
        }
        return html;
    }

    function displayValidationStatus(json) {
        Ext4.create('Ext.panel.Panel', {
            title: 'Data Validation Status',
            renderTo: 'validationStatusDiv',
            items: [validationInfo(json), skylineDocsInfo(json), modificationsInfo(json), spectralLibrariesInfo(json)]
        });
    }

    const PX_COMPLETE = 3;
    const PX_INCOMPLETE = 2;

    // -----------------------------------------------------------
    // Displays the main validation summary panel
    // -----------------------------------------------------------
    function validationInfo(json) {

        function getStatusCls(statusId) {
            return statusId === PX_COMPLETE ? 'pxv-valid' : (statusId !== -1 ? 'pxv-invalid' : '');
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
            if (json["modificationsValid"] === false) problems += '<li>Modifications without a Unimod ID</li>';
            if (json["sampleFilesValid"] === false) problems += '<li>Missing raw data files</li>';
            if (json["specLibsComplete"] === false) problems += '<li>Incomplete spectral library information</li>';
            return '</br>Problems found: <ul>' + problems + '</ul>';
        }

        function getStatusValidHtml() {
            return 'The data is valid for a "complete" ProteomeXchange submission.  ' +
                    'You can view the validation details below.';
        }

        function getIncompleteDataHtml(json) {
            return 'The data can be assigned a ProteomeXchange ID but it is not valid for a "complete" ProteomeXchange submission. ' +
                    problemSummary(json) +
                    'You can view the validation details in the tables below. ' +
                    'For a "complete" submission try submitting after fixing the problems reported. ' +
                    'Otherwise, you can continue with an incomplete submission.';
        }

        function getStatusInvalidHtml(json) {
            return 'The data cannot be assigned a ProteomeXchange ID. ' +
                    problemSummary(json) +
                    'You can view the validation details in the tables below. ' +
                    'Try submitting the data after fixing the problems reported. ' +
                    'Otherwise, you can submit the data without a ProteomeXchange ID.';
        }

        function getStatusDetails(statusId, json) {
            var html =  statusId === PX_COMPLETE ? getStatusValidHtml(json)
                        : statusId === PX_INCOMPLETE ? getIncompleteDataHtml(json) : getStatusInvalidHtml(json);
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

            var components = [{xtype: 'component', margin: '0 0 5 0', html: getStatusDetails(statusId, validationJson)}];
            if (forSubmit === true)
            {
                if (!json['validationOutdated']) {
                    components.push({
                        xtype: 'button',
                        text: getButtonText(statusId),
                        cls: getButtonCls(statusId),
                        href: getButtonLink(statusId, validationJson),
                        hrefTarget: '_self'
                    });
                }
                else {
                    components.push({
                        xtype: 'label',
                        cls: 'pxv-outdated-validation labkey-error',
                        text: 'This validation job is outdated.'
                    });
                    components.push({
                        xtype: 'button',
                        text: 'View All Validation Jobs',
                        margin: '0 0 0 10',
                        href: LABKEY.ActionURL.buildURL('panoramapublic', 'viewPxValidations', LABKEY.ActionURL.getContainer(), {id: validationJson["experimentAnnotationsId"]}),
                        hrefTarget: '_self'
                    });
                }
            }

            return {
                xtype:  'panel',
                cls: 'pxv-summary-panel',
                layout: {type: 'anchor', align: 'left'},
                style:  {margin: '10px'},
                items:  [
                            {xtype: 'component', padding: '10, 5, 0, 5', html: 'Folder: ' + htmlEncode(validationJson["folder"])},
                            {xtype: 'component', padding: '0, 5, 0, 5', html: experimentLink()},
                            {xtype: 'component', padding: '0, 5, 10, 5', html: 'Date: ' + htmlEncode(validationJson["date"])},
                            {
                                xtype:   'component',
                                padding: '0, 5, 10, 5',
                                cls:     getStatusCls(validationJson['statusId']),
                                html:    'Status: ' + htmlEncode(validationJson["status"])
                            },
                            {
                                xtype:   'panel',
                                padding: '0, 5, 10, 5',
                                border: false,
                                layout: {type: 'anchor', align: 'left'},
                                items: components
                            }
                        ]
            };
        }
        return {xtype: 'label', text: 'Missing JSON property "validation"'};
    }

    function link(text, href, cssCls, sameTab) {
        const cls = cssCls ? ' class="' + cssCls + '" ' : '';
        const target = sameTab && sameTab === true ? '' : ' target="_blank" ';
        return '<a ' + cls + ' href="' + htmlEncode(href) + '" ' + target + ' >' + htmlEncode(text) + '</a>';
    }

    function documentLink(documentName, containerPath, runId) {
        return link(documentName, LABKEY.ActionURL.buildURL('targetedms', 'showPrecursorList', containerPath, {id: runId}));
    }

    function experimentLink() {
        return link("[View experiment details]", LABKEY.ActionURL.buildURL('panoramapublic', 'showExperimentAnnotations',
                LABKEY.ActionURL.getContainer(), {id: <%=experimentAnnotationsId%>}), 'labkey-text-link', true);
    }

    function unimodLink(unimodId, cls) {
        return link("UNIMOD:" + unimodId, "https://www.unimod.org/modifications_view.php?editid1=" + unimodId, cls);
    }

    function missing() {
        return '<span class="pxv-invalid">MISSING</span>';
    }

    function assignUnimodLink(dbModId, modType, experimentAnnotationsId) {
        if (!modType) return;
        const modTypeUpper = modType.toUpperCase();
        const action = modTypeUpper === 'STRUCTURAL' ? 'structuralModToUnimodOptions' : 'matchToUnimodIsotope';
        const params = {
            'id': experimentAnnotationsId,
            'modificationId': dbModId,
            'returnUrl': LABKEY.ActionURL.buildURL(LABKEY.ActionURL.getController(), LABKEY.ActionURL.getAction(),
                    LABKEY.ActionURL.getContainer(), LABKEY.ActionURL.getParameters())
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
                fields:  ['id', 'skylineModInfo', 'unimodId', 'unimodName', 'inferred', 'valid', 'modType', 'dbModId', 'documents', 'unimodMatches'],
                data:    json,
                proxy:   { type: 'memory', reader: { type: 'json', root: 'modifications' }},
                sorters: [
                    {
                        property: 'modType',
                        direction: 'DESC' // Structural modifications first
                    },
                    {
                        property: 'valid',
                        direction: 'DESC'
                    },
                    {
                        property: 'unimodId',
                        direction: 'DESC'
                    },
                    {
                        property: 'unimodMatches',
                        direction: 'DESC'
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

            var hasInferred = modificationsStore.find('inferred', true) != -1;

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
                        dataIndex: 'skylineModInfo',
                        flex: 4,
                        renderer: function (v) { return htmlEncode(v); }
                    },
                    {
                        text: 'Unimod Id',
                        dataIndex: 'unimodId',
                        flex: 2,
                        renderer: function (value, metadata, record) {
                            if (value) return unimodLink(value, 'pxv-valid');
                            else if (record.data['unimodMatches']) {
                                var ret = ''; var sep = '';
                                var matches = record.data['unimodMatches'];
                                for (var i = 0; i < matches.length; i++) {
                                    ret += sep + unimodLink(matches[i]['unimodId'], 'pxv-valid');
                                    sep = ' + ';
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
                                for (var i = 0; i < matches.length; i++) {
                                    ret += sep + htmlEncode(matches[i]['name']);
                                    sep = ' + ';
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
                                unimodLink: function (values) {
                                    return unimodLink(values.unimodId);
                                },
                                docLink: function (doc) {
                                    return documentLink(doc.name, doc.container, doc.runId);
                                },
                                peptidesLink: function (doc, dbModId, modType) {
                                    if (!modType) return;
                                    var modTypeUpper = modType.toUpperCase();
                                    var params = {
                                        'schemaName': 'targetedms',
                                        'query.PeptideId/PeptideGroupId/RunId~eq': doc.runId,
                                        'query.queryName': queryName = modTypeUpper === 'STRUCTURAL' ? 'PeptideStructuralModification' : 'PeptideIsotopeModification'
                                    };
                                    if (modTypeUpper === 'STRUCTURAL') params['query.StructuralModId/Id~eq'] = dbModId;
                                    else params['query.IsotopeModId/Id~eq'] = dbModId;
                                    return link('[PEPTIDES]', LABKEY.ActionURL.buildURL('query', 'executeQuery', doc.container, params));
                                },
                                compiled: true
                            }
                    )
                }]
            });
            if (hasInferred) {
                var noteHtml = "Modification names starting with <strong>**</strong> in the Name column did not have a Unimod Id in the Skyline document."
                        + " A Unimod match was was inferred based on the formula, modification site(s) and terminus in the modification definition"
                        + ", or a combination modification was defined.";
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
                fields: ['id', 'runId', 'name', 'container', 'valid', 'sampleFiles'],
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
                        property: 'valid',
                        direction: 'ASC'
                    },
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
                            metadata.tdCls = value ? 'pxv-valid' : 'pxv-invalid';
                            return value ? "COMPLETE" : "INCOMPLETE";
                        }
                    },
                    {
                        text: '',
                        sortable: false,
                        hideable: false,
                        width: 200,
                        dataIndex: 'container',
                        renderer: function (value, metadata, record) {
                            // var iconCls = 'fa-file fa-arrow-up labkey-fa-upload-files';
                            // metadata.tdCls = iconCls;
                            metadata.style = 'text-align: center';
                            if (record.get('valid') === false) {
                                return link('[Upload]', LABKEY.ActionURL.buildURL('project', 'begin', value, {pageId: 'Raw Data'}));
                            }
                            return "";
                        }
                    }],
                plugins: [{
                    ptype: 'rowexpander',
                    rowBodyTpl: new Ext4.XTemplate(
                            // Sample files in the document
                            '<div class="pxv-grid-expanded-row">', '{[this.renderTable(values.sampleFiles)]}', '</div>',
                            {
                                renderTable: function(sampleFiles) {
                                    return dataFilesTableTpl.apply({files: sampleFiles, tblCls: 'sample-files-status', title: ''});
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
                fields: ['id', 'libName', 'libType', 'fileName', 'size', 'valid', 'status', 'spectrumFiles', 'idFiles', 'documents'],
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
                        dataIndex: 'libName', // TODO: link to a Spectral Library webpart
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
                        sortable: false,
                        hideable: false
                    },
                    {
                        text: 'Spectrum Files',
                        dataIndex: 'spectrumFiles',
                        width: 120,
                        sortable: false,
                        hideable: false,
                        renderer: function (value) {
                            return value.length;
                        }
                    },
                    {
                        text: 'Peptide Id Files',
                        dataIndex: 'idFiles',
                        width: 120,
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
                        flex: 2,
                        renderer: function (value, metadata) {
                            metadata.tdCls = value === true ? 'pxv-valid' : 'pxv-invalid';
                            return value === true ? 'COMPLETE' : 'INCOMPLETE';
                        }
                    }],
                plugins: [{
                    ptype: 'rowexpander',
                    rowBodyTpl: new Ext4.XTemplate(

                            '<div class="pxv-grid-expanded-row">',
                            '<div class="pxv-tpl-table-title" style="margin-bottom:10px">Status: {[this.renderLibraryStatus(values.status, values.valid)]}</div>',
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
                                renderLibraryStatus: function (status, valid) {
                                    var cls = valid === true ? 'pxv-valid' : 'pxv-invalid';
                                    return '<span class="' + cls + '">' + status + '</span>';
                                },
                                renderTable: function(dataFiles, tblCls, title) {
                                    return dataFilesTableTpl.apply({files: dataFiles, tblCls: tblCls, title: title});
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

    var dataFilesTableTpl = new Ext4.XTemplate(
            '<tpl if="title.length &gt; 0">', '<div class="pxv-tpl-table-title">{title}</div>', '</tpl>',
            '<table class="{tblCls} pxv-tpl-table">',
            '<tpl for="files">',
            '<tr> <td>{name}</td> {[this.renderStatus(values)]} </tr>', // tdTpl.apply(['{name}']),
            '</tpl>',
            '</table>',
            {
                renderStatus: function (values) {
                    var cls = values.found === true ? 'pxv-valid' : 'pxv-invalid';
                    var status = "FOUND";
                    if (values.found === false) status = "MISSING";
                    if (values.ambiguous === true) status = "AMBIGUOUS";
                    return '<td><span class="' + cls + '">' + status + '</span></td>';
                },
                compiled:true, disableFormats:true
            }
    );

    var headerRowTpl = new Ext4.XTemplate(
            '<thead> <tr> <tpl for="."> <th>{.}</th> </tpl> </tr> </thead>',
            { compiled:true, disableFormats:true }
    );

</script>