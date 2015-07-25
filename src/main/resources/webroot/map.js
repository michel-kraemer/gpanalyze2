angular.module("map", ["trackservice", "selectionservice"])

.controller("MapCtrl", function($scope, $timeout, TrackService, SelectionService) {
  var trackPolylines = {};
  var doUpdateState = true;
  
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
  
  var updateSelection = function() {
    var b = map.getBounds();
    SelectionService.setBounds(b.getSouth(), b.getWest(), b.getNorth(), b.getEast());
  };
  
  map.on("zoomend", function() {
    // update zoom button state on every zoom
    $timeout(function() {
      updateZoomDisabled();
    }, 0);
    updateState();
    updateSelection();
  });
  
  map.on("dragend", function() {
    updateState();
    updateSelection();
  });
  
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
});
