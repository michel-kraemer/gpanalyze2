angular.module("stateservice", [])

.factory("StateService", function() {
  var state = {
    lat: undefined,
    lng: undefined,
    zoom: 3
  };
  var listeners = [];

  var makeQueryString = function() {
    var qs = "@" + state.lat + "," + state.lng + "," + state.zoom;
    var result;
    var re = /(.*?)@[^\/]+(.*)/;
    var match = re.exec(location.hash);
    if (match) {
      result = match[1] + qs + match[2];
    } else {
      result = location.pathname;
      if (result[result.length - 1] != "/") {
        result = result + "/";
      }
      result += qs;
    }
    if (result[0] != '#') {
      result = "#" + result;
    }
    return result;
  };

  var loadState = function(s, listener) {
    if (s) {
      state = s;
    } else {
      var re = /@([0-9.]+),([0-9.]+),([0-9]+)/;
      var match = re.exec(location.hash);
      if (match) {
        state.lat = match[1];
        state.lng = match[2];
        state.zoom = match[3];
      }
    }
    if (listener) {
      listener(state);
    } else {
      listeners.forEach(function(v) {
        v(state);
      });
    }
  };
  loadState(history.state);

  $(window).bind("popstate", function(e) {
    var s = e.originalEvent.state;
    loadState(s);
  });

  return {
    pushState: function(s) {
      var changed = false;
      Object.keys(s).forEach(function(k) {
        if (state[k] !== s[k]) {
          changed = true;
          state[k] = s[k];
        }
      });
      if (changed) {
        history.pushState(state, "", makeQueryString());
      }
    },

    addChangeListener: function(listener) {
      listeners.push(listener);
      loadState(history.state, listener);
    }
  };
});
