angular.module("trackservice", ["ngMaterial", "eventbus", "selectionservice"])

.factory("TrackService", function($mdDialog, $timeout, EventBus, SelectionService) {
  var resetTracksTimer;
  
  var openTracks = [];
  var trackListeners = [];
  
  var findTrack = function(trackId) {
    for (var i = 0; i < openTracks.length; ++i) {
      if (openTracks[i].trackId == trackId) {
        return i;
      }
    }
    return -1;
  };
  
  var updateOrAddTrack = function(track) {
    var oldTrackPos = findTrack(track.trackId);
    trackListeners.forEach(function(l) {
      if (l.onRemove && oldTrackPos >= 0) {
        l.onRemove(openTracks[oldTrackPos]);
      }
      if (l.onAdd) {
        l.onAdd(track);
      }
    });
    if (oldTrackPos >= 0) {
      openTracks[oldTrackPos] = track;
    } else {
      openTracks.push(track);
    }
  };
  
  var loadTracks = function(tracks) {
    if (tracks.length == 0) {
      return;
    }
    
    var trackInfo = tracks[0];
    var oldTrackPos = findTrack(trackInfo.trackId);
    if (oldTrackPos >= 0 && openTracks[oldTrackPos].resolution == trackInfo.resolution) {
      // we don't have to update the track. continue with the next one.
      loadTracks(tracks.slice(1));
      return;
    }
    
    EventBus.send("tracks", {
      action: "getTrack",
      trackId: trackInfo.trackId,
      resolution: trackInfo.resolution
    }, function(err, reply) {
      if (err) {
        $mdDialog.show($mdDialog.alert()
        .parent(angular.element(document.body))
        .title("Error")
        .content("Could not load track with id " + trackInfo.trackId + ". " + err.message)
        .ok("OK"));
      } else {
        // call updateOrAddTrack() asynchronously so we can start
        // loading the next track already
        var track = reply.body;
        $timeout(function() {
          updateOrAddTrack(track);
        }, 0);
        loadTracks(tracks.slice(1));
      }
    });
  };
  
  var retainTracks = function(tracksToRetain) {
    var newOpenTracks = [];
    openTracks.forEach(function(track) {
      var found = false;
      for (var i = 0; i < tracksToRetain.length; ++i) {
        if (track.trackId == tracksToRetain[i].trackId) {
          found = true;
          break;
        }
      }
      if (found) {
        newOpenTracks.push(track);
      } else {
        trackListeners.forEach(function(l) {
          if (l.onRemove) {
            l.onRemove(track);
          }
        });
      }
    });
    openTracks = newOpenTracks;
  };
  
  var loadAllTracks = function() {
    var bounds = SelectionService.getBounds();
    var width = bounds.maxX - bounds.minX;
    var height = bounds.maxY - bounds.minY;
    if (width > 90 || height > 90) {
      bounds = undefined; // load all tracks
    }
    EventBus.send("tracks", {
      action: "findTracks",
      bounds: bounds,
      startTimeLocal: SelectionService.getStartTimeLocal(),
      endTimeLocal: SelectionService.getEndTimeLocal()
    }, function(err, reply) {
      if (err) {
        $mdDialog.show($mdDialog.alert()
          .parent(angular.element(document.body))
          .title("Error")
          .content("Could not load tracks. " + err.message)
          .ok("OK"));
      } else {
        var tracks = reply.body;
        retainTracks(tracks);
        loadTracks(tracks);
      }
    });
  };
  
  // initially load all tracks
  EventBus.addOpenListener(function() {
    startResetTracksTimer();
  });
  
  var startResetTracksTimer = function() {
    if (resetTracksTimer) {
      $timeout.cancel(resetTracksTimer);
    }
    resetTracksTimer = $timeout(function() {
      loadAllTracks();
    }, 500);
  };
  
  SelectionService.addListener({
    onSetBounds: function(bounds) {
      startResetTracksTimer();
    },
    
    onSetTimeLocal: function(startTimeLocal, endTimeLocal) {
      startResetTracksTimer();
    }
  });
  
  return {
    addListener: function(listener) {
      trackListeners.push(listener);
      openTracks.forEach(function(track) {
        listener.onAdd(track);
      });
    },
    
    resetTracks: function() {
      loadAllTracks();
    },
    
    getPoint: function(timeLocal) {
      for (var i = 0; i < openTracks.length; ++i) {
        var track = openTracks[i];
        if (timeLocal < track.startTimeLocal || timeLocal > track.endTimeLocal) {
          continue;
        }
        
        for (var j = 0; j < track.points.length; ++j) {
          var p = track.points[j];
          var ptime = p.time + track.timeZoneOffset;
          if (ptime >= timeLocal) {
            if (j == 0) {
              return p;
            } else {
              var np = track.points[j - 1];
              var nptime = np.time + track.timeZoneOffset;
              if (ptime - timeLocal > timeLocal - nptime) {
                return track.points[j - 1];
              } else {
                return p;
              }
            }
          }
        }
      }
      
      return null;
    },
  };
});
