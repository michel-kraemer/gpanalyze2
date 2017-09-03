angular.module("map", ["trackservice", "selectionservice", "stateservice"])

.controller("MapCtrl", function($scope, $timeout, TrackService, SelectionService, StateService) {
  var trackPolylines = {};
  var hoverCircle;
  
  // initialize map
  var map = L.map('map', { zoomControl: false }).fitWorld();
  L.tileLayer('/map/http%3A%2F%2Fa.tile.osm.org%2F{z}%2F{x}%2F{y}.png', {
    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
    minZoom: 3
  }).addTo(map);

  StateService.addChangeListener(function(state) {
    if (state.lat) {
      var center = L.latLng(state.lat, state.lng);
      map.setView(center, state.zoom);
    } else {
      // move to current position (but keep min zoom level)
      map.locate({
        setView: true,
        maxZoom: 3
      });
    }
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
  
  var updateState = function() {
    StateService.pushState({
      zoom: map.getZoom(),
      lat: map.getCenter().lat,
      lng: map.getCenter().lng
    });
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
  
  var updateHover = function(hoverTimeLocal) {
    if (hoverCircle) {
      map.removeLayer(hoverCircle);
      hoverCircle = undefined;
    }
    if (hoverTimeLocal) {
      var p = TrackService.getPoint(hoverTimeLocal);
      if (p) {
        hoverCircle = L.circleMarker(L.latLng(p.lat, p.lon)).addTo(map);
      }
    }
  };
  
  // calculate distance to line segment
  // see http://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
  var distPoints = function(v, w) {
    var sqr = function(x) {
      return x * x;
    };
    return sqr(v.lat - w.lat) + sqr(v.lon - w.lon);
  };
  var distToSegment = function(p, v, w) {
    var distToSegmentSquared = function(p, v, w) {
      var l2 = distPoints(v, w);
      if (l2 == 0) {
        return distPoints(p, v);
      }
      
      var t = ((p.lat - v.lat) * (w.lat - v.lat) + (p.lon - v.lon) * (w.lon - v.lon)) / l2;
      if (t < 0) {
        return distPoints(p, v);
      }
      if (t > 1) {
        return distPoints(p, w);
      }
      
      return distPoints(p, {
        lat: v.lat + t * (w.lat - v.lat),
        lon: v.lon + t * (w.lon - v.lon)
      });
    };
    
    return Math.sqrt(distToSegmentSquared(p, v, w));
  }
  
  // find the track point that is closest to p
  var findClosestPoint = function(p, track) {
    var mindist = Number.MAX_VALUE;
    var closestpoint1;
    var closestpoint2;
    
    for (var i = 0; i < track.points.length; ++i) {
      if (i == track.points.length - 1) {
        break;
      }
      var p1 = track.points[i];
      var p2 = track.points[i + 1];
      var dist = distToSegment(p, p1, p2);
      if (dist < mindist) {
        mindist = dist;
        closestpoint1 = p1;
        closestpoint2 = p2;
      }
    }
    
    var dist1 = distPoints(p, closestpoint1);
    var dist2 = distPoints(p, closestpoint2);
    return (dist1 < dist2 ? closestpoint1 : closestpoint2);
  };
  
  // add track listener
  var trackListener = {
      onAdd: function(track) {
        var latlons = $.map(track.points, function(e) {
          return L.latLng(e.lat, e.lon);
        });
        var polyline = L.polyline(latlons, { color: "red" });
        polyline.addTo(map);
        polyline.on("mousemove", function(e) {
          var p = findClosestPoint({ lat: e.latlng.lat, lon: e.latlng.lng }, track);
          SelectionService.setHoverTimeLocal(p.time + track.timeZoneOffset);
        });
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
  
  SelectionService.addListener({
    onSetHoverTimeLocal: function(hoverTimeLocal) {
      updateHover(hoverTimeLocal);
    }
  });
});
