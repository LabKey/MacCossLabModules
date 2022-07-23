<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<table>
    <tr>
        <td style="vertical-align:top;padding-right:30px;">
            <div style="font-weight:bold; font-size:14px; text-decoration:underline; margin-top:5px;">Experiment Search</div>
            <div style="border-width:1px; border-style:solid; border-color:#b4b4b4; padding:10px;">
                <table>
                    <tr>
                        <td class="labkey-form-label">Author</td><td colspan="2"><input value="" name="author" id="search_author" size="25" type="text"></td>
                        <!--</tr>
                        <tr>-->
                        <td class="labkey-form-label" style="margin-left:10px;">Title</td><td colspan="2"><input value="" name="title" id="search_title" size="25" type="text"></td>
                    </tr>
                    <tr>
                        <td class="labkey-form-label">Organism</td><td colspan="2"><input value="" name="organism" id="search_organism" size="25" type="text"></td>
                        <!--</tr>
                        <tr>-->
                        <td class="labkey-form-label" style="margin-left:10px;">Instrument</td><td colspan="2"><input value="" name="instrument" id="search_instrument" size="25" type="text"></td>
                    </tr>
                    <tr><td align="center" style="padding-top:23px;"><a class="labkey-button" onClick="refreshPage();return false;">Search</a></td></tr>
                </table>
            </div>

        </td>
        <td style="vertical-align:top;">
            <div style="font-weight:bold; font-size:14px; text-decoration:underline; margin-top:5px;">Mass Spec. Search</div>
            <div id="panorama_public_search"></div>
        </td>
    </tr>
</table>

<script type="text/javascript">

    function parseUrlQueryParams()
    {
        var query= location.search.substr(1);
        query.split("&").forEach(function (part) {
            var item = part.split("=");
            var name = decodeURIComponent(item[0]);
            var value = decodeURIComponent(item[1]);
            if(name.endsWith("List.Authors~contains")) {document.getElementById("search_author").value = value;}
            if(name.endsWith("List.Title~contains")) {document.getElementById("search_title").value = value;}
            if(name.endsWith("List.Organism~contains")) {document.getElementById("search_organism").value = value;}
            if(name.endsWith("List.Instrument~contains")) {document.getElementById("search_instrument").value = value;}
        });
    }

    function refreshPage()
    {
        var author = document.getElementById("search_author").value;
        var title = document.getElementById("search_title").value;
        var organism = document.getElementById("search_organism").value;
        var instrument = document.getElementById("search_instrument").value;

        var searchParams = "";
        var amp = "";
        if(author && author !== "")
        {
            searchParams += amp + makeUriComponent("Authors", author);
            amp = "&";
        }
        if(title && title !== "")
        {
            searchParams += amp + makeUriComponent("Title", title);
            amp = "&";
        }

        if(organism && organism!== "")
        {
            searchParams += amp + makeUriComponent("Organism", organism);
            amp = "&";
        }
        if(instrument && instrument !== "")
        {
            searchParams += amp + makeUriComponent("Instrument", instrument);
            amp = "&";
        }

        console.log(searchParams);

        var url = new URL(document.location.href);
        var newUrl = url.protocol + "//" + url.hostname + url.pathname + "?" + searchParams;
        //newUrl = "https://panoramaweb.org/labkey/project/Panorama%20Public/begin.view?" + searchParams;
        console.log(newUrl);
        window.location.href = newUrl;

    }

    function makeUriComponent(colName, value)
    {
        // return encodeURIComponent("Targeted MS Experiment List." + colName + "~contains") + "=" + encodeURIComponent(value);
        return "Targeted MS Experiment List." + colName + "~contains" + "=" + value;
    }

    // Require that ExtJS 4 be loaded
    LABKEY.requiresExt4Sandbox(function() {
        Ext4.onReady(function() {

            parseUrlQueryParams();
            document.getElementById("search_author").addEventListener("keyup", function(event) {event.preventDefault(); if(event.keyCode === 13) {refreshPage();}});
            document.getElementById("search_title").addEventListener("keyup", function(event) {event.preventDefault(); if(event.keyCode === 13) {refreshPage();}});
            document.getElementById("search_organism").addEventListener("keyup", function(event) {event.preventDefault(); if(event.keyCode === 13) {refreshPage();}});
            document.getElementById("search_instrument").addEventListener("keyup", function(event) {event.preventDefault(); if(event.keyCode === 13) {refreshPage();}});

            Ext4.create('Ext.tab.Panel', {
                renderTo: 'panorama_public_search',
                defaults: { bodyPadding: 10, flex: 1, border: false },
                activeTab: 0,
                layout: 'fit',
                items: [{
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
                }
                    ,{
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
                    }
                ]
            });
        });
    });
</script>