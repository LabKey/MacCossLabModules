/**
 * // Created by Yuval Boss yuval (at) uw.edu | 4/4/16
 */
var barChartData = []; // d3 peptide chart DO NOT MODIFY ONLY SORT
var peakAreaDiv, proteinBarDiv; // viz module div containers


protein =
{
    selectedPeptide: null,
    settings: null,
    longestPeptide: 0,
    oldPeptide: null,
    peptides: null,
    projects: null,
    features: null,
    sequence: null,
    UI: {
        features: {
            colors: {},
            updateUI: function () {
                if( $('.feature-aa').tooltip().data("tooltipset")) {
                    var visibleFeatures = protein.settings.getVisibleFeatures();
                    $('.feature-aa').tooltip('disable');
                    $('.feature-aa').css('background-color', '#FFF');
                    visibleFeatures.forEach(function(type){
                        $("."+type).tooltip('enable');
                        $("."+type).removeAttr('style');
                        $("."+type).css('background-color', protein.UI.features.colors[type]);
                    });
                }
            },

            initialize: function() {
                if(protein.features == null || protein.features.length == 0)
                    return;
                var allColors = ["#e6194b","#3cb44b","#ffe119","#0082c8","#f58231","#911eb4","#46f0f0","#f032e6","#d2f53c","#fabebe",
                    "#008080","#e6beff","#aa6e28","#fffac8","#800000","#aaffc3","#808000","#ffd8b1","#000080","#000000"];

                // var allFeatures= {
                //     "Chain":"#e6194b",
                //     "Disulfide bond":"",
                //     "Domain":"",
                //     "Glycosylation site":"",
                //     "Lipid moiety-binding region":"",
                //     "Modified residue":"",
                //     "Mutagenesis site":"",
                //     "Region of interest":"",
                //     "Sequence conflict":"",
                //     "Sequence variant":"#f032e6",
                //     "Signal peptide":""};


                var uniqueFeatureTypes = [];
                protein.features.forEach(function(feature) {
                   if(!contains(uniqueFeatureTypes, feature.type))
                       uniqueFeatureTypes.push(feature.type);
                });

                // sort alphabetically
                uniqueFeatureTypes.sort(function(a, b){
                    if(a < b) return -1;
                    if(a > b) return 1;
                    return 0;
                });
                var colorIndex = 0;
                uniqueFeatureTypes.forEach(function(type) {
                    var featureId = "feature-" + type.split(" ").join("");
                    // create colors
                    if(colorIndex < allColors.length -1) {
                        protein.UI.features.colors[featureId] = allColors[colorIndex];
                        colorIndex++;
                    } else {
                        protein.UI.features.colors[featureId] = "#808080"; // if out of colors rest will be grey
                    }


                    // create dom elements
                    var checkbox = document.createElement('input');
                    checkbox.type = "checkbox";
                    checkbox.name = type;
                    checkbox.value = featureId;
                    checkbox.id = featureId+"-checkbox";
                    checkbox.className = "featureCheckboxItem";
                    checkbox.setAttribute("color", protein.UI.features.colors[featureId]);

                    var label = document.createElement('Label');
                    label.setAttribute("for",checkbox.id);
                    label.innerHTML = capitalizeFirstLetter(type);
                    label.setAttribute("style", "padding-left: 5px; border-left: 5px solid "+protein.UI.features.colors[featureId] +";");

                    var listItem = document.createElement("li");
                    listItem.className = "featureListItem";

                    listItem.appendChild(checkbox);
                    listItem.appendChild(label);

                    document.getElementById("featuresList").appendChild(listItem);
                });

                // add checkbox change event
                $(".featureCheckboxItem").change(function() {
                    if(this.checked) {
                        protein.settings.addFeatureVisible(this.value);
                        // if all are manually checked
                        if ($('.featureCheckboxItem:checked').length == $('.featureCheckboxItem').length) {
                            $("#showFeatures").prop('checked', true);
                        }
                    } else {
                        protein.settings.removeFeatureVisible(this.value);
                        $("#showFeatures").prop('checked', false);
                    }
                });

                // check/uncheck all features event listener
                $("#showFeatures").change(function() {
                    if(this.checked) {
                        $(".featureCheckboxItem").each(function() {
                            $(this).prop('checked', true).trigger("change");
                        });
                    } else {
                        $(".featureCheckboxItem").prop('checked', false).trigger('change');
                    }
                });

                // tooltip
                $( ".feature-aa" ).tooltip({
                    items: ".feature-aa",
                    tooltipClass:"feature-aa-tooltip",
                    content: function() {
                        var element = $( this );
                        if ( element.attr( "index" ) != null) {
                            var ptm = protein.features[element.attr( "index" )];
                            if(ptm.variation != null) {
                                var text = element.text() + " > "+ptm.variation.toString();
                                if(ptm.description != "")
                                    return text + "<br /><span style='color:#a6a6a6;'>" + ptm.description + "</span>";
                            }

                            return ptm.type[0].toUpperCase() + ptm.type.slice(1) + "<br /><span style='color:#a6a6a6;'>" + ptm.description + "</span>";
                        }
                    }
                }).data("tooltipset", true);
            }
        }
    },

    getSelectedPeptide: function() {
        return protein.selectedPeptide;
    },

    selectPeptide: function(p) {
        if(p == null)
            return;

        if(protein.selectPeptide.PeptideId != null && protein.selectPeptide.PeptideId == p.PeptideId)
            return;
        protein.oldPeptide =  protein.selectedPeptide;
        protein.selectedPeptide = p;
        var dataIndexOfPeptide = 0; // not sequence index, but index in array of peptides
        for(dataIndexOfPeptide; dataIndexOfPeptide < barChartData.length; dataIndexOfPeptide++) {
            if (barChartData[dataIndexOfPeptide] === protein.selectedPeptide)
                break;
        }

        // sets panorama chromatogram to that of the newly selected peptide
        if(protein.selectedPeptide["ChromatogramBeforeId"] == null&&protein.selectedPeptide["ChromatogramAfterId"] == null) {
            $('#selectedPeptideChromatogramBefore').attr("src", "");
            $('#selectedPeptideChromatogramAfter').attr("src", "");
            $('#selectedPeptideLink').hide();
        } else {
            $('#selectedPeptideLink').show();
            $('#selectedPeptideChromatogramBefore').attr("src", chomatogramUrl+"id="+protein.selectedPeptide["ChromatogramBeforeId"]+"&chartWidth=250&chartHeight=400&syncY=false&syncX=false")
            $('#selectedPeptideChromatogramAfter').attr("src", chomatogramUrl+"id="+protein.selectedPeptide["ChromatogramAfterId"]+"&chartWidth=250&chartHeight=400&syncY=false&syncX=false")
        }
        // sets panorama peptide link
        $('#selectedPeptideLink').attr("href", showPeptideUrl + "id=" + protein.selectedPeptide.PeptideId);
        // sets basic peptide info (Seq, location, length, etc..
        $('#peptideinfo').empty();
        var totalPeakAreaWithCommas = protein.selectedPeptide["Before Incubation"].toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        $('#peptideinfo').append('<span style="font-weight:600">Sequence:</span> <span style="font-family: monospace;">' + protein.selectedPeptide.Sequence + "</span><br />" +
                '<span style="font-weight:600">Location:</span> <span style="font-family: monospace;">[' + protein.selectedPeptide.StartIndex +","+ protein.selectedPeptide.EndIndex + "]</span> <br />" +
                '<span style="font-weight:600">Length:</span> <span style="font-family: monospace;">' + protein.selectedPeptide.Sequence.length+ "</span> <br />" +
                '<span style="font-weight:600">Total Peak Area:</span> <span style="font-family: monospace;">' + totalPeakAreaWithCommas + "</span> ");

        // scroll in ul
        var liIndex = $("li").index($('.'+protein.selectedPeptide.Sequence+'-text'));
        $('#livepeptidelist').scrollTop(0)
        var pos = $('#livePeptideList li:nth-child('+liIndex+')').position();
        if (pos != null) {
            $('#livepeptidelist').scrollTop(0).scrollTop(pos.top);
        }

        peakareachart.selectPeptide(p);
    },

    updateUI: function(){
        var parentWidth = parseInt(d3.select('#chart').style('width'), 10);
        peakareachart.draw("#chart", "#protein", parentWidth);
    },

    initialize: function() {

        protein.peptides = proteinJSON.peptides;
        protein.projects = proteinJSON.projects;
        protein.features = proteinJSON.features;
        protein.sequence = proteinJSON.sequence;

        var peptides = protein.peptides;
        for(var i = 0; i < peptides.length; i++) {
            var totalBeforeArea = 0;
            var totalAfterArea = 0;
            if(peptides[i].beforeintensity != null)
                totalBeforeArea = peptides[i].beforeintensity;
            if(peptides[i].normalizedafterintensity != null)
                totalAfterArea = peptides[i].normalizedafterintensity;
            barChartData.push({"Sequence":peptides[i].sequence, "Before Incubation": totalBeforeArea, "After Incubation": totalAfterArea, "StartIndex":peptides[i].startindex,"EndIndex":peptides[i].endindex,
                "Enabled": true, "ChromatogramBeforeId": peptides[i].panoramaprecursorbeforeid, "ChromatogramAfterId": peptides[i].panoramaprecursorafterid, "PeptideId":peptides[i].panoramapeptideid })
        }

        // callback for the chart settings
        // when settings are changed this get called which updates data and UI
        var updateData = function() {
            for(var i = 0; i < barChartData.length; i++) {
                var peptide = barChartData[i];
                var bounds = protein.settings.getSequenceBounds();

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
                var bounds = protein.settings.getDegradation();
                if((peptideDegradation > bounds.start && peptideDegradation < bounds.end) || peptideDegradation < 0) {
                    peptide["Enabled"] = true;
                } else {
                    peptide["Enabled"] = false;
                }
            }
            var sortBy = protein.settings.getSortBy();
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
            });
            $("#copytoclipboard").attr("clipboard", clipboardPeptides.join("\r"));

            setFilteredPeptideCount();
            function setFilteredPeptideCount() {
                var activePeptides = 0;
                barChartData.forEach(function(a) {
                    if(a["Enabled"])
                        activePeptides++
                });

                $("#filteredPeptideCount > green").text(activePeptides);
            }
            protein.UI.features.updateUI();
            protein.updateUI();
        };

        protein.settings = new Settings(updateData);

        if(protein.projects != null) {
            for(var i = 0; i < protein.projects.length; i++) {
                new Project(protein.projects[i]);
            }
        }

        longestPeptide = 0;
        barChartData.forEach(function(p) {
            if(p["Sequence"].length > longestPeptide)
                longestPeptide = p["Sequence"].length;
        });

        protein.settings.changeSequenceLength(0, longestPeptide);
        protein.setJqueryEventListeners();
        protein.UI.features.initialize();
        protein.settings.update();
        peakAreaDiv = document.getElementById("chart"); // initial set
        proteinBarDiv = document.getElementById("protein"); // initial set
        if(protein != null && protein.peptides.length > 0) {
            d3.select(window).on('resize', protein.updateUI);
            protein.selectedPeptide = barChartData[0];
            protein.selectPeptide(barChartData[0]);
        }
    },

    // Sets listeners of dom objects that need listeners
    setJqueryEventListeners: function() {
        $(".feature-aa").click(function(){
            var index = $(this).attr("index");
        });

        // keys for peptide selection
        $(document).keydown(function(e) {
            if(protein.selectedPeptide != null)  {
                var indexOfPeptide = barChartData.indexOf(protein.selectedPeptide);
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
                    protein.selectPeptide(barChartData[indexOfPeptide])
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
            protein.settings.changeDegradation(0, 100)
            // reset peptide length slider
            var $rangesliderlength = $("#rangesliderlength");
            $rangesliderlength.slider("values", 0, 0);
            $rangesliderlength.slider("values", 1, longestPeptide);
            $("#filterpeplength").val("0 - " + longestPeptide);
            protein.settings.changeSequenceLength(0, longestPeptide);
            // reset checkbox
            $('#showFeatures').prop('checked', false).trigger("change");
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
                    protein.settings.changeDegradation(ui.values[0], ui.values[1])
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
                    protein.settings.changeSequenceLength(ui.values[0], ui.values[1])
                }
            });
            $("#filterpeplength").val($("#rangesliderlength").slider("values", 0) +
                    " - " + $("#rangesliderlength").slider("values", 1));
        });
    }
};