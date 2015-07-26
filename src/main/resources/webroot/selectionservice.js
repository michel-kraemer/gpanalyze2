angular.module("selectionservice", [])

.factory("SelectionService", function() {
  var bounds = {};
  var startTimeLocal = +new Date("2013-01-01");
  var endTimeLocal = +new Date();
  
  var listeners = [];
  
  return {
    setBounds: function(minX, minY, maxX, maxY) {
      bounds = {
          minX: minX,
          minY: minY,
          maxX: maxX,
          maxY: maxY
      };
      listeners.forEach(function(l) {
        if (l.onSetBounds) {
          l.onSetBounds(bounds);
        }
      });
    },
    
    setTimeLocal: function(newStartTimeLocal, newEndTimeLocal) {
      startTimeLocal = newStartTimeLocal;
      endTimeLocal = newEndTimeLocal;
      listeners.forEach(function(l) {
        if (l.onSetTimeLocal) {
          l.onSetTimeLocal(startTimeLocal, endTimeLocal);
        }
      });
    },
    
    addListener: function(listener) {
      listeners.push(listener);
    },
    
    getBounds: function() {
      return bounds;
    },
    
    getStartTimeLocal: function() {
      return startTimeLocal;
    },
    
    getEndTimeLocal: function() {
      return endTimeLocal;
    }
  };
});
