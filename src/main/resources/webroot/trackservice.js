angular.module("trackservice", ["ngMaterial", "eventbus", "selectionservice"])

.factory("TrackService", function($mdDialog, $timeout, EventBus, SelectionService) {
  var resetTracksTimer;
  
  var openTracks = [];
  var trackListeners = [];
  
  var makeLoadTrackHandler = function(onAdd, onDone) {
    var loadTrackHandler = function(track, replyHandler) {
      if (track.points) {
        var oldtrack;
        var found = -1;
        for (var i = 0; i < openTracks.length; ++i) {
          if (openTracks[i].trackId == track.trackId) {
            oldtrack = openTracks[i];
            found = i;
            break;
          }
        }
        if (found >= 0) {
          openTracks[found] = track;
        } else {
          openTracks.push(track);
        }
        trackListeners.forEach(function(l) {
          if (l.onRemove && oldtrack) {
            l.onRemove(oldtrack);
          }
          if (l.onAdd) {
            l.onAdd(track);
          }
        });
        onAdd(track.trackId);
      }
      
      if (replyHandler) {
        replyHandler({}, loadTrackHandler);
      } else {
        onDone();
      }
    };
    
    return loadTrackHandler;
  };
  
  var loadAllTracks = function() {
    // save track ids of currently open tracks
    var oldOpenTracks = openTracks.map(function(t) { return t.trackId; });
    
    var loadTrackHandler = makeLoadTrackHandler(function(trackId) {
      // every time a track was loaded remove its track id from 'oldOpenTracks'
      var i = oldOpenTracks.indexOf(trackId);
      if (i >= 0) {
        oldOpenTracks.splice(i, 1);
      }
    }, function() {
      // after all tracks have been loaded remove all tracks that were
      // loaded before, but have not been loaded again
      oldOpenTracks.forEach(function(oldTrackId) {
        var f = -1;
        for (var i = 0; i < openTracks.length; ++i) {
          if (openTracks[i].trackId == oldTrackId) {
            f = i;
            break;
          }
        }
        if (f >= 0) {
          trackListeners.forEach(function(l) {
            if (l.onRemove) {
              l.onRemove(openTracks[i]);
            }
          });
          openTracks.splice(i, 1);
        }
      });
    });
    EventBus.send("tracks", {
      action: "findTracks",
      bounds: SelectionService.getBounds()
    }, loadTrackHandler, function(err) {
      $mdDialog.show($mdDialog.alert()
          .parent(angular.element(document.body))
          .title("Error")
          .content("Could not load tracks. " + err.message)
          .ok("OK"));
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
    }, 1000);
  };
  
  SelectionService.addListener({
    onSetBounds: function(bounds) {
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
    }
  };
});
