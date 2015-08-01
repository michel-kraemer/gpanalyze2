angular.module("graph", ["trackservice", "selectionservice"])

.controller("GraphCtrl", function($scope, $timeout, TrackService, SelectionService) {
  var redrawTimer;
  
  var graph = $("#graph");
  var canvas = $("#graph_canvas");
  
  var tracks = {};
  
  var margin = {top: 15, right: 50, bottom: 40, left: 50};
  var width = graph.width() - margin.left - margin.right;
  var height = graph.height() - margin.top - margin.bottom;
  
  // add d3 graph
  graph = d3.select("#graph")
    .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  
  var customTimeFormat = d3.time.format.utc.multi([
    [".%L", function(d) { return d.getUTCMilliseconds(); }],
    [":%S", function(d) { return d.getUTCSeconds(); }],
    ["%H:%M", function(d) { return d.getUTCMinutes(); }],
    ["%H:00", function(d) { return d.getUTCHours(); }],
    ["%a %d", function(d) { return d.getUTCDay() && d.getUTCDate() != 1; }],
    ["%b %d", function(d) { return d.getUTCDate() != 1; }],
    ["%B", function(d) { return d.getUTCMonth(); }],
    ["%Y", function() { return true; }]
  ]);
  
  // scales used to calculate screen coordinates for x and y values
  var xScale = d3.time.scale.utc()
    .range([0, width])
    .nice();
  var yScale = d3.scale.linear()
    .range([height, 0])
    .nice();
  
  // the x and y axis
  var xAxis = d3.svg.axis()
    .scale(xScale)
    .outerTickSize(0)
    .innerTickSize(-height)
    .tickFormat(customTimeFormat)
    .orient("bottom");
  var yAxis = d3.svg.axis()
    .scale(yScale)
    .innerTickSize(-width)
    .outerTickSize(0)
    .orient("left");
  
  // the current view of the x scale (in milliseconds sind epoch)
  xScale.domain([+new Date("2013-01-01"), +new Date()]);
  
  // the current view of the y scale (in meters)
  yScale.domain([-100, 1600]);
  
  // add x and y axis to graph
  graph.append("g")
    .attr("class", "x axis")
    .attr("transform", "translate(0," + height + ")")
    .call(xAxis);
  graph.append("g")
    .attr("class", "y axis")
    .call(yAxis);
  
  // add zoom behaviour that affects the x scale
  var zoom = d3.behavior.zoom()
    .x(xScale)
    .on("zoom", function() {
      // on zoom redraw x axis and then redraw graph
      graph.select('.x.axis').call(xAxis);
      redrawGraph();
      
      // notify selection service
      var startTimeLocal = +xScale.invert(0);
      var endTimeLocal = +xScale.invert(width);
      SelectionService.setTimeLocal(startTimeLocal, endTimeLocal);
    });
  
  var mousemove = function() {
    var x0 = +xScale.invert(d3.mouse(this)[0]);
    SelectionService.setHoverTimeLocal(x0);
  };
  
  var mouseout = function() {
    SelectionService.setHoverTimeLocal(undefined);
  };
  
  // add transparent overlay which receives mouse events
  var overlay = d3.svg.area()
    .x(function(t) { return xScale(t); })
    .y0(0)
    .y1(height);
  graph.append("path")
    .attr("class", "overlay")
    .attr("d", overlay([xScale.domain()[0], xScale.domain()[1]]))
    .on("mouseout", mouseout)
    .on("mousemove", mousemove)
    .call(zoom);
  
  // add canvas for high performance drawing
  canvas.css({top: margin.top, left: margin.left});
  canvas.width(width);
  canvas.height(height);
  canvas.attr("width", width * 2);
  canvas.attr("height", height * 2);
  var context = canvas[0].getContext("2d");
  
  // draw a track to the canvas
  var drawTrack = function(track) {
    var hoverTimeLocal = SelectionService.getHoverTimeLocal();
    var hoverTimeCX;
    var hoverTimeCY;
    if (hoverTimeLocal && hoverTimeLocal >= track.startTimeLocal && hoverTimeLocal <= track.endTimeLocal) {
      hoverTimeCX = Math.round(xScale(hoverTimeLocal) * 2);
    }
    
    context.beginPath();
    track.points.forEach(function(p, i) {
      var timeLocal = p.time + track.timeZoneOffset;
      var cx = Math.round(xScale(timeLocal) * 2);
      var cy = Math.round(yScale(p.ele) * 2);
      if (i == 0) {
        context.moveTo(cx, cy);
      } else {
        context.lineTo(cx, cy);
      }
      
      if (hoverTimeLocal && i < track.points.length - 1) {
        var timeLocalJ = track.points[i + 1].time + track.timeZoneOffset;
        if (hoverTimeLocal >= timeLocal && hoverTimeLocal <= timeLocalJ) {
          var dist = timeLocalJ - timeLocal;
          var weight = 1 - ((hoverTimeLocal - timeLocal) / dist);
          var weightj = 1 - ((timeLocalJ - hoverTimeLocal) / dist);
          var hoverEle = p.ele * weight + track.points[i + 1].ele * weightj;
          hoverTimeCY = Math.round(yScale(hoverEle) * 2);
        }
      }
    });
    context.strokeStyle = "#3f51b5";
    context.lineWidth = 3;
    context.stroke();
    
    if (hoverTimeCX && hoverTimeCY) {
      context.beginPath();
      context.arc(hoverTimeCX, hoverTimeCY, 10, 0, 2 * Math.PI);
      context.fillStyle = "#ff4081";
      context.fill();
      context.strokeStyle = "#fff";
      context.stroke();
    }
  };
  
  // redraw all tracks
  var redrawGraph = function() {
    context.clearRect(0, 0, width * 2, height * 2);
    Object.keys(tracks).forEach(function(trackId) {
      drawTrack(tracks[trackId]);
    });
  };
  
  var startRedrawTimer = function() {
    if (redrawTimer) {
      $timeout.cancel(redrawTimer);
    }
    redrawTimer = $timeout(function() {
      redrawGraph();
    }, 100);
  };
  
  // add or remove tracks from the graph
  TrackService.addListener({
    onAdd: function(track) {
      tracks[track.trackId] = track;
      startRedrawTimer();
    },
    
    onRemove: function(track) {
      delete tracks[track.trackId];
      startRedrawTimer();
    }
  });
  
  SelectionService.addListener({
    onSetHoverTimeLocal: function(hoverTimeLocal) {
      redrawGraph();
    }
  });
});
