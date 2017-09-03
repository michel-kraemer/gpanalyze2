angular.module("eventbus", [])

.factory("EventBus", function() {
  var eb;
  
  var initialOpenHandled = false;
  var openListeners = [];
  
  var connect = function() {
    eb = new EventBus("http://localhost:8080/eventbus");
    initialOpenHandled = false;
    eb.onopen = function() {
      if (!initialOpenHandled) {
        openListeners.forEach(function(l) {
          l();
        });
        initialOpenHandled = true;
        openListeners = [];
      }
    };
  };
  connect();
  
  var reconnect = function(done) {
    var retry = function() {
      if (eb.state === EventBus.OPEN) {
        done();
      } else if (eb.state === EventBus.CONNECTING) {
        setTimeout(function() {
          retry()
        }, 1000);
      } else {
        console.error("Reconnecting ...");
        setTimeout(function() {
          connect();
          retry();
        }, 2000);
      }
    };
    retry();
  };
  
  return {
    send: function(address, message, headers, handler) {
      var a = arguments;
      reconnect(function() {
        eb.send.apply(eb, a);
      });
    },
    
    publish: function(address, message, headers) {
      var a = arguments;
      reconnect(function() {
        eb.publish.apply(eb, a);
      });
    },
    
    registerHandler: function(address, handler) {
      reconnect(function() {
        eb.registerHandler(address, handler);
      });
    },
    
    unregisterHandler: function(address, handler) {
      reconnect(function() {
        eb.unregisterHandler(address, handler);
      });
    },
    
    addOpenListener: function(listener) {
      reconnect(function() {});
      if (eb.state === EventBus.OPEN && initialOpenHandled) {
        listener();
      } else {
        openListeners.push(listener);
      }
    }
  }
});
