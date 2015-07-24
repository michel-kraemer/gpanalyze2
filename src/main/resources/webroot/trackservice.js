angular.module("trackservice", ["ngMaterial", "eventbus"])

.factory("TrackService", function($mdDialog, EventBus) {
  var openTracks = [];
  var trackListeners = [];
  
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
    }
    
    if (replyHandler) {
      replyHandler({}, loadTrackHandler);
    }
  };
  
  var loadAllTracks = function() {
    EventBus.send("tracks", {
      action: "findTracks"
    }, loadTrackHandler, function(err) {
      $mdDialog.show($mdDialog.alert()
          .parent(angular.element(document.body))
          .title("Error")
          .content("Could not load tracks. " + err.message)
          .ok("OK"));
    });
  };
  
  // initially load all tracks
  EventBus.addOpenListener(loadAllTracks);
  
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
