angular.module("bottombar", [])

.controller("BottomBarCtrl", function($scope) {
  $scope.visible = false;
  
  $scope.toggle = function() {
    $scope.visible = !$scope.visible;
  };
});
