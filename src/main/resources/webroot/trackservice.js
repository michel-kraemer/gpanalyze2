angular.module("trackservice", ["ngMaterial", "eventbus"])

.factory("TrackService", function($mdDialog, EventBus) {
  var openTracks = [];
  var trackListeners = [];
  
  var loadTrackHandler = function(track, replyHandler) {
    if (track.points) {
      trackListeners.forEach(function(l) {
        if (l.onAdd) {
          l.onAdd(track);
        }
      });
      openTracks.push(track);
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
      openTracks.forEach(function(track) {
        trackListeners.forEach(function(l) {
          if (l.onRemove) {
            l.onRemove(track);
          }
        });
      });
      openTracks = [];
      loadAllTracks();
    }
  };
});
