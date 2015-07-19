angular.module("GPAnalyzeApp", ["ngMaterial", "map", "importDialog", "eventbus"])

.controller("MainMenuCtrl", function($scope, $mdDialog, ImportDialogCtrl, EventBus) {
  // show import dialog
  $scope.showImportDialog = function(ev) {
    $mdDialog.show({
      controller: ImportDialogCtrl,
      templateUrl: "importDialog.tmpl.html",
      parent: angular.element(document.body),
      targetEvent: ev,
      clickOutsideToClose: true
    }).then(function(track) {
      // TODO support more than one track per file
      addTrack(track[0], function(err) {
        if (err) {
          $mdDialog.show($mdDialog.alert()
              .parent(angular.element(document.body))
              .title("Error")
              .content("Could not create track. " + err.message)
              .ok('OK'));
        } else {
          // TODO display new track and zoom to it
        }
      });
    });
  };
  
  var addTrack = function(track, callback) {
    // create new track on server
    EventBus.send("tracks", {
      "action": "addTrack"
    }, function(reply) {
      // add points to new track (max. 20 points per message)
      var trackId = reply.id;
      var doAddPoints = function(i, n) {
        if (i >= track.points.length) {
          return;
        }
        addPointsToTrack(track, i, n, trackId, function(err) {
          if (err) {
            EventBus.send("tracks", {
              "action": "deleteTrack",
              "trackId": trackId
            });
            callback(err);
            return;
          }
          doAddPoints(i + n, n);
        });
      };
      doAddPoints(0, 20);
    }, function(err) {
      callback(err);
    });
  };
  
  var addPointsToTrack = function(track, i, n, trackId, callback) {
    var ps = track.points.slice(i, i + n);
    EventBus.send("tracks", {
      "action": "addPoints",
      "trackId": trackId,
      "points": ps
    }, function(reply) {
      callback(null);
    }, function(err) {
      callback(err);
    })
  };
});
