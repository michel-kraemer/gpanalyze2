angular.module("bottombar", ["selectionservice", "stateservice", "timerange"])

.controller("BottomBarCtrl", function($scope, $timeout, SelectionService, StateService) {
  $scope.visible = false;
  $scope.startTimeLocal = SelectionService.getStartTimeLocal();
  $scope.endTimeLocal = SelectionService.getEndTimeLocal();
  $scope.displaySpeed = false;

  var pushStateTimer = undefined;
  var initialDisplaySpeed = true;

  StateService.addChangeListener({
    bottomBarVisible: $scope.visible,
    displaySpeed: $scope.displaySpeed,
    startTimeLocal: $scope.startTimeLocal,
    endTimeLocal: $scope.endTimeLocal
  }, function(state) {
    if (state.startTimeLocal) {
      $timeout(function() {
        $scope.visible = state.bottomBarVisible;
        $scope.displaySpeed = state.displaySpeed;
        $scope.startTimeLocal = state.startTimeLocal;
        $scope.endTimeLocal = state.endTimeLocal;
        SelectionService.setTimeLocal(state.startTimeLocal, state.endTimeLocal);
      }, 0);
    }
  });

  $scope.toggle = function() {
    $scope.visible = !$scope.visible;
    StateService.pushState({
      bottomBarVisible: $scope.visible
    })
  };
  
  $scope.setDisplaySpeed = function(displaySpeed) {
    $scope.displaySpeed = displaySpeed;
  };
  
  $scope.$watch("displaySpeed", function() {
    $scope.$broadcast("onDisplaySpeedChanged", $scope.displaySpeed);
    if (!initialDisplaySpeed) {
      // do not push state right at the beginning
      StateService.pushState({
        displaySpeed: $scope.displaySpeed
      });
    } else {
      initialDisplaySpeed = false;
    }
  });
  
  SelectionService.addListener({
    onSetTimeLocal: function(startTimeLocal, endTimeLocal) {
      $timeout(function() {
        $scope.startTimeLocal = startTimeLocal;
        $scope.endTimeLocal = endTimeLocal;
        if (pushStateTimer) {
          $timeout.cancel(pushStateTimer);
        }
        pushStateTimer = $timeout(function() {
          StateService.pushState({
            startTimeLocal: $scope.startTimeLocal,
            endTimeLocal: $scope.endTimeLocal
          })
          pushStateTimer = undefined;
        }, 200);
      }, 0);
    }
  });
});
