<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<div id="panorama_public_search"></div>
<br/>
<div id="search-indicator"></div>
<div id="experiment_list_wp"></div>

<script type="text/javascript">

    // Require that ExtJS 4 be loaded
    LABKEY.requiresExt4Sandbox(function() {
        Ext4.onReady(function() {

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

            let handleRendering = function (btn, clicked) {
                let panel = btn.up('panel');
                const activeTab = panel.activeTab.getItemId();

                let expSearchPanel = panel.down('#' + expSearchPanelItemId);
                let proteinSearchPanel = panel.down('#' + proteinSearchPanelItemId);
                let peptideSearchPanel = panel.down('#' + peptideSearchPanelItemId);

                let expAnnotationFilters = [];
                let proteinParameters = {};
                let peptideParameters = {};

                // render experiment list webpart
                // add filters in qwp and in the url for back button
                if (clicked) {
                    updateUrlFilters(activeTab);
                    if (activeTab === expSearchPanelItemId) {
                        let author = expSearchPanel.down('#' + authorsItemId);
                        let title = expSearchPanel.down('#' + titleItemId);
                        let organism = expSearchPanel.down('#' + organismItemId);
                        let instrument = expSearchPanel.down('#' + instrumentItemId);

                        if (author && author.getValue()) {
                            expAnnotationFilters.push(LABKEY.Filter.create(authorsItemId, author.getValue(), LABKEY.Filter.Types.CONTAINS));
                            updateUrlFilters(null, authorsItemId, author.getValue());
                        }
                        if (title && title.getValue()) {
                            expAnnotationFilters.push(LABKEY.Filter.create(titleItemId, title.getValue(), LABKEY.Filter.Types.CONTAINS));
                            updateUrlFilters(null, titleItemId, title.getValue());
                        }
                        if (organism && organism.getValue()) {
                            expAnnotationFilters.push(LABKEY.Filter.create(organismItemId, organism.getValue(), LABKEY.Filter.Types.CONTAINS));
                            updateUrlFilters(null, organismItemId, organism.getValue());
                        }
                        if (instrument && instrument.getValue()) {
                            expAnnotationFilters.push(LABKEY.Filter.create(instrumentItemId, instrument.getValue(), LABKEY.Filter.Types.CONTAINS));
                            updateUrlFilters(null, instrumentItemId, instrument.getValue());
                        }
                    }
                    else if (activeTab === proteinSearchPanelItemId) {
                        let protein = proteinSearchPanel.down('#' + proteinNameItemId);
                        let exactProteinMatch = proteinSearchPanel.down('#' + exactProteinMatchesItemId);

                        if (protein && protein.getValue()) {
                            proteinParameters[proteinNameItemId] = protein.getValue();
                            updateUrlFilters(null, proteinNameItemId, protein.getValue());
                        }
                        if (exactProteinMatch && exactProteinMatch.getValue()) {
                            proteinParameters[exactMatch] = exactProteinMatch.getValue();
                            updateUrlFilters(null, exactProteinMatchesItemId, exactProteinMatch.getValue());
                        }
                    }
                    else if (activeTab === peptideSearchPanelItemId) {
                        let peptide = peptideSearchPanel.down('#' + peptideSequenceItemId);
                        let exactPeptideMatch = peptideSearchPanel.down('#' + exactPeptideMatchesItemId);

                        if (peptide && peptide.getValue()) {
                            peptideParameters[peptideSequenceItemId] = peptide.getValue();
                            updateUrlFilters(null, peptideSequenceItemId, peptide.getValue());
                        }
                        if (exactPeptideMatch && exactPeptideMatch.getValue()) {
                            peptideParameters[exactMatch] = exactPeptideMatch.getValue();
                            updateUrlFilters(null, exactPeptideMatchesItemId, exactPeptideMatch.getValue());
                        }
                    }
                }
                // getFiltersFromUrl and add to the filters
                else {
                    let context = getFiltersFromUrl();
                    if (context[authorsItemId]) {
                        expAnnotationFilters.push(LABKEY.Filter.create(authorsItemId, context[authorsItemId], LABKEY.Filter.Types.CONTAINS));
                    }
                    if (context[titleItemId]) {
                        expAnnotationFilters.push(LABKEY.Filter.create(titleItemId, context[titleItemId], LABKEY.Filter.Types.CONTAINS));
                    }
                    if (context[organismItemId]) {
                        expAnnotationFilters.push(LABKEY.Filter.create(organismItemId, context[organismItemId], LABKEY.Filter.Types.CONTAINS));
                    }
                    if (context[instrumentItemId]) {
                        expAnnotationFilters.push(LABKEY.Filter.create(instrumentItemId, context[instrumentItemId], LABKEY.Filter.Types.CONTAINS));
                    }
                    if (context[proteinNameItemId]) {
                        proteinParameters[proteinNameItemId] =  context[proteinNameItemId];
                    }
                    if (context[exactProteinMatchesItemId]) {
                        proteinParameters[exactMatch] =  context[exactProteinMatchesItemId];
                    }
                    if (context[peptideSequenceItemId]) {
                        peptideParameters[peptideSequenceItemId] =  context[peptideSequenceItemId];
                    }
                    if (context[exactPeptideMatchesItemId]) {
                        peptideParameters[exactMatch] =  context[exactPeptideMatchesItemId];
                    }
                }

                // render search qwps if search is clicked or page is reloaded (user hit back) and there are url parameters
                if (clicked || expAnnotationFilters.length > 0 ||
                        proteinParameters[proteinNameItemId] ||
                        peptideParameters[peptideSequenceItemId]
                ) {
                    Ext4.create('Ext.panel.Panel', {
                        border: false,
                        renderTo: 'search-indicator',
                    });
                    Ext4.get('search-indicator').mask('Search is running, results pending...');

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
                                Ext4.get('search-indicator').unmask();
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
                                Ext4.get('search-indicator').unmask();
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
                                Ext4.get('search-indicator').unmask();
                            }
                        });
                        wp.render();
                    }
                }
            };

            let getFiltersFromUrl = function () {
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
            };

            let updateUrlFilters = function (tabId, settingName, elementId) {
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
            };

            let clearHistory = function () {
                history.pushState("", document.title, window.location.pathname
                        + window.location.search);
            };

            let addSelectedTabToUrl = function (tabId) {
                window.location.href = window.location.href + '#searchTab:' + tabId;
            };

            let checkAndFillValuesFromUrl = function(itemId, comp) {
                let context = getFiltersFromUrl();
                if (context[itemId]) {
                    comp.setValue(context[itemId]);
                }
            };

            Ext4.define('PanoramaPublic.panel.SearchPanel', {
                extend: 'Ext.tab.Panel',
                alias: 'widget-panoramaSearchPanel',
                activeTab: 0,

                initComponent: function () {
                    Ext4.apply(this, {
                        defaults: { bodyPadding: 10, border: false },
                        activeTab: this.activeTab,
                        layout: 'fit',
                        items: [
                            {
                                xtype: 'panel',
                                name: expSearchPanelItemId,
                                itemId: expSearchPanelItemId,
                                title: 'Experiment Search',
                                cls: 'non-ext-search-tab-panel',
                                layout: {type: 'hbox', align: 'left'},
                                items:[{
                                    xtype: 'textfield',
                                    fieldLabel: 'Author',
                                    name: authorsItemId,
                                    itemId: authorsItemId,
                                    labelCls: 'labkey-form-label',
                                    labelWidth: 75,
                                    listeners: {
                                        render: function (comp, eOpts) {
                                            checkAndFillValuesFromUrl(authorsItemId, comp);
                                        }
                                    }
                                },
                                    {
                                        xtype: 'textfield',
                                        name: titleItemId,
                                        fieldLabel: titleItemId,
                                        itemId: titleItemId,
                                        labelCls: 'labkey-form-label',
                                        labelWidth: 75,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(titleItemId, comp);
                                            }
                                        }
                                    },
                                    {
                                        xtype: 'textfield',
                                        name: organismItemId,
                                        fieldLabel: organismItemId,
                                        itemId: organismItemId,
                                        labelCls: 'labkey-form-label',
                                        labelWidth: 75,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(organismItemId, comp);
                                            }
                                        }
                                    },
                                    {
                                        xtype: 'textfield',
                                        name: instrumentItemId,
                                        fieldLabel: instrumentItemId,
                                        itemId: instrumentItemId,
                                        labelCls: 'labkey-form-label',
                                        labelWidth: 75,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(instrumentItemId, comp);
                                            }
                                        }
                                    }
                                ]
                            },
                            {
                                // protein search webpart
                                xtype: 'panel',
                                name: proteinSearchPanelItemId,
                                itemId: proteinSearchPanelItemId,
                                title: 'Protein Search',
                                cls: 'non-ext-search-tab-panel',
                                layout: {type: 'hbox', align: 'left'},
                                items : [
                                    {
                                        xtype: 'textfield',
                                        fieldLabel: 'Protein Name',
                                        name: proteinNameItemId,
                                        itemId: proteinNameItemId,
                                        labelCls: 'labkey-form-label',
                                        labelWidth: 125,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(proteinNameItemId, comp);
                                                new Ext4.ToolTip({
                                                    target : comp.getEl(),
                                                    html: 'Required to search for proteins. You may use the name as specified by the FASTA file, or an annotation, such as a gene name, that has been loaded from an annotations file. You may comma separate multiple names.'
                                                });
                                            }
                                        }
                                    },
                                    {
                                        xtype: 'checkbox',
                                        fieldLabel: 'Exact Matches Only',
                                        name: exactProteinMatchesItemId,
                                        itemId: exactProteinMatchesItemId,
                                        labelCls: 'labkey-form-label',
                                        input: true,
                                        labelWidth: 125,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(exactProteinMatchesItemId, comp);
                                                new Ext4.ToolTip({
                                                    target : comp.getEl(),
                                                    html: 'If checked, the search will only find proteins with an exact name match. If not checked, proteins that contain the name entered will also match, but the search may be significantly slower.'
                                                });
                                            }
                                        }
                                    }
                                ]
                            },
                            {
                                // peptide search webpart
                                xtype: 'panel',
                                name: peptideSearchPanelItemId,
                                itemId: peptideSearchPanelItemId,
                                title: 'Peptide Search',
                                cls: 'non-ext-search-tab-panel',
                                layout: {type: 'hbox', align: 'left'},
                                items : [
                                    {
                                        xtype: 'textfield',
                                        name: 'Peptide Sequence',
                                        fieldLabel: 'Peptide Sequence',
                                        itemId: peptideSequenceItemId,
                                        labelCls: 'labkey-form-label',
                                        labelWidth: 125,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(peptideSequenceItemId, comp);
                                                new Ext4.ToolTip({
                                                    target : comp.getEl(),
                                                    html: 'Enter the peptide sequence to find, or multiple sequences separated by commas. Use * to match any sequence of characters.'
                                                });
                                            }
                                        }
                                    },
                                    {
                                        xtype: 'checkbox',
                                        fieldLabel: 'Exact Matches Only',
                                        name: exactPeptideMatchesItemId,
                                        itemId: exactPeptideMatchesItemId,
                                        labelCls: 'labkey-form-label',
                                        input: true,
                                        labelWidth: 125,
                                        listeners: {
                                            render: function (comp, eOpts) {
                                                checkAndFillValuesFromUrl(exactPeptideMatchesItemId, comp);
                                                new Ext4.ToolTip({
                                                    target : comp.getEl(),
                                                    html: 'If checked, the search will match the peptides exactly; if unchecked, it will match any peptide that contain the specified sequence and ignore modifications.'
                                                });
                                            }
                                        }
                                    }
                                ]
                            }],
                        dockedItems: [{
                            xtype: 'toolbar',
                            dock: 'bottom',
                            ui: 'footer',
                            items: [{
                                text: 'Search',
                                formBind: true,
                                handler: function (btn) {
                                    handleRendering(btn, true);
                                },
                                listeners: {
                                    render: function(comp, eOpts) {
                                        handleRendering(comp, false);
                                    }
                                },

                            }]
                        }],
                        scope: this,
                        listeners: {
                            tabchange: function (tabPanel, newCard, oldCard, eOpts ) {
                                updateUrlFilters(tabPanel.activeTab.itemId);
                                if (tabPanel.activeTab.itemId === expSearchPanelItemId) {
                                    let author = tabPanel.down('#' + authorsItemId);
                                    let title = tabPanel.down('#' + titleItemId);
                                    let organism = tabPanel.down('#' + organismItemId);
                                    let instrument = tabPanel.down('#' + instrumentItemId);

                                    if (author && author.getValue()) {
                                        updateUrlFilters(null, authorsItemId, author.getValue());
                                    }
                                    if (title && title.getValue()) {
                                        updateUrlFilters(null, titleItemId, title.getValue());
                                    }
                                    if (organism && organism.getValue()) {
                                        updateUrlFilters(null, organismItemId, organism.getValue());
                                    }
                                    if (instrument && instrument.getValue()) {
                                        updateUrlFilters(null, instrumentItemId, instrument.getValue());
                                    }
                                }
                                else if (tabPanel.activeTab.itemId === proteinSearchPanelItemId) {
                                    let protein = tabPanel.down('#' + proteinNameItemId);
                                    let exactMatch = tabPanel.down('#' + exactProteinMatchesItemId);

                                    if (protein && protein.getValue()) {
                                        updateUrlFilters(null, proteinNameItemId, protein.getValue());
                                    }
                                    if (exactMatch && exactMatch.getValue()) {
                                        updateUrlFilters(null, exactProteinMatchesItemId, exactMatch.getValue());
                                    }
                                }
                                else if (tabPanel.activeTab.itemId === peptideSearchPanelItemId) {
                                    let peptide = tabPanel.down('#' + peptideSequenceItemId);
                                    let exactMatch = tabPanel.down('#' + exactProteinMatchesItemId);

                                    if (peptide && peptide.getValue()) {
                                        updateUrlFilters(null, peptideSequenceItemId, peptide.getValue());
                                    }
                                    if (exactMatch && exactMatch.getValue()) {
                                        updateUrlFilters(null, exactPeptideMatchesItemId, exactMatch.getValue());
                                    }
                                }
                            },
                            render: function(comp, eOpts) {
                                let context = getFiltersFromUrl();
                                this.setActiveTab(context.searchTab);
                            }
                        }
                    });
                    this.callParent(arguments);
                },

            });

            Ext4.create('PanoramaPublic.panel.SearchPanel', {
                renderTo: 'panorama_public_search',
            })
        });
    });
</script>