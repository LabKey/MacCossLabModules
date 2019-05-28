function Settings(updateCallback) {
    var update = function() { updateCallback(); }; // private update callback
    var visibleFeatures = [];

    var degradationPercentRange = {
        start:0,
        end: 100
    };
    var sequenceLength = {
        start: 0,
        end: 0
    };
    var sortBy = "Intensity";

    this.addFeatureVisible = function(type) {
        if(!contains(visibleFeatures, type)) {
            visibleFeatures.push(type);
            update();
        }
    };

    this.removeFeatureVisible = function(type) {
        visibleFeatures = visibleFeatures.filter(f => f != type);
        update();
    };

    this.getVisibleFeatures = function() {
        return visibleFeatures;
    };

    this.getSortBy = function() {
        return sortBy;
    };
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
    };

    this.changeDegradation = function(_start, _end){
        if(_start <= _end)
        {
            degradationPercentRange.start = _start;
            degradationPercentRange.end = _end;
            update();
        }
    };

    this.getSequenceBounds = function() {
        return {start:sequenceLength.start, end: sequenceLength.end}
    };

    this.changeSequenceLength = function(_start, _end){
        if(_start <= _end)
        {
            sequenceLength.start = _start;
            sequenceLength.end = _end;
            update();
        }
    };

    this.update = function() { // public update
        update();
    }
}
