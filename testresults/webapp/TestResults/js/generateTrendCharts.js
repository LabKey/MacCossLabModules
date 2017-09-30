// In order for script to work the following 4 divs must be included in the file which calls this must contain the following
// <div id="duration" class="c3chart"></div>
// <div id="passes" class="c3chart"></div>
// <div id="memory" class="c3chart"></div>
// <div id="failGraph" class="c3chart"></div>
function generateTrendCharts(trendsJson, showSubChart) {
    var dates = trendsJson.dates;
    for (var i = 0; i < dates.length; i++)
        dates[i] = new Date(dates[i]);
    var avgDuration = trendsJson.avgDuration;
    var avgMemory = trendsJson.avgMemory;
    var avgTestRuns = trendsJson.avgTestRuns;
    var avgFailures = trendsJson.avgFailures;
    var dateFormat = "%m/%d";
    if(showSubChart)
        dateFormat = "%m/%y"
    if(dates.length >= 1) {
        dates.unshift('x');
        avgDuration.unshift("Average Duration");
        avgMemory.unshift("Average Memory");
        avgTestRuns.unshift("Average Test Runs");
        avgFailures.unshift("Average Failures");

        var durationTrendChart= c3.generate({
            bindto: '#duration',
            padding: {
                bottom: 25
            },
            data: {
                x: 'x',
                columns: [
                    dates,
                    avgDuration
                ],
                type: 'bar',
                colors: {
                    'Average Duration': '#B0C4DE'
                },
                onclick: function(d, i) {
                    console.log("onclick", d.x, i); // currently not used but has the potential to be
                }
            },
            subchart: { show: showSubChart},
            bar: {
                width: {
                    ratio: 0.5
                }
            },
            axis: {
                x: {
                    type: 'timeseries',
                    localtime: false,
                    tick: {
                        rotate: 75,
                        format: dateFormat
                    }
                }
            }
        });
        var memoryTrendChart = c3.generate({
            bindto: '#memory',
            padding: {
                bottom: 25
            },
            data: {
                x: 'x',
                columns: [
                    dates,
                    avgMemory
                ],
                type: 'bar',
                colors: {
                    'Average Memory': '#A078A0'
                },
                onclick: function(d, i) {
                    console.log("onclick", d.x, i);
                }
            },
            subchart: { show: showSubChart},
            bar: {
                width: {
                    ratio: 0.5
                }
            },
            axis: {
                x: {
                    type: 'timeseries',
                    localtime: false,
                    tick: {
                        rotate: 75,
                        format: dateFormat
                    }
                }
            }
        });
        var passTrendChart = c3.generate({
            bindto: '#passes',
            padding: {
                bottom: 25
            },
            data: {
                x: 'x',
                columns: [
                    dates,
                    avgTestRuns
                ],
                type: 'bar',
                colors: {
                    'Average Test Runs': '#20B2AA'
                },
                onclick: function(d, i) {
                    console.log("onclick", d.x, i);
                }
            },
            subchart: { show: showSubChart},
            bar: {
                width: {
                    ratio: 0.5
                }
            },
            axis: {
                x: {
                    type: 'timeseries',
                    localtime: false,
                    tick: {
                        rotate: 75,
                        format: dateFormat
                    }
                }
            }
        });
        var failTrendChart = c3.generate({
            bindto: '#failGraph',
            padding: {
                bottom: 25
            },
            data: {
                x: 'x',
                columns: [
                    dates,
                    avgFailures
                ],
                type: 'bar',
                colors: {
                    'Average Failures': '#F08080'
                }
            },
            subchart: {
                show: showSubChart,
                onbrush: function (domain) {
                    subchartDomainUpdated(domain);
                }
            },
            bar: {
                width: {
                    ratio: 0.5
                }
            },
            axis: {
                x: {
                    type: 'timeseries',
                    localtime: false,
                    tick: {
                        rotate: 75,
                        format: dateFormat
                    }
                }
            }
        });
    } else { // hide all divs containing bar charts because they have a fixed height given by .c3chart in ../img/style.css
        $('#duration').css("display","none")
        $('#passes').css("display","none")
        $('#memory').css("display","none")
        $('#failGraph').css("display","none")
    }
}

