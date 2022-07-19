/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function externalHeatmapViewerLink(container, fileName, elementId, assayType)
{
    var fileUrl= LABKEY.ActionURL.buildURL("_webdav", "REMOVE", container + '/@files/GCT/' + fileName);
    fileUrl= fileUrl.substring(0, fileUrl.indexOf("/REMOVE"));
    fileUrl= LABKEY.ActionURL.getBaseURL(true) + fileUrl;
    // console.log("File URL is " + fileUrl);

    var morpheusUrl = getMorpheusUrl(fileUrl, assayType);

    var analyticsEvt = " onclick=\"try {_gaq.push(['_trackEvent', 'Lincs', 'Morpheus', '" + fileName + "']);} catch (err) {} try {gtag.event('Lincs', {eventAction: 'Morpheus', fileName: '" + fileName + "'});} catch (err) {}\" ";

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