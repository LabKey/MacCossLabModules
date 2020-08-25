// In order for script to work the following 4 divs must be included in the file which calls this must contain the following
//<div id="medianmem" class="c3chart"></div>
// <div id="duration" class="c3chart"></div>
// <div id="passes" class="c3chart"></div>
// <div id="memory" class="c3chart"></div>
// <div id="failGraph" class="c3chart"></div>

function setYRange(chart, data, metadata) {
    var yMin = null;
    var yMax = null;
    for (var i = 1; i < data.length; i++) { // start at 1 to skip name
        var v = data[i];
        if (v == null)
            continue;
        if (yMin == null || (v > 0 && v < yMin))
            yMin = v;
        if (yMax == null || v > yMax)
            yMax = v;
    }
    if (yMin != null && yMax != null) {
        if (metadata) {
            yMin = Math.max(yMin, metadata.mean - 10 * metadata.stddev);
            yMax = Math.min(yMax, metadata.mean + 10 * metadata.stddev);
        }
        chart.axis.min({y: yMin});
        chart.axis.max({y: yMax});
    }
}

function addBoundLines(chart, metadata) {
    if (!metadata)
        return;
    var upperErrorBound = metadata.mean + metadata.stddev * metadata.errorBound;
    var upperWarnBound = metadata.mean + metadata.stddev * metadata.warnBound;
    var lowerErrorBound = metadata.mean - metadata.stddev * metadata.warnBound;
    var lowerWarnBound = metadata.mean - metadata.stddev * metadata.errorBound;
    chart.ygrids.add([{ value: metadata.mean, class: 'chart-mean' }]);
    if (metadata.stddev > 0) {
        chart.ygrids.add([
            { value: upperErrorBound, class: 'chart-stddev-error' },
            { value: upperWarnBound, class: 'chart-stddev-warn' },
            { value: lowerErrorBound, class: 'chart-stddev-warn' },
            { value: lowerWarnBound, class: 'chart-stddev-error' }]);
    }
    if (chart.axis.max().y < upperErrorBound)
        chart.axis.max({y: upperErrorBound});
    lowerErrorBound -= metadata.stddev;
    if (chart.axis.min().y > lowerErrorBound)
        chart.axis.min({y: lowerErrorBound});
}

function trendDataColor(color, d) {
    for (var prop in window.GraphInfo) {
        var infoObj = window.GraphInfo[prop];
        if (infoObj.name == d || (d.id && infoObj.name == d.id)) {
            if (d.x === undefined || !window.GraphTrainRuns) {
                return infoObj.color;
            }
            return !window.GraphTrainRuns.has(typeof d.x === 'number' ? d.x : d.x.getTime())
                ? infoObj.color
                : infoObj.colorTrain;
        }
    }
    return '#000000'; // default
}

