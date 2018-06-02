/**
 * // Created by Yuval Boss yuval (at) uw.edu | 4/4/16
 */
var margin = {top: 20, right: 40, bottom: 40, left: 80}; // d3 peptide chart
var barChartData = []; // d3 peptide chart DO NOT MODIFY ONLY SORT
var peakAreaDiv, proteinBarDiv; // viz module div containers
var settings = null;
var longestPeptide = 0;
var selectedPeptide = null;

    function Settings(updateCallback) {
    var update = function() { updateCallback(); }; // private update callback
    var degradationPercentRange = {
        start:0,
        end: 100
    };
    var sequenceLength = {
        start: 0,
        end: 0
    }
    var sortBy = "Intensity";
    var featuresVisible = false;

    this.setFeaturesVisible = function(visible) {
        if(typeof visible === 'boolean' && visible != featuresVisible) {
            featuresVisible = visible;
            update();
        }
    }

    this.isFeaturesVisible = function() {
        return featuresVisible;
    }

    this.getSortBy = function() {
        return sortBy;
    }
    $( "#peptideSort" )
        .change(function () {
            $( "select option:selected" ).each(function() {
                var newSort = $( this ).text();
                if(newSort != sortBy && (newSort == 'Intensity' || newSort == 'Sequence Location')) {
                    sortBy = newSort;
                    update();
                }
            });
        })
        .change();

    this.getDegradation = function() {
        return {start:degradationPercentRange.start, end: degradationPercentRange.end}
    }

    this.changeDegradation = function(_start, _end){
        if(_start <= _end)
        {
            degradationPercentRange.start = _start;
            degradationPercentRange.end = _end;
           update();
        }
    }

    this.getSequenceBounds = function() {
        return {start:sequenceLength.start, end: sequenceLength.end}
    }

    this.changeSequenceLength = function(_start, _end){
        if(_start <= _end)
        {
            sequenceLength.start = _start;
            sequenceLength.end = _end;
            update();
        }
    }

    this.update = function() { // public update
        update();
    }
}




document.addEventListener("DOMContentLoaded", function() {
    var peptides = protein.peptides;
    for(var i = 0; i < peptides.length; i++) {
        var totalBeforeArea = 0;
        var totalAfterArea = 0;
        if(peptides[i].beforeintensity != null)
            totalBeforeArea = peptides[i].beforeintensity;
        if(peptides[i].normalizedafterintensity != null)
            totalAfterArea = peptides[i].normalizedafterintensity;
        barChartData.push({"Sequence":peptides[i].sequence, "Before Incubation": totalBeforeArea, "After Incubation": totalAfterArea, "StartIndex":peptides[i].startindex,"EndIndex":peptides[i].endindex,
            "Enabled": true, "ChromatogramId": peptides[i].panoramaprecursorbeforeid, "PeptideId":peptides[i].panoramapeptideid })
    }
    // callback for the chart settings
    // when settings are changed this get called which updates data and UI
    var updateData = function() {
        for(var i = 0; i < barChartData.length; i++) {
            var peptide = barChartData[i];
            var bounds = settings.getSequenceBounds();

            if(peptide["Sequence"].length >= bounds.start && peptide["Sequence"].length <= bounds.end) {
                peptide["Enabled"] = true;
            } else {
                peptide["Enabled"] = false;
                continue;
            }
            if(peptide["Before Incubation"] == 0 || peptide["After Incubation"] == 0)
                continue;

            // peptide degradation
            var peptideDegradation = (peptide["Before Incubation"] - peptide["After Incubation"]) / peptide["Before Incubation"] * 100;
            var bounds = settings.getDegradation();
            if((peptideDegradation > bounds.start && peptideDegradation < bounds.end) || peptideDegradation < 0) {
                peptide["Enabled"] = true;
            } else {
                peptide["Enabled"] = false;
            }
        }
        var sortBy = settings.getSortBy();
        if(sortBy=="Sequence Location") {
            barChartData.sort(function(a, b) {
                return a["StartIndex"] - b["StartIndex"];
            });
        }
        if(sortBy=="Intensity"){
            barChartData.sort(function(a, b) {
                return b["Before Incubation"] -a["Before Incubation"];
            });
        }
        $("#livepeptidelist").empty();
        var clipboardPeptides = [];
        barChartData.forEach(function(a) {
            if(a["Enabled"]) {
                $("#livepeptidelist").append('<li class="'+ a.Sequence+'-text"><span style="color:#A6B890;">&block;&nbsp;</span>'+ a.Sequence+ '</li>');
                clipboardPeptides.push(a.Sequence) // add to copey clipboard feature
            }
            else
                $("#livepeptidelist").append('<li class="'+ a.Sequence+'-text"><span style="color:#B9485A;">&block;&nbsp;</span>'+ a.Sequence+ '</li>');
        })
        $("#copytoclipboard").attr("clipboard", clipboardPeptides.join("\r"));

        setFilteredPeptideCount();
        function setFilteredPeptideCount() {
            var activePeptides = 0;
            barChartData.forEach(function(a) {
                if(a["Enabled"])
                    activePeptides++
            })

            $("#filteredPeptideCount > green").text(activePeptides);
        }
        if( $('.ptm-text').tooltip().data("tooltipset")) {
            var featuresVisible = settings.isFeaturesVisible();
            if(featuresVisible == true) {
                $('.ptm-text').tooltip('enable');
                $('.ptm-text').removeAttr('style');

            } else {
                $('.ptm-text').tooltip('disable');
                $('.ptm-text').css('background-color', '#FFF');
            }
        }
        updateUI();
    }
    settings = new Settings(updateData);
    longestPeptide = 0;
    barChartData.forEach(function(p) {
        if(p["Sequence"].length > longestPeptide)
            longestPeptide = p["Sequence"].length;
    })
    settings.changeSequenceLength(0, longestPeptide);
    setJqueryEventListeners();
    settings.update();
    peakAreaDiv = document.getElementById("chart"); // initial set
    proteinBarDiv = document.getElementById("protein"); // initial set
    if(protein != null && protein.peptides.length > 0)
        init()
});

