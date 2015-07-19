angular.module("eventbus", [])

.factory("EventBus", function() {
  var eb = new vertx.EventBus("http://localhost:8080/eventbus");
  return eb;
});
