<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.panoramapublic.PanoramaPublicController" %>
<%@ page import="org.labkey.panoramapublic.model.ExperimentAnnotations" %>
<%@ page import="org.labkey.panoramapublic.model.Submission" %>
<%@ page import="org.labkey.panoramapublic.model.validation.Status" %>
<%@ page import="org.labkey.panoramapublic.query.DataValidationManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("PanoramaPublic/css/pxValidation.css");
        dependencies.add("PanoramaPublic/js/pxValidation.js");
    }
%>
<labkey:errors/>

<%
    var view = (JspView<PanoramaPublicController.PxValidationStatusBean>) HttpView.currentView();
    var bean = view.getModelBean();
    ExperimentAnnotations experimentAnnotations = bean.getExpAnnotations();
    int experimentAnnotationsId = experimentAnnotations.getId();
    boolean includeSubfolders = experimentAnnotations.isIncludeSubfolders();
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
    JSONObject json = validationStatus != null ? validationStatus.toJSON() : new JSONObject();
    boolean isOutdated = DataValidationManager.isValidationOutdated(bean.getDataValidation(), experimentAnnotations, getUser());
    boolean isLatest = DataValidationManager.isLatestValidation(bean.getDataValidation(), experimentAnnotations.getContainer());
%>

<div id="validationStatusDiv"/>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

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

    const PX_COMPLETE = 3;
    const PX_INCOMPLETE = 2;
    const validationStatusDiv = document.getElementById("validationStatusDiv");
    const parameters = LABKEY.ActionURL.getParameters();
    let forSubmit = true;
    if (LABKEY.ActionURL.getParameter("forSubmit") !== undefined) {
        forSubmit = LABKEY.ActionURL.getParameter("forSubmit") === 'true';
    }

    Ext4.onReady(function() {

        const json = <%=json%>;
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
                    allComponents.unshift({
                        xtype: 'button',
                        text: 'Rerun Validation',
                        style: {'margin': '10px'},
                        handler: function() {
                            LABKEY.Utils.postToAction(LABKEY.ActionURL.buildURL('panoramapublic', 'submitPxValidationJob', LABKEY.ActionURL.getContainer(), {'id': <%=experimentAnnotationsId%>}));
                        }
                    });
                }
                else {
                    text += " Please view the latest results on the experiment details page.";
                }
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

    function experimentLink() {
        return link("[View experiment details]", LABKEY.ActionURL.buildURL('panoramapublic', 'showExperimentAnnotations',
                LABKEY.ActionURL.getContainer(), {id: <%=experimentAnnotationsId%>}), 'labkey-text-link', true);
    }

    function unimodLink(unimodId) {

        return link("UNIMOD:" + unimodId, "https://www.unimod.org/modifications_view.php?editid1=" + unimodId, "labkey-text-link-noarrow",
                false, "margin-right: 0px; padding-right: 5px; color:green; font-size:14px;");
    }

    function assignUnimodLink(dbModId, modType, experimentAnnotationsId) {
        if (!modType) return;
        const action = isStructuralModType(modType) ? 'structuralModToUnimodOptions' : 'matchToUnimodIsotope';
        const params = {
            'id': experimentAnnotationsId,
            'modificationId': dbModId,
            'returnUrl': returnUrl
        };

        var href = LABKEY.ActionURL.buildURL('panoramapublic', action, LABKEY.ActionURL.getContainer(), params);
        return '<span style="margin-left:5px;">'  + link("Find Match", href, 'labkey-text-link', true) + '</span>';
    }

    function deleteModInfoLink(modInfoId, skylineModName, experimentAnnotationsId, modType, matchCount) {
        if (!modType) return;
        const action = isStructuralModType(modType) ? 'deleteStructuralModInfo' : 'deleteIsotopeModInfo';

        const linkText = matchCount === 1 ? "Delete Match" : "Delete Matches";
        const id = 'delete-assigned-unimod-id-' + Ext4.id();
        const html = '<a id="' + id +'" href="#" class="labkey-text-link" style="margin-left:5px;" >' + linkText + '</a>';
        const callback = () => {
            console.log("attaching event handler for id " + id);
            LABKEY.Utils.attachEventHandler(id, "click", function () {
                return deleteModInfo(modInfoId, experimentAnnotationsId, skylineModName, action);
            });
        };
        return {"html": html, "callback": callback }
        // return '<a class="labkey-text-link" style="margin-left:5px;" onClick="deleteModInfo(' + modInfoId + ',' + experimentAnnotationsId
        //         + ', \'' + skylineModName + '\', \'' + action + '\');">' + linkText + '</a>';
    }

    function deleteModInfo(modInfoId, experimentAnnotationsId, skylineModName, action) {
        const confirmMsg = "Are you sure you want to delete the saved Unimod information for modification '" + skylineModName + "'?";
        const params = {
            'id': experimentAnnotationsId,
            'modInfoId': modInfoId,
            'returnUrl': returnUrl
        };
        const href = LABKEY.ActionURL.buildURL('panoramapublic', action, LABKEY.ActionURL.getContainer(), params);
        return LABKEY.Utils.confirmAndPost(confirmMsg, href);
    }

    function isStructuralModType(modType) {
        return modType && modType.toUpperCase() === 'STRUCTURAL';
    }

    // -----------------------------------------------------------
    // Displays the modifications validation grid
    // -----------------------------------------------------------
    function modificationsInfo(json) {

        if (json["modifications"]) {
            const modificationsStore = Ext4.create('Ext.data.Store', {
                storeId: 'modificationsStore',
                fields:  ['id', 'skylineModName', 'unimodId', 'unimodName', 'matchAssigned', 'valid', 'modType', 'dbModId', 'documents', 'unimodMatches', 'modInfoId'],
                data:    json,
                proxy:   { type: 'memory', reader: { type: 'json', root: 'modifications' }},
                sorters: [
                    {
                        property: 'modType',
                        direction: 'DESC' // Structural modifications first
                    },
                    {
                        property: 'dbModId',
                        direction: 'ASC'
                    }
                ]
            });

            let columns = [];
            let plugins = [];

            if (modificationsStore.getCount() > 0) {
                columns = [
                    {
                        text: 'Name',
                        dataIndex: 'skylineModName',
                        flex: 2,
                        renderer: function (v) { return htmlEncode(v); }
                    },
                    {
                        text: 'Unimod Match',
                        dataIndex: 'unimodId',
                        flex: 3,
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
                                if (record.data['modInfoId']) {
                                    const link = deleteModInfoLink(record.data['modInfoId'], record.data['skylineModName'],
                                            <%=experimentAnnotationsId%>, record.data['modType'], matches.length);
                                    ret += link.html;
                                    Ext4.defer(function()
                                    {
                                        // Attach the onclick handler in a deferred function so that it fires after the renderer function is executed.
                                        // This cannot be added to the "afterrender" listener since the renderer function is executing after "afterrender"
                                        // is fired.
                                        link.callback();
                                    }, 100);
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
                    }];

                plugins = [{
                    ptype: 'rowexpander',
                    expandOnDblClick: false,
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
                }];
            }

            var hasInferred = modificationsStore.find('matchAssigned', true) != -1;

            var grid = Ext4.create('Ext.grid.Panel', {
                store:    modificationsStore,
                storeId: 'modificationsStore',
                cls: 'pxv-modifications-panel',
                padding:  '10',
                disableSelection: true,
                collapsible: true,
                animCollapse: false,
                viewConfig: {enableTextSelection: true},
                title: 'Modifications',
                columns: columns,
                plugins: plugins
            });
            if (hasInferred) {
                var noteHtml = "Unimod Ids starting with <strong>**</strong> in the Unimod Match column were assigned based on the formula, "
                        + "modification site(s) and terminus in the modification definition.";
                grid.addDocked({
                    xtype: 'component',
                    dock: 'top',
                    padding: '10px',
                    margin: 0,
                    style: 'background-color:#efefef; border: 1px solid #b4b4b4;',
                    cls: 'labkey-error',
                    html: '<em>' + noteHtml + '</em>'
                });
            }
            if (modificationsStore.getCount() === 0) {
                grid.addDocked({
                    xtype: 'component',
                    dock: 'top',
                    padding: '10px',
                    margin: 0,
                    style: 'background-color:white; border: 1px solid #b4b4b4;',
                    cls: 'labkey-error',
                    html: '<em>No modifications found</em>'
                });
            }
            return grid;
        }
        return {xtype: 'label', text: 'Missing JSON property "modifications"'};
    }

    // -----------------------------------------------------------
    // Displays the sample files validation grid
    // -----------------------------------------------------------
    function skylineDocsInfo(json) {
        if (json["skylineDocuments"]) {
            return {xtype: 'pxv-skydocs-grid',
                    experimentAnnotationsId: <%=experimentAnnotationsId%>,
                    json: json,
                    includeSubfolders: <%=includeSubfolders%>
            }
        }
        return {xtype: 'label', text: 'Missing JSON for property "skylineDocuments"'};
    }

    // -----------------------------------------------------------
    // Displays the spectral libraries validation grid
    // -----------------------------------------------------------
    function spectralLibrariesInfo(json) {
        if (json["spectrumLibraries"]) {
            return {xtype: 'pxv-speclibs-grid',
                experimentAnnotationsId: <%=experimentAnnotationsId%>,
                json: json,
                includeSubfolders: <%=includeSubfolders%>
            }
        }
        return {xtype: 'label', text: 'Missing JSON for property "spectrumLibraries"'};
    }

</script>