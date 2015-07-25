angular.module("graph", ["trackservice", "selectionservice"])

.controller("GraphCtrl", function($scope, $timeout, TrackService, SelectionService) {
  var redrawTimer;
  
  var graph = $("#graph");
  var canvas = $("#graph_canvas");
  
  var tracks = {};
  
  var margin = {top: 25, right: 50, bottom: 40, left: 50};
  var width = graph.width() - margin.left - margin.right;
  var height = graph.height() - margin.top - margin.bottom;
  
  // add d3 graph
  graph = d3.select("#graph")
    .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  
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
    .orient("bottom");
  var yAxis = d3.svg.axis()
    .scale(yScale)
    .orient("left");
  
  // the current view of the x scale (in milliseconds sind epoch)
  xScale.domain([+new Date("2013-01-01"), +new Date()]);
  
  // the current view of the y scale (in meters)
  yScale.domain([0, 1500]);
  
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
      var startTime = +xScale.invert(0);
      var endTime = +xScale.invert(width);
      SelectionService.setTime(startTime, endTime);
    });
  
  // add transparent overlay which receives the mouse events for zooming and panning
  var overlay = d3.svg.area()
    .x(function(t) { return xScale(t); })
    .y0(0)
    .y1(height);
  graph.append("path")
    .attr("class", "overlay")
    .attr("d", overlay([xScale.domain()[0], xScale.domain()[1]]))
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
    context.beginPath();
    track.points.forEach(function(p, i) {
      var cx = xScale(p.time + track.timeZoneOffset) * 2;
      var cy = yScale(p.ele) * 2;
      if (i == 0) {
        context.moveTo(cx, cy);
      } else {
        context.lineTo(cx, cy);
      }
    });
    context.strokeStyle = "#fff";
    context.lineWidth = 3;
    context.stroke();
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
    }, 500);
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
});
