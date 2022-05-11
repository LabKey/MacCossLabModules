const htmlEncode = Ext4.util.Format.htmlEncode;

const returnUrl = LABKEY.ActionURL.buildURL(LABKEY.ActionURL.getController(), LABKEY.ActionURL.getAction(),
        LABKEY.ActionURL.getContainer(), LABKEY.ActionURL.getParameters());

link = function (text, href, cssCls, sameTab, style) {
    const cls = cssCls ? ' class="' + cssCls + '" ' : '';
    let target = ' target="_blank" ';
    let rel = ' rel="noopener noreferrer" ';
    if (sameTab && sameTab === true) {
        target = "";
        rel = "";
    }
    return '<a ' + cls + (style ? ' style="' + style + '" ' : "") + ' href="' + htmlEncode(href) + '" ' + target + rel + ' >' + htmlEncode(text) + '</a>';
}

documentLink = function (documentName, containerPath, runId) {
    // If there is no container path it means that the document or the containing container was deleted. Do not render a link in that case
    return containerPath ? link(documentName, LABKEY.ActionURL.buildURL('targetedms', 'showPrecursorList', containerPath, {id: runId})) : documentName;
}

missing = function () {
    return '<span class="pxv-invalid pxv-bold">MISSING</span>';
}

const sampleFilesTableTpl = new Ext4.XTemplate(
        '<table class="{tblCls} pxv-tpl-table">',
        '<thead><tr><th>Replicate</th><th>File</th><th>Status</th><th>Path</th><tr></thead>',
        '<tpl for="files">',
        '<tr> <td>{replicate:htmlEncode}</td> <td>{name:htmlEncode}</td> {[this.renderStatus(values)]}  <td>{[this.renderPath(values)]}</td></tr>', // tdTpl.apply(['{name}']),
        '</tpl>',
        '</table>',
        '<div>{container}</div>',
        {
            renderStatus: function (values) {
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
            renderPath: function (values) {
                return htmlEncode(values.path);
            },
            compiled: true, disableFormats: true
        }
);

const headerRowTpl = new Ext4.XTemplate(
        '<thead> <tr> <tpl for="."> <th>{.}</th> </tpl> </tr> </thead>',
        { compiled:true, disableFormats:true }
);

const libSourceFilesTableTpl = new Ext4.XTemplate(
        '<tpl if="title.length &gt; 0">', '<div class="pxv-tpl-table-title">{title:htmlEncode}</div>', '</tpl>',
        '<table class="{tblCls} pxv-tpl-table">',
        '<thead><tr><th>File</th><th>Status</th><th>Path</th><tr></thead>',
        '<tpl for="files">',
        '<tr> <td>{name:htmlEncode}</td> {[this.renderStatus(values)]}  <td>{path:htmlEncode}</td></tr>',
        '</tpl>',
        '</table>',
        {
            renderStatus: function (values) {
                return renderFileStatus(values, false)
            },
            compiled: true, disableFormats: true
        }
);

renderFileStatus: function renderFileStatus(values, isSampleFile, link) {
    const cls = (values.found === true ? 'pxv-valid' : 'pxv-invalid') + ' pxv-bold';
    let status = '';
    if (values.found === true) status = "FOUND";
    else if (values.ambiguous === true) status = "AMBIGUOUS"
    else status = "MISSING";
    return '<td><span class="' + cls + '">' + htmlEncode(status) + (link ? '<span style="margin-left: 5px;">' + link + '</span>' : "") + '</span></td>';
}

expandGridNodes = function(store, expander) {
    for (let i=0; i < store.getCount(); i++ ) {
        const record = store.getAt(i);
        if(expander.recordsExpanded && !expander.recordsExpanded[record.internalId]){
            expander.toggleRow(i, record);
        }
    }
}

Ext4.define('LABKEY.pxvalidation.SkyDocsGridPanel', {
    extend: 'Ext.grid.Panel',
    xtype: 'pxv-skydocs-grid',
    cls: 'pxv-skydocs-panel',
    collapsible: true,
    animCollapse: false,
    padding: 10,
    disableSelection: true,
    viewConfig: {enableTextSelection: true},
    title: 'Skyline Document Sample Files',

    constructor: function (config) {

        if (!config.experimentAnnotationsId)
            throw "experimentAnnotationsId is required.";
        if (!config.json)
            throw "JSON is required to render the grid.";
        if (!("showUploadButton" in config)) config.showUploadButton = true;
        if (!("expandNodes" in config)) config.expandNodes = false;
        if (!("includeSubfolders" in config)) config.includeSubfolders = false;

        Ext4.apply(config, {
            store: this.initStore(config.json),
            columns: this.initColumns(config),
            plugins: this.initPlugins(config),
            listeners: {
                viewready: function(table, opts){
                    if (config.expandNodes) expandGridNodes(table.getStore(), table.plugins[0]);
                }
            }
        });

        this.callParent([config]);
    },

    initStore: function (json) {
        const store = Ext4.create('Ext.data.Store', {
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
        // console.log("Store count: " + store.getCount());
        return store
    },

    initColumns: function (config) {
        let columns = [
            {
                text: 'Name',
                dataIndex: 'name',
                flex: 1,
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
                    return value === true ? "COMPLETE" : "INCOMPLETE";
                }
            }
        ];

        if (config.includeSubfolders) {
            columns.push(
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
                    }
            );
        }
        return columns;
    },

    initPlugins: function(config) {
        return [{
            ptype: 'rowexpander',
            rowBodyTpl : new Ext4.XTemplate(
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
                            if (config.showUploadButton &&
                                    values['container'] // If container value is missing it means that the document or the containing container was deleted.
                                    && values['valid'] === false) {
                                const sampleFiles = values['sampleFiles'];
                                const missing = sampleFiles.find(sf => sf['found'] === false && sf['ambiguous'] === false);
                                if (!missing) return "";
                                return '<div style="margin: 8px;">'
                                        + link('Upload Files', LABKEY.ActionURL.buildURL('panoramapublic', 'uploadSampleFiles', values['container'],
                                                {id: config.experimentAnnotationsId, returnUrl: returnUrl}), 'labkey-button', true)
                                        + '</div>';
                            }
                            return "";
                        },
                        ambiguousFilesMsg: function(sampleFiles) {
                            let ambiguousFilesMsg = "";
                            if (sampleFiles && sampleFiles.length > 0) {
                                let hasAmbiguous = false;
                                for (const file of sampleFiles) {
                                    if (file['ambiguous'] === true) {
                                        hasAmbiguous = true;
                                        break;
                                    }
                                }
                                if (hasAmbiguous) {
                                    ambiguousFilesMsg = '<div class="pxv-invalid"><em>Files marked as ambiguous have the same name in one or more documents in the folder but are different ' +
                                            'files based their acquired time on the mass spectrometer. ' +
                                            'Click the View Files link in the table below to view sample files with the same name. ' +
                                            '<br>' +
                                            'File names imported into Skyline documents being submitted must have unique names if they are different files.' + '</em></div>';
                                }
                            }
                            return ambiguousFilesMsg;
                        },
                        compiled:true
                    }
            )
        }];
    }
});

