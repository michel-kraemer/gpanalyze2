angular.module("selectionservice", [])

.factory("SelectionService", function() {
  var bounds = {};
  var startTime = +new Date("2013-01-01");
  var endTime = +new Date();
  
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
    
    setTime: function(newStartTime, newEndTime) {
      startTime = newStartTime;
      endTime = newEndTime;
      listeners.forEach(function(l) {
        if (l.onSetTime) {
          l.onSetTime(startTime, endTime);
        }
      });
    },
    
    addListener: function(listener) {
      listeners.push(listener);
    },
    
    getBounds: function() {
      return bounds;
    },
    
    getStartTime: function() {
      return startTime;
    },
    
    getEndTime: function() {
      return endTime;
    }
  };
});
