angular.module("statistics", ["ngMaterial", "selectionservice", "trackservice", "eventbus"])

.controller("StatisticsCtrl", function($scope, $timeout, $mdDialog, SelectionService, TrackService, EventBus) {
  $scope.time_hours = 0;
  $scope.time_minutes = "";
  $scope.distance = 0;
  $scope.elevation_gain = 0;
  $scope.elevation_loss = 0;
  $scope.elevation_max = "";
  $scope.elevation_min = "";
  $scope.redrawing = false;
  
  var tracks = {};
  var redrawTimer;
  var scheduleRedraw = false;
  
  var startRedrawTimer = function() {
    if (redrawTimer) {
      $timeout.cancel(redrawTimer);
    }
    redrawTimer = $timeout(function() {
      redraw();
    }, 100);
  };
  
  var redraw = function() {
    if ($scope.redrawing) {
      scheduleRedraw = true;
      return;
    }
    
    $scope.redrawing = true;
    EventBus.send("statistics", {
      action: "calculate",
      trackIds: Object.keys(tracks),
      startTimeLocal: SelectionService.getStartTimeLocal(),
      endTimeLocal: SelectionService.getEndTimeLocal()
    }, function(err, reply) {
      if (err) {
        $scope.redrawing = false;
        scheduleRedraw = false;
        $mdDialog.show($mdDialog.alert()
          .parent(angular.element(document.body))
          .title("Error")
          .content("Could not calculate statistics. " + err.message)
          .ok("OK"));
      } else {
        var stats = reply.body;
        $timeout(function() {
          $scope.redrawing = false;
          if (scheduleRedraw) {
            scheduleRedraw = false;
            redraw();
          } else {
            $scope.time_hours = Math.floor(stats.time / 1000 / 60 / 60);
            $scope.time_minutes = Math.floor(stats.time / 1000 / 60) % 60;
            $scope.distance = stats.distance;
            $scope.elevation_gain = stats.elevationGain;
            $scope.elevation_loss = stats.elevationLoss;
            $scope.elevation_max = stats.elevationMax || "";
            $scope.elevation_min = stats.elevationMin || "";
          }
        }, 0);
      }
    });
  };
  
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
  
  SelectionService.addListener({
    onSetTimeLocal: function(startTimeLocal, endTimeLocal) {
      startRedrawTimer();
    }
  });
});
