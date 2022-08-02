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

            Ext4.create('Ext.tab.Panel', {
                renderTo: 'panorama_public_search',
                defaults: { bodyPadding: 10, border: false },
                activeTab: 0,
                layout: 'fit',
                items: [
                    {
                        xtype: 'panel',
                        itemId: 'expSearchPanel',
                        title: 'Experiment Search',
                        cls: 'non-ext-search-tab-panel',
                        layout: {type: 'hbox', align: 'left'},
                        items:[{
                            xtype: 'textfield',
                            fieldLabel: 'Author',
                            itemId: 'author',
                            labelCls: 'labkey-form-label',
                            labelWidth: 75
                        },
                        {
                            xtype: 'textfield',
                            fieldLabel: 'Title',
                            itemId: 'title',
                            labelCls: 'labkey-form-label',
                            labelWidth: 75
                        },
                        {
                            xtype: 'textfield',
                            fieldLabel: 'Organism',
                            itemId: 'organism',
                            labelCls: 'labkey-form-label',
                            labelWidth: 75
                        },
                        {
                            xtype: 'textfield',
                            fieldLabel: 'Instrument',
                            itemId: 'instrument',
                            labelCls: 'labkey-form-label',
                            labelWidth: 75
                        }
                        ]
                    },
                    {
                    // protein search webpart
                    xtype: 'panel',
                    title: 'Protein Search',
                    cls: 'non-ext-search-tab-panel',
                    items : [{
                        xtype: 'component',
                        border : false,
                        listeners : {
                            scope: this,
                            afterrender : function(cmp) {
                                var wp = new LABKEY.WebPart({
                                    partName: "Panorama Public Protein Search",
                                    frame: 'none',
                                    renderTo: cmp.getId(),
                                    success: function() { cmp.up('panel').doLayout(); }
                                });
                                wp.render();
                            }
                        }
                    }]
                },{
                    // peptide search webpart
                    title: 'Peptide Search',
                    cls: 'non-ext-search-tab-panel',
                    items : [{
                        xtype: 'component',
                        border : false,
                        listeners : {
                            scope: this,
                            afterrender : function(cmp) {
                                var wp = new LABKEY.WebPart({
                                    partName: 'Panorama Public Peptide Search',
                                    frame: 'none',
                                    renderTo: cmp.getId(),
                                    partConfig: {subfolders: true},
                                    success: function() { cmp.up('panel').doLayout(); }
                                });
                                wp.render();
                            }
                        }
                    }]
                },{
                    // modification search webpart from the targetedms module
                    title: 'Modification Search',
                    items : [{
                        xtype: 'component',
                        border : false,
                        listeners : {
                            scope: this,
                            afterrender : function(cmp) {
                                var wp = new LABKEY.WebPart({
                                    partName: 'Targeted MS Modification Search',
                                    frame: 'none',
                                    renderTo: cmp.getId(),
                                    partConfig: {hideIncludeSubfolder: true, includeSubfolders: true},
                                    success: function() { cmp.up('panel').doLayout(); }
                                });
                                wp.render();
                            }
                        }
                    }]
                }],
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui: 'footer',
                    items: [{
                        text: 'Search',
                        formBind: true,
                        handler: function (btn) {
                            var panel = btn.up('panel');
                            var expSearchPanel = panel.down('#expSearchPanel');
                            var author = expSearchPanel.down('#author');
                            var title = expSearchPanel.down('#title');
                            var organism = expSearchPanel.down('#organism');
                            var instrument = expSearchPanel.down('#instrument');
                            var expAnnotationFilters = [];

                            // render experiment list webpart
                            // add filters in qwp and in the url for back button
                            if (author && author.getValue()) {
                                expAnnotationFilters.push(LABKEY.Filter.create('Authors', author.getValue(), LABKEY.Filter.Types.CONTAINS))
                            }
                            if (title && title.getValue()) {
                                expAnnotationFilters.push(LABKEY.Filter.create('Title', title.getValue(), LABKEY.Filter.Types.CONTAINS))
                            }
                            if (organism && organism.getValue()) {
                                expAnnotationFilters.push(LABKEY.Filter.create('Organism', organism.getValue(), LABKEY.Filter.Types.CONTAINS))
                            }
                            if (instrument && instrument.getValue()) {
                                expAnnotationFilters.push(LABKEY.Filter.create('Instrument', instrument.getValue(), LABKEY.Filter.Types.CONTAINS))
                            }


                            Ext4.create('Ext.panel.Panel', {
                                border: false,
                                renderTo: 'search-indicator',
                            });
                            Ext4.get('search-indicator').mask('Search is running, results pending...');

                            var wp = new LABKEY.QueryWebPart({
                                renderTo: 'experiment_list_wp',
                                schemaName: 'panoramapublic',
                                queryName: 'ExperimentAnnotations',
                                filters: expAnnotationFilters,
                                success: function() {
                                    Ext4.get('search-indicator').unmask();
                                }
                            });
                            wp.render();

                        }
                    }]
                }],
                // add listener on load
                // check for params in url and render the apt web parts
            });
        });
    });
</script>