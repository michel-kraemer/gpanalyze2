// based on https://github.com/DevLab2425/angular-date-range
// licensed under the MIT license

angular.module('timerange', [])

.directive('timeRange', ['$filter', function($filter) {
  return {
    scope: {
      startTime: '@',
      endTime: '@'
    },
    template: '<span class="time-range">{{ range }}</span>',
    restrict: 'AE',
    replace: false,
    link: function(scope, element, attrs) {
      scope.$watch('startTime', function() {
        update();
      });
      scope.$watch('endTime', function() {
        update();
      });
      var update = function() {
        // DEFAULT VALUES IF ATTRIBUTES DON'T EXIST
        if (!scope.startTime) {
          scope.startTime = 0;
        }
        if (!scope.endTime) {
          scope.endTime = 24 * 60 * 60 * 1000;
        }
        scope.startTime = +scope.startTime;
        scope.endTime = +scope.endTime;
        
        var output = '';
        var fromDate = new XDate(scope.startTime, true);
        var endDate = new XDate(scope.endTime, true);
        
        if (fromDate.getFullYear() === endDate.getFullYear()) {
          if (fromDate.getMonth() === endDate.getMonth()) {
            if (fromDate.getDate() === endDate.getDate()) {
              if (fromDate.getHours() === endDate.getHours() && fromDate.getMinutes() == endDate.getMinutes()) {
                // MMM d, yyyy HH:mm
                output = fromDate.toString('MMM d, yyyy') + ' ' + fromDate.toString('HH:mm');
              } else {
                // MMM d, yyyy HH:mm - HH:mm 
                output = fromDate.toString('MMM d, yyyy') + ' ' + fromDate.toString('HH:mm') + " - " + endDate.toString('HH:mm');
              }
            } else {
              // MMM d - d, yyyy
              output = fromDate.toString('MMM d') + ' - ' + endDate.toString('d, yyyy');
            }
          } else {
            // MMM d - MMM d, yyyy
            output = fromDate.toString('MMM d') + ' - ' + endDate.toString('MMM d, yyyy');
          }
        } else {
          // MMM d, yyyy - MMM d, yyyy
          output = fromDate.toString('MMM d, yyyy') + ' - ' + endDate.toString('MMM d, yyyy');
        }
        
        scope.range = output;
      };
      update();
    }
  };
}]);
