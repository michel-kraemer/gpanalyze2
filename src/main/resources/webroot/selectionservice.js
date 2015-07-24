angular.module("selectionservice", [])

.factory("SelectionService", function() {
  var bounds = {};
  
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
    
    addListener: function(listener) {
      listeners.push(listener);
    },
    
    getBounds: function() {
      return bounds;
    }
  };
});
