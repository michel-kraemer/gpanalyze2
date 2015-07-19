angular.module("map", []/*, ["selection"]*/)

.controller("MapCtrl", function($scope, $timeout/*, MainService, SelectionService*/) {
  // initialize map
  var map = L.map('map', { zoomControl: false }).fitWorld();
  L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
    minZoom: 3
  }).addTo(map);
  
  // move to current position (but keep min zoom level)
  map.locate({
    setView: true,
    maxZoom: 3
  });
  
  // handle zoom buttons
  $scope.zoomInDisabled = false;
  $scope.zoomOutDisabled = false;
  
  $scope.zoomIn = function() {
    map.zoomIn();
  };
  
  $scope.zoomOut = function() {
    map.zoomOut();
  };
  
  var updateZoomDisabled = function() {
    // disable zoom buttons if necessary
    $scope.zoomInDisabled = map.getZoom() == map.getMaxZoom();
    $scope.zoomOutDisabled = map.getZoom() == map.getMinZoom();
  };
  
  map.on("zoomend", function() {
    // update zoom button state on every zoom
    $timeout(function() {
      updateZoomDisabled();
    }, 0);
  });
  
  /*var n = 0;
  var colors = [ "red", "blue", "green", "yellow" ];
  var selectionChangedTimeTimeout = null;
  var selectionChangedHoverTimeout = null;
  var hoveredCircle = null;
  var trackBounds = null;
  
  var trackListener = {
    onAddTrack: function(track) {
      var latlons = $.map(track.points, function(e) {
        return L.latLng(e.lat, e.lon);
      });
      var polyline = L.polyline(latlons, { color: colors[n] }).addTo(map);
      n = (n + 1) % colors.length;
      trackBounds = polyline.getBounds();
      map.fitBounds(trackBounds);
    }
  };
  
  var onSelectionChangedTime = function(obj) {
    if (selectionChangedTimeTimeout != null) {
      $timeout.cancel(selectionChangedTimeTimeout);
    }
    selectionChangedTimeTimeout = $timeout(function() {
      var points = MainService.getPoints(obj[0], obj[1]);
      for (var i = 0; i < points.length; ++i) {
        var p = points[i];
        points[i] = L.latLng(p.lat, p.lon)
      }
      
      if (points.length > 0) {
        map.fitBounds(points);
      } else if (trackBounds != null) {
        map.fitBounds(trackBounds);
      } else {
        map.fitWorld();
      }
    }, 1000);
  };
  
  var onSelectionChangedHover = function(obj) {
    if (hoveredCircle != null) {
      map.removeLayer(hoveredCircle);
    }
    if (obj != null) {
      var p = MainService.getPoint(obj);
      hoveredCircle = L.circleMarker(L.latLng(p.lat, p.lon)).addTo(map);
    }
  };
  
  var selectionListener = {
    onSelectionChanged: function(type, obj) {
      if (type == "time") {
        onSelectionChangedTime(obj);
      } else if (type == "hover") {
        onSelectionChangedHover(obj);
      }
    }
  };*/
  
  /*MainService.addListener(trackListener);
  SelectionService.addListener(selectionListener);
  $scope.$on("$destroy", function() {
    MainService.removeListener(trackListener);
    SelectionService.removeListener(selectionListener);
  });*/
});
