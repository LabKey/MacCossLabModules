/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _assayType; // P100 or GCP
var _reportName; /* "GCT File P100" or "GCT File GCP"*/

function initGCTDownloadTable(assayType)
{
    // console.log("Initializing for assay type " + assayType);
    _assayType = assayType
    _reportName = "GCT File " + _assayType;

    // Get a list of Skyline files in this folder
    LABKEY.Query.selectRows({
        schemaName: 'targetedms',
        queryName: 'runs',
        columns: ['FileName', 'Id'],
        filterArray: [LABKEY.Filter.create('StatusId', '1')], // Show only fully imported Skyline documents
        success: displayFiles,
        sort: '-Created',
        failure: onError
    });
    // var customGCTUrl = LABKEY.ActionURL.buildURL("project", "begin", LABKEY.ActionURL.getContainer(), {pageId: "Custom GCT"});
    // console.log(customGCTUrl);
    // Ext.get("customGctButton").set({href: customGCTUrl});
}

function displayFiles(data)
{
    var container = LABKEY.ActionURL.getContainer();
    // console.log(container);
    console.log("Report: " + _reportName)

    var gctToExternalIdPrefix = "toexternal_";
    var gctProcToExternalIdPrefix = "toexternal_proc_";

    for ( var i = 0; i < data.rowCount; i += 1)
    {
        var runId = data.rows[i].Id;

        var params = {};
        params["runId"] = runId;
        params["reportName"] = _reportName;

        var gctDloadUrl = LABKEY.ActionURL.buildURL('lincs', 'RunGCTReport', container, params);

        params["processed"] = true;
        var procGctDloadUrl = LABKEY.ActionURL.buildURL('lincs', 'RunGCTReport', container, params);

        //console.log(gctDloadUrl);
        //console.log(procGctDloadUrl);

        var fileName = data.rows[i].FileName;
        var fileNameNoExt = getBaseFileName(fileName);
        console.log(fileNameNoExt);

        // http://www.blastam.com/blog/how-to-track-downloads-in-google-analytics
        // Tell the browser to wait 400ms before going to the download.  This is to ensure
        // that the GA tracking request goes through. Some browsers will interrupt the tracking
        // request if the download opens on the same page.
        var timeout = "that = this; setTimeout(function(){location.href=that.href;},400);return false;";
        var gaEventPush = "_gaq.push(['_trackEvent', 'Lincs', 'DownloadGCT', ";
        var gctFile = fileNameNoExt + '.gct';
        var procGctFile = fileNameNoExt + '.processed.gct';
        var analyticsEvtGct = " onclick=\"" + gaEventPush + "'" + gctFile + "']); " + timeout + "\" ";
        var analyticsEvtGctProc = " onclick=\"" + gaEventPush + "'" + procGctFile + "']); " + timeout + "\" ";

        var newRow = '<tr>';
        newRow += '<td>' + fileName + '</td>';
        newRow += '<td> <a ' + analyticsEvtGct + 'href="' + gctDloadUrl + '"> [GCT] </a> <span id="' + gctToExternalIdPrefix + i + '"></span>&nbsp;&nbsp;';
        newRow += '<a ' + analyticsEvtGctProc + 'href="' + procGctDloadUrl + '"> [Processed GCT] </a><span id="' + gctProcToExternalIdPrefix + i + '"></span></td>';
        newRow += '</tr>';
        // alert("Results returned: " + data.rows[i].FileName + ", " + data.rows[i].Id);
        // $("#skylinefiles tbody").append(newRow);
        var extRow = new Ext4.Template(newRow);
        extRow.append('skylinefiles');

        externalHeatmapViewerLink(container, gctFile, gctToExternalIdPrefix + i, _assayType);
        externalHeatmapViewerLink(container, procGctFile, gctProcToExternalIdPrefix + i, _assayType);
    }

}

function getBaseFileName(fileName)
{
    var idx = fileName.indexOf('.sky.zip');
    if(idx == -1)
    {
        idx = fileName.indexOf(".zip");
    }
    return fileName.substring(0, idx);
}

function externalHeatmapViewerLink(container, fileName, elementId, assayType)
{
    var fileUrl= LABKEY.ActionURL.buildURL("_webdav", "REMOVE", container + '/@files/GCT/' + fileName);
    fileUrl= fileUrl.substring(0, fileUrl.indexOf("/REMOVE"));
    fileUrl= LABKEY.ActionURL.getBaseURL(true) + fileUrl;
    // console.log("File URL is " + fileUrl);

    var morpheusUrl = getMorpheusUrl(fileUrl, assayType);

    var analyticsEvt = " onclick=\"_gaq.push(['_trackEvent', 'Lincs', 'Morpheus', '" + fileName + "']);\" ";

    Ext4.Ajax.request({
        url: fileUrl,
        method: 'HEAD',
        success: function(response, opts) {
            var imgUrl = LABKEY.ActionURL.getContextPath() + "/lincs/GENE-E_icon.png";
            Ext4.get(elementId).dom.innerHTML = '(<a target="_blank" ' + analyticsEvt + 'href="' + morpheusUrl + '">View in Morpheus</a> <img src=' + imgUrl + ' width="13", height="13"/>)';
        },
        failure: function(response, opts) {
            console.log('server-side failure with status code ' + response.status);
        }
    });

}

function getMorpheusUrl(fileUrl, assayType)
{
    var morpheusJson = '{"dataset":"' + fileUrl + '",';
    if(assayType === 'P100')
    {
        morpheusJson += '"rows":[{"field":"pr_p100_modified_peptide_code","display":"Text"},{"field":"pr_gene_symbol","display":"Text"},{"field":"pr_p100_phosphosite","display":"Text"},{"field":"pr_uniprot_id","display":"Text"}],';
    }
    if(assayType === 'GCP')
    {
        morpheusJson += '"rows":[{"field":"pr_gcp_histone_mark","display":"Text"},{"field":"pr_gcp_modified_peptide_code","display":"Text"}],'
    }
    morpheusJson += '"columns":[{"field":"pert_iname","display":"Text"},{"field":"det_well","display":"Text"}],';
    morpheusJson += '"colorScheme":{"type":"fixed","map":[{"value":-3,"color":"blue"},{"value":0,"color":"white"},{"value":3,"color":"red"}]}';
    morpheusJson += '}';

    var morpheusUrl= "http://www.broadinstitute.org/cancer/software/morpheus/?json=";
    morpheusUrl += encodeURIComponent(morpheusJson);
    console.log("Morpheus URL " + morpheusUrl);
    return morpheusUrl;
}


function onError(errorInfo, options, responseObj)
{
    if (errorInfo && errorInfo.exception)
        alert("Failure: " + errorInfo.exception);
    else
        alert("Failure: " + responseObj.statusText);
}