Ext4.define('LABKEY.pxvalidation.SpecLibsGridPanel', {
    extend: 'Ext.grid.Panel',
    xtype: 'pxv-speclibs-grid',
    cls: 'pxv-speclibs-panel',
    collapsible: true,
    animCollapse: false,
    padding: 10,
    disableSelection: true,
    viewConfig: {enableTextSelection: true},
    title: 'Spectral Libraries',

    constructor: function (config) {

        if (!config.experimentAnnotationsId)
            throw "experimentAnnotationsId is required.";
        if (!config.json)
            throw "JSON to render is required.";
        if (!("showUploadButton" in config)) config.showUploadButton = true;
        if (!("expandNodes" in config)) config.expandNodes = false;
        if (!("includeSubfolders" in config)) config.includeSubfolders = false;
        if (!("showLibInfo" in config)) config.showLibInfo = true;

        const store = this.initStore(config.json);
        let columns = [];
        let plugins = [];
        let listeners = [];
        if (store.getCount() === 0) {
            config['dockedItems'] = [{
                xtype: 'component',
                dock: 'top',
                padding: '10px',
                margin: 0,
                style: 'background-color:white; border: 1px solid #b4b4b4;',
                cls: 'labkey-error',
                html: '<em>No spectral libraries found</em>'
            }];
        }
        else {
            columns = this.initColumns();
            plugins = this.initPlugins(config);
            listeners = {
                viewready: function (table, opts) {
                    if (config.expandNodes) expandGridNodes(table.getStore(), table.plugins[0]);
                }
            }
        }

        Ext4.apply(config, {
            store: store,
            columns: columns,
            plugins: plugins,
            listeners: listeners
        });

        this.callParent([config]);
    },

    initStore: function (json) {
        const store = Ext4.create('Ext.data.Store', {
            storeId: 'specLibStore',
            fields: ['id', 'libName', 'libType', 'fileName', 'size',
                'valid', 'validWithoutSpecLibInfo', 'status', 'helpMessage', 'prositLibrary',
                'specLibInfo', 'specLibInfoId', 'iSpecLibId',
                'spectrumFiles', 'idFiles', 'documents'],
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
                    property: 'id',
                    direction: 'ASC'
                }
            ]
        });
        return store;
    },

    initColumns: function () {
        return [
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
            }
        ];
    },

    initPlugins: function(config) {
        return [{
            ptype: 'rowexpander',
            rowBodyTpl : new Ext4.XTemplate(

                    '<div class="pxv-grid-expanded-row">',
                    '<div>{[this.renderLibraryStatus(values)]}</div>',
                    '<div>{[this.uploadButton(values)]}</div>',
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

                            if (values['prositLibrary'])
                                return '<div><span><em>' + htmlEncode(values.status) + '</em></span></div>';

                            if (values['validWithoutSpecLibInfo']) return '';

                            return this.statusHtml(values) + (config.showLibInfo ? this.libInfoHtml(values) : "");
                        },
                        statusHtml: function(values) {

                            let helpMessageHtml = '';
                            if (values['valid'] === false && values['helpMessage']) {
                                // Show the help message (e.g. rebuild the library with latest Skyline...) only if there is no overriding
                                // library information (e.g. "irrelevant to results") that makes the library valid.
                                helpMessageHtml = '<div style="margin-top:6px;"><em>' + values['helpMessage'] + '</em></div>';
                            }

                            return '<div style="margin-bottom:6px;">'
                                    + '<div>'
                                    + '<span style="margin-right:5px;">Library could not be validated because:</span>'
                                    + '<span class="pxv-invalid"><em>' + values['status'] + '</em></span>'
                                    + helpMessageHtml
                                    + '</div></div>';
                        },
                        libInfoHtml: function(values) {

                            if (values['specLibInfoId']) {
                                const params = {
                                    'schemaName': 'panoramapublic',
                                    'queryName': 'SpectralLibraries',
                                    'viewName': 'SpectralLibrariesInfo',
                                    'query.SpecLibInfoId~eq': values['specLibInfoId'],
                                    'returnUrl': returnUrl
                                };
                                if (config.includeSubfolders) {
                                    params['query.containerFilterName'] = 'CurrentAndSubfolders';
                                }
                                const href = LABKEY.ActionURL.buildURL('query', 'executeQuery', LABKEY.ActionURL.getContainer(), params);
                                const specLibInfoLink = link("[View Library Information]", href, 'labkey-text-link', true);

                                let specLibInfoHtml = '';
                                let libInfoCls = '';
                                if (values['valid'] === true && values['validWithoutSpecLibInfo'] === false) {
                                    // The library is valid but only because the user added overriding information, e.g. "irrelevant library"
                                    specLibInfoHtml = '<span style="margin-right:5px;">Library is considered <em><b>complete</b></em> due to the library information: </span>';
                                    libInfoCls = 'pxv-valid';
                                }
                                else specLibInfoHtml = "<b>Library Information: </b>";
                                specLibInfoHtml += '<em style="margin-right:8px;"><span class="' + libInfoCls + '">' + values['specLibInfo'] + '</span></em>';
                                return '<div style="margin:6px 0 6px 0;">' + specLibInfoHtml + specLibInfoLink + '</div>';
                            }
                            else if (values['iSpecLibId']) {
                                const params = {
                                    'id': config.experimentAnnotationsId,
                                    'specLibId': values['iSpecLibId'],
                                    'returnUrl': returnUrl
                                };
                                const href = LABKEY.ActionURL.buildURL('panoramapublic', 'editSpecLibInfo', LABKEY.ActionURL.getContainer(), params);
                                return link("[Add Library Information]", href, 'labkey-text-link', true);
                            }
                        },
                        renderTable: function(dataFiles, tblCls, title) {
                            return libSourceFilesTableTpl.apply({files: dataFiles, tblCls: tblCls, title: title});
                        },
                        docLink: function (doc) {
                            return documentLink(doc.name, doc.container, doc.runId);
                        },
                        uploadButton: function(values) {
                            if (config.showUploadButton && values['valid'] === false
                                    && (values['spectrumFiles'].length > 0 || values['idFiles'].length > 0)) {
                                return '<div style="margin: 10px;">'
                                        + link('Upload Files', LABKEY.ActionURL.buildURL('panoramapublic', 'uploadSpecLibSourceFiles', values['container'],
                                                {id: config.experimentAnnotationsId, returnUrl: returnUrl}), 'labkey-button', true)
                                        + '</div>';
                            }
                            return "";
                        },
                        compiled:true
                    }
            )
        }];
    }
});
