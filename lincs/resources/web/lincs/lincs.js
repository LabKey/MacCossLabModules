var reportName; /* "GCT File P100" or "GCT File GCP"*/
function initGCTDownloadTable(report)
{
    // console.log("Initializing..");
    reportName = report;

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
    console.log("Report: " + reportName)

    var gctToExternalIdPrefix = "toexternal_";
    var gctProcToExternalIdPrefix = "toexternal_proc_";

    for ( var i = 0; i < data.rowCount; i += 1)
    {
        var runId = data.rows[i].Id;

        var gctParams = {runId: runId, reportName: reportName, "GCT_input_peptidearearatio.RunId~eq": runId};
        var processedGctParams = {runId: runId, reportName: reportName, "GCT_input_peptidearearatio.RunId~eq": runId, processed: true};

        var gctDloadUrl = LABKEY.ActionURL.buildURL('lincs', 'RunGCTReport', container, gctParams);
        var procGctDloadUrl = LABKEY.ActionURL.buildURL('lincs', 'RunGCTReport', container, processedGctParams);

        //console.log(gctDloadUrl);
        //console.log(procGctDloadUrl);

        var fileName = data.rows[i].FileName;
        var fileNameNoExt = getBaseFileName(fileName);
        console.log(fileNameNoExt);

        var newRow = '<tr>';
        newRow += '<td>' + fileName + '</td>';
        newRow += '<td> <a href="' + gctDloadUrl + '"> [GCT] </a> <span id="' + gctToExternalIdPrefix + i + '"></span>&nbsp;&nbsp;';
        newRow += '<a href="' + procGctDloadUrl + '"> [Processed GCT] </a><span id="' + gctProcToExternalIdPrefix + i + '"></span></td>';
        newRow += '</tr>';
        // alert("Results returned: " + data.rows[i].FileName + ", " + data.rows[i].Id);
        // $("#skylinefiles tbody").append(newRow);
        var extRow = new Ext4.Template(newRow);
        extRow.append('skylinefiles');

        externalHeapmapViewerLink(container, fileNameNoExt, '.gct', gctToExternalIdPrefix + i);
        externalHeapmapViewerLink(container, fileNameNoExt, '.processed.gct', gctProcToExternalIdPrefix + i);

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

function externalHeapmapViewerLink(container, fileNameNoExt, extension, elementId)
{
    var fileUrl= LABKEY.ActionURL.buildURL("_webdav", "REMOVE", LABKEY.ActionURL.getContainer() + '/@files/GCT/' + fileNameNoExt + extension);
    fileUrl= fileUrl.substring(0, fileUrl.indexOf("/REMOVE"));
    fileUrl= LABKEY.ActionURL.getBaseURL(true) + fileUrl;
    console.log("File URL is" + fileUrl);

    var morpheusUrl = getMorpheusUrl(fileUrl);

    Ext.Ajax.request({
        url: fileUrl,
        method: 'HEAD',
        success: function(response, opts) {
            var imgUrl = LABKEY.ActionURL.getContextPath() + "/lincs/GENE-E_icon.png";
            Ext.get(elementId).dom.innerHTML = '(<a target="_blank" href="' + morpheusUrl + '">View in Morpheus</a> <a href="' + morpheusUrl + '"><img src=' + imgUrl + ' width="13", height="13"/></a>)';
        },
        failure: function(response, opts) {
            console.log('server-side failure with status code ' + response.status);
        }
    });

}

function getMorpheusUrl(fileUrl)
{
    var morpheusJson = '{"dataset":"' + fileUrl + '",';
    morpheusJson += '"rows":[{"field":"pr_p100_modified_peptide_code","display":"Text"},{"field":"pr_gene_symbol","display":"Text"},{"field":"pr_p100_phosphosite","display":"Text"},{"field":"pr_uniprot_id","display":"Text"}],';
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