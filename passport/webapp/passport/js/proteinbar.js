function ProteinBar(project) {

    var parentWidth = parseInt(d3.select('#chart').style('width'), 10);
    var margin = {top: 20, right: 100, bottom: 60, left: 100},
            width = parentWidth - margin.left - margin.right,
            height = 20;
    var xProtein = d3.scale.linear()
            .range([0, width])
            .domain([0, protein.sequence.length]);

    var yRelative = d3.scale.linear()
            .range([0, height])
            .domain([0, height]);


    var svg = d3.select("#"+project.getId()).append("svg")
            .attr("width", width)
            .attr("height", height)
            .attr("id", project.runId + "-protbar")
            .attr("style","margin-left:"+83+"px;");

    var sequence_group = svg.append("g")
            .attr("class", "prot_domain_plot");

    sequence_group.append("rect")
            .attr("class", "protein_sequence")
            .attr("x",0)
            .attr("width", function(d) { return xProtein(protein.sequence.length); })
            .attr("y", yRelative(2))
            .attr("height", 15);
    //
    // sequence_group.append("text")
    //         .attr("class", "protein_label_text")
    //         .attr("x", xProtein(0.5))
    //         .attr("y", yRelative(45) + 35)
    //         .text("1");
    //
    // sequence_group.append("text")
    //         .attr("class", "protein_label_text")
    //         .attr("x", xProtein(protein.sequence.length)-25)
    //         .attr("y", yRelative(45) + 35)
    //         .text(protein.sequence.length);

    // draw peptides
    for(var i = 0; i < project.peptides.length; i++) {
        var peptide = project.peptides[i];
        sequence_group.append("rect")
                .attr("class", "peptide_sequence")
                .attr("x",xProtein(peptide.startindex))
                .attr("width", function(d) { return xProtein(peptide.endindex - peptide.startindex); })
                .attr("y", yRelative(2))
                .attr("title", peptide.sequence)
                .attr("height", 15);
        //
        // sequence_group.append("text")
        //         .attr("class", "peptide_label_text")
        //         .attr("x", xProtein(peptide.startindex))
        //         .attr("y", yRelative(0) + 35)
        //         .text(peptide.sequence);
    }

};