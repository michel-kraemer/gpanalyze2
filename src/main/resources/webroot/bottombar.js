angular.module("bottombar", ["selectionservice", "timerange"])

.controller("BottomBarCtrl", function($scope, $timeout, SelectionService) {
  $scope.visible = false;
  $scope.startTimeLocal = SelectionService.getStartTimeLocal();
  $scope.endTimeLocal = SelectionService.getEndTimeLocal();
  
  $scope.toggle = function() {
    $scope.visible = !$scope.visible;
  };
  
  SelectionService.addListener({
    onSetTimeLocal: function(startTimeLocal, endTimeLocal) {
      $timeout(function() {
        $scope.startTimeLocal = startTimeLocal;
        $scope.endTimeLocal = endTimeLocal;
      }, 0);
    }
  });
});
