/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function externalHeatmapViewerLink(fileUrl, fileName, elementId, assayType, addAnalyticsTracking)
{
    const morpheusUrl = getMorpheusUrl(fileUrl, assayType);

    Ext4.Ajax.request({
        url: fileUrl,
        method: 'HEAD',
        success: function(response, opts) {
            const imgUrl = LABKEY.ActionURL.getContextPath() + "/lincs/GENE-E_icon.png";

            const targetElement = Ext4.get(elementId);
            if (!targetElement) {
                console.error('Element with Id not found:', elementId);
                return;
            }

            // Create link element
            const link = document.createElement('a');
            link.href = morpheusUrl;
            link.target = '_blank';
            link.textContent = 'View in Morpheus ';

            // Create image element
            const img = document.createElement('img');
            img.src = imgUrl;
            img.width = 13;
            img.height = 13;
            img.alt = 'GENE-E icon';

            link.appendChild(img);

            const wrapper = document.createElement('span');
            wrapper.appendChild(document.createTextNode('['));
            wrapper.appendChild(link);
            wrapper.appendChild(document.createTextNode(']'));

            targetElement.dom.appendChild(wrapper);

            if (addAnalyticsTracking)
            {
                // Attach the event handler
                link['onclick'] = function () {
                    try
                    {
                        gtag('event', 'Lincs', {
                            eventAction: 'Morpheus',
                            fileName: fileName
                        });
                    }
                    catch (err)
                    {
                        console.warn('Failed to track Morpheus click:', err);
                    }
                };
            }
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