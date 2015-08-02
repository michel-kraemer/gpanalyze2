angular.module("bottombar", ["selectionservice", "timerange"])

.controller("BottomBarCtrl", function($scope, $timeout, SelectionService) {
  $scope.visible = false;
  $scope.startTimeLocal = SelectionService.getStartTimeLocal();
  $scope.endTimeLocal = SelectionService.getEndTimeLocal();
  $scope.displaySpeed = false;
  
  $scope.toggle = function() {
    $scope.visible = !$scope.visible;
  };
  
  $scope.setDisplaySpeed = function(displaySpeed) {
    $scope.displaySpeed = displaySpeed;
  };
  
  $scope.$watch("displaySpeed", function() {
    $scope.$broadcast("onDisplaySpeedChanged", $scope.displaySpeed);
  });
  
  SelectionService.addListener({
    onSetTimeLocal: function(startTimeLocal, endTimeLocal) {
      $timeout(function() {
        $scope.startTimeLocal = startTimeLocal;
        $scope.endTimeLocal = endTimeLocal;
      }, 0);
    }
  });
});
