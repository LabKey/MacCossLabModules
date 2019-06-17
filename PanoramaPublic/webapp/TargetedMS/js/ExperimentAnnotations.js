/*
 * Copyright (c) 2017-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function showShareLink(el, link)
{
    var imgUrl = LABKEY.contextPath + "/TargetedMS/images/clippy.png";
    var content = '<div><input type="text" id="accessUrlInput" class="shareLink" value="' + link + '" size=55"/>';
    content += '<img height="20" alt="Copy to clipboard" id="copyShareLink" class="copyShareLink" ';
    content += 'data-clipboard-text="' + link + '" src="' + imgUrl + '"></img>';
    content += '</div>';
    var calloutMgr = hopscotch.getCalloutManager();
    calloutMgr.removeAllCallouts();
    calloutMgr.createCallout({
        id: Ext4.id(),
        target: el,
        placement: 'bottom',
        width: 450,
        showCloseButton: true,
        content: content
    });

    var clipboard = new Clipboard("#copyShareLink");
    clipboard.on('success', function(e){
        console.info('Action', e.action);
        console.info('Text', e.text);
        console.info('Trigger:', e.trigger);
        var input = document.getElementById("accessUrlInput");
        input.focus();input.select();
        // e.clearSelection();
    });

    clipboard.on('error', function(e) {
        console.error('Action:', e.action);
        console.error('Trigger:', e.trigger);
    });

    return false;
}