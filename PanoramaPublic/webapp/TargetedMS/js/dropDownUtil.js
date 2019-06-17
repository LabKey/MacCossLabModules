/*
 * Copyright (c) 2014-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * * *
 * Created with IntelliJ IDEA.
 * User: Yuval Boss
 * Date: 2/10/14
 * Time: 12:55 PM
 * * *
 */

function viewExperimentDetails(obj, experimentContainer, id, detailsPageURL)
{
    var abstract = null;
    var expDetails = null;
    var sampleDetails = null;
    var loaded = obj.getAttribute('loaded');
    var active = obj.getAttribute('active');
    var currentRow = $(obj).closest('tr');
    var bgColor =  $(obj).closest('tr').css('background');
    var styles = 'style="background:'+bgColor+'; border-bottom:4px solid #DDDDDD;"';
    var totalCols = currentRow.eq(0).children("td").length;

    function onFailure(errorInfo, options, responseObj)
    {
        var error;
        if (errorInfo && errorInfo.exception)
            error ="Failure: " + errorInfo.exception;
        else
            error = "Failure: " + responseObj.statusText;
        var nextRow= currentRow.next();
        if($(nextRow).attr('display')!='open' && $(nextRow).attr('display')!='closed')
        {
        var newRow = "<tr class='openrow' display='open' id='openrow-"+id+"'><td "+styles+"colspan='"+totalCols+"' >"+error+"</td></tr>";
        currentRow.after(newRow);
        fadeIn();
        }

        if($(nextRow).attr('display')=='open')
        {
            $(nextRow).fadeOut(500)
            $(nextRow).attr('display', 'closed');
        }
        else {
            if($(nextRow).attr('display')=='closed')
            {
                $(nextRow).fadeIn(500)
                $(nextRow).attr('display', 'open');
            }
        }

        $(obj).attr("loaded", "false");
    }

    function verifyNewCol(object, type, rowNum)
    {
        if(object.rowCount === 0)
        {
            return null;
        }
        var results;
        if(object.rows[rowNum][type] != null)
        {
            if(object.rows[rowNum][type].length > 500)
            {
                results = object.rows[rowNum][type].substring(0,500)+"<a href='"+detailsPageURL+"'>...more.</a>";
            }
            else {
                results =object.rows[rowNum][type];
            }
        }
        else {results = null;}
        return results;
    }

    function onSuccess(data)
    {
        abstract = verifyNewCol(data, "Abstract",0);
        expDetails = verifyNewCol(data, "ExperimentDescription",0);
        sampleDetails = verifyNewCol(data, "SampleDescription",0);

        if(active == "true")
        {
            fadeOut();
        }

        if(active == "false")
        {
            var html = [];

            // Add another row for the details.  Make it hidden initially
            html.push("<tr style='display: none;'><td "+styles+"colspan='"+totalCols+"' class='openrow' id='openrow-"+id+"'>");
            if(abstract != null)
            {
                html.push("<div class='descriptionCols'><h1>Abstract</h1>"+abstract+"</div>");
            }
            if(expDetails != null)
            {
                html.push("<div class='descriptionCols'><h1>Experiment Description</h1>"+expDetails+"</div>");
            }
            if(sampleDetails != null)
            {
                html.push("<div class='descriptionCols'><h1>Sample Description</h1>"+sampleDetails+"</div></td></tr>");
            }

            var newRow = html.join("");
            currentRow.after(newRow);
            fadeIn();
            $(obj).attr("loaded", "true");
        }
    }

    //ensures that content is only loaded the first time user clicks [+]
    if(loaded == "false")
    {
    //AJAX query on request of viewExperimentDetails(..)
    LABKEY.Query.selectRows({
        schemaName: 'targetedms',
        queryName: 'ExperimentAnnotations',
        success: onSuccess,
        containerPath: experimentContainer,
        columns:['abstract', 'experimentdescription', 'sampledescription'],
        failure: onFailure,
        filterArray:[
            LABKEY.Filter.create('id', id)
    ]
    });
    }
    else
    {
        if(active == "true")
        {
            fadeOut();
        }
        else{
            fadeIn();
        }
    }

    function fadeIn()
    {
        currentRow.next().fadeIn(500)
        $(obj).attr("active", "true");
        $('#expandcontract-'+id).attr('src', '/labkey/_images/minus.gif');
    }

    function fadeOut()
    {
        currentRow.next().fadeOut(200);
        $(obj).attr("active", "false");
        $('#expandcontract-'+id).attr('src', '/labkey/_images/plus.gif');
    }
}


