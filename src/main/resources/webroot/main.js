angular.module("GPAnalyzeApp", ["ngMaterial", "map", "importDialog", "bottombar", "graph"])

.controller("MainMenuCtrl", function($scope, $mdDialog, ImportDialogCtrl) {
  // show import dialog
  $scope.showImportDialog = function(ev) {
    $mdDialog.show({
      controller: ImportDialogCtrl,
      templateUrl: "importDialog.tmpl.html",
      parent: angular.element(document.body),
      targetEvent: ev
    });
  };
});
