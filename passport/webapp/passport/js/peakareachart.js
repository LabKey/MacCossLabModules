/**
 * Created by Yuval Boss yuval (at) uw.edu | 4/4/16
 * the peakarea module includes a barchart with before & after incubation data
 * also contains the protein bar viz which shows the location of a selected peptide in the protein
 */

peakareachart = {
    highlightPeptide: function highlightPeptide(start, sequence) {
        var protein_domain = d3.select("#protein svg").selectAll("g");

        var highlighted= d3.select("#protein svg").selectAll("g.domain_plot").selectAll("rect.highlight_peptide");
        var highlightBoundsText= d3.select("#protein svg").selectAll("g.domain_plot").selectAll("text.highlight_peptide_bounds_text");
        highlighted.remove();
        highlightBoundsText.remove();

        var proteinSequenceLength= d3.select("#protein svg").selectAll("g.domain_plot").selectAll("text.protein_label_text")[0][1].textContent;
        var width = d3.select("#protein svg").selectAll("g.domain_plot").selectAll("rect.protein_sequence")[0][0].getAttribute("width");
        var relativeLength = sequence.length/proteinSequenceLength;
        var relativeWidth = relativeLength * width;
        var relativeStart = (start / proteinSequenceLength) * width;
        protein_domain.append("rect")
            .attr("class", "highlight_peptide")
            .attr("x",relativeStart)
            .attr("width", relativeWidth)
            .attr("y", yRelative(46))
            .attr("height", 16);


        protein_domain.append("text")
            .attr("class", "highlight_peptide_bounds_text")
            .style("text-anchor", "middle")
            .attr("x", relativeStart + relativeWidth/2)
            .attr("y", yRelative(31 - 12 - 8)) //12px font  8px for highlight_peptide_bounds
            .text(start +  " - " + (start + sequence.length -1)); // subtract 1 because end index will be base 2

    },
    paintPeptide: function paintPeptide(d) {
        var parentWidth = parseInt(d3.select('#chart').style('width'), 10);
        var sequence = d.Sequence;
        var peptideWidth = sequence.length * 50;
        var marginLeft = (parentWidth - peptideWidth)/2;
        if(d3.select("#peptide")[0][0].getAttribute("peptide") != sequence) {
            d3.select("#peptide").selectAll("svg").remove();
            var svg =  d3.select("#peptide").append("svg")
                .attr("width", peptideWidth)
                .attr("height", 50)
                .attr("peptide", sequence)
                .attr("style", "margin-left:"+marginLeft+"px;");

            svg[0][0].innerHTML = "";
            svg[0][0].setAttribute("peptide", sequence);
            for(var i = 0; i < sequence.length; i++) {
                // issues with i = 0 because 'cx' would be 0*50 and be off the page, so start i at 1
                svg.append("circle")
                    .attr("r", 25)
                    .attr("cx", 25 + (i* 50))
                    .attr("cy", 25)
                    .attr("class", "aminoCircle");

                svg.append("text")
                    .attr("x", 25+ (i* 50))
                    .attr("y", 32) // TODO figure out resizing
                    .attr("text-anchor", "middle")
                    .attr("style","font:20px 'Raleway', sans-serif; font-weight:600;")
                    .text(sequence[i]);
            }
        }
    },
    selectPeptide: function selectPeptide(p) {
        if(p == null)
            return;
        // order is VERY important here so be careful and make sure changes don't break anything
        var oldPeptide = protein.oldPeptide;
        var selectedPeptide = protein.selectedPeptide;
        if(oldPeptide != null) {
            $("#group-"+ oldPeptide.Sequence).find(".left-bar").css("fill","#8a89a6");
            $("#group-"+ oldPeptide.Sequence).find(".right-bar").css("fill","#a05d56");
        }

        $("#group-"+ selectedPeptide.Sequence).find(".left-bar").css("fill","#494777");
        $("#group-"+ selectedPeptide.Sequence).find(".right-bar").css("fill","#66221B");


        // highlights peptide in left panel and soon also protein sequence
        if(oldPeptide == null || oldPeptide != p.Sequence) {
            if(oldPeptide != null) {
                $('.'+oldPeptide.Sequence+'-text').css("background-color","transparent");
            }

            $('.'+selectedPeptide.Sequence+'-text').css("background-color","#ddd");
        }
        var selectedPeptide = protein.getSelectedPeptide();
        peakareachart.paintPeptide(selectedPeptide);
        peakareachart.highlightPeptide(selectedPeptide.StartIndex, selectedPeptide.Sequence);
    },
    draw: function (peakAreaDiv, proteinBarDiv, parentWidth) {
        var margin = {top: 20, right: 100, bottom: 60, left: 100},
            width = parentWidth - margin.left - margin.right,
            height = 400 - margin.top - margin.bottom;

        plotProtein(protein.sequence);
        plotBarChart();
        protein.selectPeptide(protein.selectedPeptide);
        peakareachart.selectPeptide(protein.selectedPeptide);

        function plotProtein(sequence) {
            d3.select("#protein").selectAll("svg").remove();
            var svg = d3.select("#protein").append("svg")
                .attr("width", width)
                .attr("height", 80)
                .attr("style","margin-left:"+(parentWidth-width)/2+"px;");

            xProtein = d3.scale.linear()
                .range([0, width])
                .domain([0, sequence.length]);


            yRelative = d3.scale.linear()
                .range([80, 0])
                .domain([0, 100]);

            var sequence_group = svg.append("g")
                .attr("class", "domain_plot");

            sequence_group.append("rect")
                .attr("class", "protein_sequence")
                .attr("x",0)
                .attr("width", function(d) { return xProtein(sequence.length); })
                .attr("y", yRelative(45))
                .attr("height", 15);

            sequence_group.append("text")
                .attr("class", "protein_label_text")
                .attr("x", xProtein(0.5))
                .attr("y", yRelative(45) + 35)
                .text("1");

            sequence_group.append("text")
                .attr("class", "protein_label_text")
                .attr("x", xProtein(sequence.length)-25)
                .attr("y", yRelative(45) + 35)
                .text(sequence.length);
        }

        function plotBarChart() {
            var x0 = d3.scale.ordinal()
                .rangeRoundBands([0, width]);

            var x1 = d3.scale.ordinal();

            var y = d3.scale.linear()
                .range([height, 0]);

            var color = d3.scale.ordinal()
                .range(["#8a89a6", "#a05d56"]);
            var barClassName = d3.scale.ordinal()
                .range(["left-bar", "right-bar"]);
            var xAxis = d3.svg.axis()
                .scale(x0)
                .orient("bottom");

            var yAxis = d3.svg.axis()
                .scale(y)
                .orient("left")
                .tickFormat(function (d) {
                    if (d == 0)
                        return 0;
                    return d.toExponential();
                });

            //  svg contains all chart elements it is the "canvas"
            d3.select("#chart").selectAll("svg").remove();
            var svg = d3.select("#chart").append("svg")
                .attr("width", width + margin.left + margin.right)
                .attr("height", height + margin.top + margin.bottom)
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")")
                .attr("id", "barchart");

            var groups = ["Before Incubation", "After Incubation"];
            barChartData.forEach(function (d) {
                d.groups = groups.map(function (name) {
                    return {name: name, value: +d[name], enabled: +d["Enabled"]};
                });
            });

            x0.domain(barChartData.map(function (d) {
                return d.Sequence;
            }));
            x1.domain(groups).rangeRoundBands([0, x0.rangeBand()]); // bar
            y.domain([0, d3.max(barChartData, function (d) {
                return d3.max(d.groups, function (d) {
                    return d.value;
                });
            })]);

            svg.append("g")
                .attr("class", "y axis")
                .call(yAxis)
                .attr("transform", "translate(" + -1 + ",0)")// small bug hacky fix, sometimes on resize bars slightly cover y axis
                .append("text")
                .attr("y", -60)
                .attr("x", -(height/2-40))
                .attr("id", "barchart")
                .style("text-anchor", "end")
                .style("font-weight", "600")
                .text("Peak Area")
                .attr("transform", "rotate(-90)");

            var seq = svg.selectAll(".seq")
                .data(barChartData)
                .enter().append("g")
                .attr("id", function (d) {
                    return "group-" + d.Sequence;
                })
                .attr("transform", function (d) {
                    return "translate(" + x0(d.Sequence) + ",0)";
                })
                .on("click", function (p) {
                    if(p.Enabled) {
                        protein.selectPeptide(p);
                        peakareachart.selectPeptide(p);
                    }
                })
                .on("mouseover", function (d) {
                    if(d.Enabled == true) {
                        this.childNodes[0].style.fill = "#494777"; // Lighter option for hover #67668D, #7E3C35
                        this.childNodes[1].style.fill = "#66221B";
                    }
                }).on('mouseout', function (d) {
                    if(d.Enabled && (protein.selectedPeptide == null || d.Sequence != protein.selectedPeptide.Sequence)) {
                        this.childNodes[0].style.fill = "#8a89a6";
                        this.childNodes[1].style.fill = "#a05d56";
                    }
                });

            var badSeq = []; // peptides that are missing precursors
            seq.selectAll("rect")
                .data(function (d) {
                    if (d.groups[0].value < 0) { // -1 is flag for missing precursor
                        d.groups[0].value = 0;
                        badSeq.push(d.Sequence)
                    } else if (d.groups[1].value < 0) {
                        d.groups[1].value = 0;
                        badSeq.push(d.Sequence);
                    }
                    return d.groups;
                })
                .enter().append("rect")
                .attr("width", x1.rangeBand()).attr("x", function (d) {
                    var value = x1(d.name);
                    return value;
                })
                .attr("y", function (d) {
                    return y(d.value);
                })
                .attr("height", function (d) {
                    if(d.value < 0) {
                        d.value = 0;
                    }
                    var val = height - y(d.value);
                    if(val < 0) {
                        val = 0;
                    }
                    return val;
                })
                .style("opacity", function (d) {
                        if (d.enabled == false)
                            return 0.3;
                    }
                )
                .style("fill", function (d) {
                    return color(d.name);
                })
                .attr("class", function(d) {
                    return barClassName(d.name)
                });

            // legend with two boxes and (Before/After text associated to color)
            var barsLegend = svg.selectAll(".barsLegend")
                .data(groups)
                .enter().append("g")
                .attr("class", "barsLegend")
                .attr("transform", function (d, i) {
                    return "translate(0," + i * 32 + ")";
                });

            barsLegend.append("rect")
                .attr("x", width - 30)
                .attr("y", "15")
                .attr("width", 30)
                .attr("height", 30)
                .style("fill", color);

            barsLegend.append("text")
                .attr("x", width - 36)
                .attr("y", 30)
                .attr("dy", ".35em")
                .style("text-anchor", "end")
                .style("font-weight", 600)
                .text(function (d) {
                    return d;
                });

            svg.append("g")
                .attr("class", "x axis")
                .attr("transform", "translate(0," + height + ")")
                .call(xAxis)
                .selectAll("text")
                .text(function (d) {
                    if (d.length > 3)
                        return d.substring(0, 3)
                    return d;
                })
                .style("text-anchor", "end")
                .style("font-weight", "600")
                .style("font-size", function() { // TODO: resizing function may need a bit of work but looks pretty good
                    var totalPeptides = barChartData.length;
                    var peptideToWidth = width/totalPeptides;
                    if(peptideToWidth < 7)
                        return "8px";
                    if(peptideToWidth < 10)
                        return "10px";
                    if(peptideToWidth > 15)
                        return "15px";
                    return "12px";
                })
                .attr("fill", function (d) {
                    if (contains(badSeq, d))
                        return "#A05D56"
                    return "#000";
                })
                .attr("dx", "-.8em")
                .attr("dy", ".15em").attr("transform", "rotate(-50)")
                .append("svg:title").text(
                function (d) {
                    return d;
                });
            // Allows hover on X axis ticks
            d3.selectAll('.x')
                .selectAll('.tick')
                .data(barChartData)
                .on("click", function (p) {
                    if(p.Enabled) {
                        protein.selectPeptide(p);
                        peakareachart.selectPeptide(p);
                    }
                }).on('mouseout', function (d) {

            });

        }
    }
};