function generateTrendCharts(trendsJson, options) {
    options = options || {};

    if (window.GraphObjects) {
        for (var i = 0; i < window.GraphObjects.length; i++)
            window.GraphObjects[i].destroy();
        window.GraphObjects = [];
    }

    var dates = trendsJson.dates;
    if (dates.length == 0) {
        // hide all divs containing bar charts because they have a fixed height given by .c3chart in ../img/style.css
        $('#duration').css("display", "none");
        $('#passes').css("display", "none");
        $('#memory').css("display", "none");
        $('#failGraph').css("display", "none");
        $('#medianmem').css("display", "none");
        return;
    }


    var avgDuration = trendsJson.avgDuration;
    var avgMemory = trendsJson.avgMemory;
    var avgTestRuns = trendsJson.avgTestRuns;
    var avgFailures = trendsJson.avgFailures;
    // var medianmem = trendsJson.medianMemory;

    window.GraphTrainRuns = new Set();

    var xAxisType = 'timeseries';
    var xTickFormat = !options.showSubChart ? "%m/%d/%y" : "%m/%y";

    var trainIndices = [];
    var curDate = new Date(dates[0]);
    var anyMissing = false;
    for (var i = 0; i < dates.length; i++) {
        for ( ; curDate < new Date(dates[i]); curDate.setDate(curDate.getDate() + 1)) {
            if (options.fillMissing) {
                dates.splice(i, 0, curDate.valueOf());
                avgDuration.splice(i, 0, null);
                avgMemory.splice(i, 0, null);
                avgTestRuns.splice(i, 0, null);
                avgFailures.splice(i, 0, null);
                i++;
            } else {
                anyMissing = true;
            }
        }
        if (options.trainRuns && options.trainRuns.has(dates[i])) {
            trainIndices.push(i);
        }
        curDate.setDate(curDate.getDate() + 1);
    }
    if (!anyMissing) {
        for (var i = 0; i < trainIndices.length; i++) {
            window.GraphTrainRuns.add(dates[trainIndices[i]]);
        }
    } else {
        xAxisType = 'category';
        xTickFormat = function(i) {
            var tmp = new Date(dates[i + 1]);
            var label = (tmp.getMonth() + 1) + '/';
            if (!options.showSubChart)
                label += tmp.getDate() + '/';
            label += tmp.getFullYear();
            return label;
        };
        window.GraphTrainRuns = new Set(trainIndices);
    }

    // colors for training data points
    window.GraphInfo = {
        duration: {name: 'Average Duration', color: '#b0c4de', colorTrain: '#3c6090'},
        memory: {name: 'Average Memory', color: '#a078a0', colorTrain: '#ff0000'},
        passes: {name: 'Average Test Runs', color: '#20b2aa', colorTrain: '#ff0000'},
        failures: {name: 'Average Failures', color: '#f08080', colorTrain: '#a01313'}
    };

    dates.unshift('x');
    avgDuration.unshift(window.GraphInfo.duration.name);
    avgMemory.unshift(window.GraphInfo.memory.name);
    avgTestRuns.unshift(window.GraphInfo.passes.name);
    avgFailures.unshift(window.GraphInfo.failures.name);
    // medianmem.unshift("Median Memory");

    var axisSettings = {
        x: {
            type: xAxisType,
            localtime: false,
            tick: { culling: { max: 24 }, multiline: false, rotate: 75, format: xTickFormat },
        }
    };

    window.GraphObjects = [];

    /*c3.generate({
        bindto: '#medianmem',
        padding: { bottom: 25 },
        data: {
            x: 'x',
            columns: [dates, medianmem],
            colors: { 'Median Memory (calculated from the last entries of a run)' : '#ffff00' }
        },
        axis: {
            x: { type: 'timeseries', localtime: false, tick: { rotate: 75, format: dateFormat } },
            y: { label: { text: 'Memory (MB)', position: 'outer-middle' } }
        }
    });*/

    var durationChart = c3.generate({
        bindto: '#duration',
        padding: { bottom: 25 },
        data: {
            x: 'x',
            columns: [dates, avgDuration],
            type: 'bar',
            color: trendDataColor,
            onclick: options.clickHandler
        },
        subchart: { show: options.showSubChart },
        bar: { width: { ratio: 0.3 } },
        axis: axisSettings
    });
    window.GraphObjects.push(durationChart);

    var memChart = c3.generate({
        bindto: '#memory',
        padding: { bottom: 25 },
        data: {
            x: 'x',
            columns: [dates, avgMemory],
            color: trendDataColor,
            onclick: options.clickHandler
        },
        subchart: { show: options.showSubChart },
        axis: axisSettings
    });
    setYRange(memChart, avgMemory, options.memData);
    addBoundLines(memChart, options.memData);
    window.GraphObjects.push(memChart);

    var avgTestRunsChart = c3.generate({
        bindto: '#passes',
        padding: { bottom: 25 },
        data: {
            x: 'x',
            columns: [dates, avgTestRuns],
            color: trendDataColor,
            onclick: options.clickHandler
        },
        subchart: { show: options.showSubChart },
        axis: axisSettings
    });
    setYRange(avgTestRunsChart, avgTestRuns, options.runData);
    addBoundLines(avgTestRunsChart, options.runData);
    window.GraphObjects.push(avgTestRunsChart);

    var failureChart = c3.generate({
        bindto: '#failGraph',
        padding: { bottom: 25 },
        data: {
            x: 'x',
            columns: [dates, avgFailures],
            type: 'bar',
            color: trendDataColor,
            onclick: options.clickHandler
        },
        subchart: {
            show: options.showSubChart,
            onbrush: function (domain) {
                subchartDomainUpdated(domain);
            }
        },
        bar: { width: { ratio: 0.3 } },
        axis: axisSettings
    });
    window.GraphObjects.push(failureChart);
}
