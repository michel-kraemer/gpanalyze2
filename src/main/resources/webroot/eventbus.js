angular.module("eventbus", [])

.factory("EventBus", function() {
  var eb = new vertx.EventBus("http://localhost:8080/eventbus");
  
  var initialOpenHandled = false;
  var openListeners = [];
  
  eb.onopen = function() {
    if (!initialOpenHandled) {
      openListeners.forEach(function(l) {
        l();
      });
      initialOpenHandled = true;
      openListeners = [];
    }
  };
  
  // TODO eb.onclose or eb.onerror -> try to reconnect
  
  return {
    send: function(address, message, headers, replyHandler, failureHandler) {
      return eb.send.apply(eb, arguments);
    },
    
    publish: function(address, message, headers) {
      return eb.publish.apply(eb, arguments);
    },
    
    registerHandler: function(address, handler) {
      return eb.registerHandler(address, handler);
    },
    
    unregisterHandler: function(address, handler) {
      return eb.unregisterHandler(address, handler);
    },
    
    addOpenListener: function(listener) {
      if (initialOpenHandled) {
        listener();
      } else {
        openListeners.push(listener);
      }
    }
  }
});
