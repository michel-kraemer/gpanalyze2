angular.module("map", ["trackservice"])

.controller("MapCtrl", function($scope, $timeout, TrackService) {
  var trackPolylines = {};
  var doUpdateState = true;
  var doResetTracks = true;
  var resetTracksTimer;
  
  // initialize map
  var map = L.map('map', { zoomControl: false }).fitWorld();
  L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
    minZoom: 3
  }).addTo(map);
  
  var loadState = function(state) {
    if (state) {
      map.setView(state.center, state.zoom);
    } else {
      var re = /@([0-9.]+),([0-9.]+),([0-9]+)/;
      var match = re.exec(location.hash);
      if (match) {
        map.setView(L.latLng(match[1], match[2]), match[3]);
      } else {
        // move to current position (but keep min zoom level)
        doResetTracks = false;
        map.locate({
          setView: true,
          maxZoom: 3
        });
      }
    }
  };
  loadState(history.state);
  
  $(window).bind("popstate", function(e) {
    var s = e.originalEvent.state;
    doUpdateState = false;
    loadState(s);
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
  
  var makeQueryString = function() {
    var qs = "@" + map.getCenter().lat + "," + map.getCenter().lng + "," + map.getZoom();
    var result;
    var re = /(.*?)@[^\/]+(.*)/;
    var match = re.exec(location.hash);
    if (match) {
      result = match[1] + qs + match[2];
    } else {
      result = location.pathname;
      if (result[result.length - 1] != "/") {
        result = result + "/";
      }
      result += qs;
    }
    if (result[0] != '#') {
      result = "#" + result;
    }
    return result;
  };
  
  var updateState = function() {
    if (doUpdateState) {
      history.pushState({zoom: map.getZoom(), center: map.getCenter()}, "", makeQueryString());
    } else {
      doUpdateState = true;
    }
  };
  
  map.on("zoomend", function() {
    // update zoom button state on every zoom
    $timeout(function() {
      updateZoomDisabled();
    }, 0);
    updateState();
    startResetTracksTimer();
  });
  
  map.on("dragend", function() {
    updateState();
    startResetTracksTimer();
  });
  
  var startResetTracksTimer = function() {
    if (doResetTracks) {
      if (resetTracksTimer) {
        $timeout.cancel(resetTracksTimer);
      }
      resetTracksTimer = $timeout(function() {
        TrackService.resetTracks();
      }, 1000);
    } else {
      doResetTracks = true;
    }
  }
  
  // add track listener
  var trackListener = {
      onAdd: function(track) {
        var latlons = $.map(track.points, function(e) {
          return L.latLng(e.lat, e.lon);
        });
        var polyline = L.polyline(latlons, { color: "red" });
        polyline.addTo(map);
        trackPolylines[track.trackId] = polyline;
      },
      
      onRemove: function(track) {
        var polyline = trackPolylines[track.trackId];
        if (polyline) {
          map.removeLayer(polyline);
          delete trackPolylines[track.trackId];
        }
      }
  };
  TrackService.addListener(trackListener);
  
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