function init() {
    d3.select(window).on('resize', updateUI);
    peakareachart.selectPeptide(barChartData[0])
}

// Sets listeners of dom objects that need listeners
function setJqueryEventListeners() {
    $(".ptm-text").click(function(){
        var index = $(this).attr("index");
    })
    $(function() {
        $( ".ptm-text" ).tooltip({
            items: ".ptm-text",
            tooltipClass:"ptm-text-tooltip",
            content: function() {
                var element = $( this );
                if ( element.attr( "index" ) != null) {
                    var ptm = protein.features[element.attr( "index" )]
                    if(ptm.variation != null) {
                        var text = element.text() + " > "+ptm.variation.toString();
                        if(ptm.description != "")
                            return text + "<br /><span style='color:#a6a6a6;'>" + ptm.description + "</span>";
                    }

                    return ptm.type[0].toUpperCase() + ptm.type.slice(1) + "<br /><span style='color:#a6a6a6;'>" + ptm.description + "</span>";
                }
            }
        }).data("tooltipset", true);
    });
    $(document).ready(function() {
        //set initial state.
        $('#showFeatures').val($(this).is(':checked'));

        $('#showFeatures').change(function() {
            settings.setFeaturesVisible($(this).is(':checked'))
        });

    });
    $('#changecontrolleft').click(function() {
        var e = $.Event('keydown');
        e.which = 37;
        $(document).trigger(e);
    });
    $('#changecontrolright').click(function() {
        var e = $.Event('keydown');
        e.which = 39;
        $(document).trigger(e);
    });
    // keys for peptide selection
    $(document).keydown(function(e) {
        if(selectedPeptide != null)  {
            var indexOfPeptide = barChartData.indexOf(selectedPeptide);
            switch(e.which) {
                case 37: // left
                    indexOfPeptide-= 1;
                    break;
                case 39: // right
                    indexOfPeptide+=1;
                    break;
                case 38: // up
                    indexOfPeptide-=1;
                    break;
                case 40: // down
                    indexOfPeptide+= 1;
                    break;
                default: return; // exit this handler for other keys
            }
            if(typeof barChartData[indexOfPeptide] === 'undefined') {
                return;
            }
            else {
                e.preventDefault();
                peakareachart.selectPeptide(barChartData[indexOfPeptide])
            }
        }
    });
    // copy to clipboard action
    $( "#copytoclipboard" ).click(function(d) {
        copyTextToClipboard($( "#copytoclipboard" ).attr("clipboard"))
    });
    // filter reset button action
    $( "#formreset" ).click(function() {
        // reset combo box 'Sort By'
            $('#peptideSort').prop('selectedIndex', 0);
            $('#peptideSort').trigger('change');
        // reset degradation slider
            var $rangesliderdeg = $("#rangesliderdeg");
            $rangesliderdeg.slider("values", 0, 0);
            $rangesliderdeg.slider("values", 1, 100);
            $("#filterdeg").val("0% - 100%");
            settings.changeDegradation(0, 100)
        // reset peptide length slider
            var $rangesliderlength = $("#rangesliderlength");
            $rangesliderlength.slider("values", 0, 0);
            $rangesliderlength.slider("values", 1, longestPeptide);
            $("#filterpeplength").val("0 - " + longestPeptide);
            settings.changeSequenceLength(0, longestPeptide);
        // reset checkbox
            $('#showFeatures').prop('checked', false);
            settings.setFeaturesVisible(false);
    });
    // initialize sliding ranger bar in Filter Options
    $(function () {
        $("#rangesliderdeg").slider({
            range: true,
            min: 0,
            max: 100,
            values: [0, 100],
            slide: function (event, ui) {
                $("#filterdeg").val(ui.values[0] + "% - " + ui.values[1] + "%");
                settings.changeDegradation(ui.values[0], ui.values[1])
            }
        });
        $("#filterdeg").val($("#rangesliderdeg").slider("values", 0) +
            "% - " + $("#rangesliderdeg").slider("values", 1) + "%");

        $("#rangesliderlength").slider({
            range: true,
            min: 0,
            max: longestPeptide,
            values: [0, longestPeptide],
            slide: function (event, ui) {
                $("#filterpeplength").val(ui.values[0] + " - " + ui.values[1]);
                settings.changeSequenceLength(ui.values[0], ui.values[1])
            }
        });
        $("#filterpeplength").val($("#rangesliderlength").slider("values", 0) +
            " - " + $("#rangesliderlength").slider("values", 1));
    });
}
// to get url parameter by name
function getParameterByName(name) {
    name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
        results = regex.exec(location.search);
    return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
}

// basic javascript contains to see if element exists in an array
function contains(a, obj) {
    for (var i = 0; i < a.length; i++) {
        if (a[i] === obj) {
            return true;
        }
    }
    return false;
}
// used for peptide panel
// http://stackoverflow.com/a/30810322/3175376
function copyTextToClipboard(text) {
    if(text == "") // for some reason can't copy an empty string and user may be confused so we'll copy N/A
        text = "N/A"
    var textArea = document.createElement("textarea");
    // Place in top-left corner of screen regardless of scroll position.
    textArea.style.position = 'fixed';
    textArea.style.top = 0;
    textArea.style.left = 0;

    // Ensure it has a small width and height. Setting to 1px / 1em
    // doesn't work as this gives a negative w/h on some browsers.
    textArea.style.width = '2em';
    textArea.style.height = '2em';

    // We don't need padding, reducing the size if it does flash render.
    textArea.style.padding = 0;

    // Clean up any borders.
    textArea.style.border = 'none';
    textArea.style.outline = 'none';
    textArea.style.boxShadow = 'none';

    // Avoid flash of white box if rendered for any reason.
    textArea.style.background = 'transparent';
    textArea.value = text;
    document.body.appendChild(textArea);
    textArea.select();

    try {
        var successful = document.execCommand('copy');
        var msg = successful ? 'successful' : 'unsuccessful';
        console.log('Copying text command was ' + msg);
    } catch (err) {
        console.log('Oops, unable to copy');
    }

    document.body.removeChild(textArea);
}

function updateUI() {
    var parentWidth = parseInt(d3.select('#chart').style('width'), 10);
    peakareachart.draw("#chart", "#protein", parentWidth);
}

