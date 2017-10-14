angular.module("importDialog", ["ngMaterial", "ngFileUpload", "eventbus", "trackservice"])

.factory("ImportDialogCtrl", function() {
  return function($scope, $mdDialog, $timeout, EventBus, TrackService) {
    $scope.files = [];
    $scope.importing = false;
    $scope.progress = 0;
    
    $scope.cancel = function() {
      $mdDialog.cancel();
    };
    
    $scope.ok = function() {
      $scope.importing = true;
      $scope.progress = 0;
      var file = $scope.files[0];
      var name = file.name;
      if (name.toLowerCase().endsWith(".gpx")) {
        name = name.substr(0, name.length - 4);
      }
      var fr = new FileReader();
      fr.onload = function(e) {
        var xmlstr = e.target.result;
        loadRawTracksFromFile(xmlstr, function(track) {
          // TODO support more than one track per file
          importTrack(track[0], name, function(err, trackId) {
            if (err) {
              $mdDialog.show($mdDialog.alert()
                  .parent(angular.element(document.body))
                  .title("Error")
                  .content("Could not create track. " + err.message)
                  .ok("OK"));
            } else {
              $mdDialog.hide();
              TrackService.resetTracks(trackId);
            }
          });
        });
      };
      fr.readAsText(file);
    };
    
    $scope.onSelect = function(files) {
      $scope.files = files;
    };
    
    var loadRawTracksFromFile = function(data, callback) {
      var doc = $($.parseXML(data));
      var tracks = loadRawTracksFromDoc(doc);
      if (callback) {
        callback(tracks);
      }
    };
    
    var loadRawTracksFromDoc = function(doc) {
      var result = [];
      
      doc.children().first().children("trk").each(function(i, trk) {
        $(trk).children("trkseg").each(function(j, trkseg) {
          var track = {};
          var points = [];
          
          $(trkseg).children("trkpt").each(function(k, trkpt) {
            trkpt = $(trkpt);
            var p = {};
            p.lat = parseFloat(trkpt.attr("lat"));
            p.lon = parseFloat(trkpt.attr("lon"));
            //p.speed = parseFloat(trkpt.attr("speed")) * 1.609344;
            
            var ele = trkpt.find("ele");
            if (ele.length > 0) {
              ele = ele.first();
              ele = ele.text();
              if (ele.length > 0) {
                p.ele = parseFloat(ele);
              }
            }
            
            var time = trkpt.find("time");
            if (time.length > 0) {
              time = time.first();
              time = time.text();
              if (time.length > 0) {
                p.time = time;
              }
            }
            
            /*var speed = trkpt.find("speed");
            if (speed.length > 0) {
              speed = speed.first();
              speed = speed.text();
              if (speed.length > 0) {
                p.speed = parseFloat(speed) * 3.6;
              }
            }*/
            
            points.push(p);
          });
          
          track.points = points;
          result.push(track);
        });
      });
      
      return result;
    }
    
    var importTrack = function(track, name, callback) {
      // create new track on server
      EventBus.send("tracks", {
        "action": "addTrack",
        "name": name
      }, function(err, reply) {
        if (err) {
          callback(err);
          return;
        }
        // add points to new track (max. n points per message to avoid
        // exceeding maximum WebSocket frame size)
        var trackId = reply.body.trackId;
        var doAddPoints = function(i, n) {
          if (i >= track.points.length) {
            callback(null, trackId);
            return;
          }
          $timeout(function() {
            $scope.progress = Math.round(i * 100 / track.points.length);
          }, 0);
          addPointsToTrack(track, i, n, trackId, function(err) {
            if (err) {
              EventBus.send("tracks", {
                "action": "deleteTrack",
                "trackId": trackId
              });
              callback(err);
              return;
            }
            i += n;
            doAddPoints(i, findBestWebsocketFrameSize(track, i, n));
          });
        };
        doAddPoints(0, findBestWebsocketFrameSize(track, 0, track.points.length));
      });
    };
    
    var addPointsToTrack = function(track, i, n, trackId, callback) {
      var ps = track.points.slice(i, i + n);
      EventBus.send("tracks", {
        "action": "addPoints",
        "trackId": trackId,
        "points": ps
      }, function(err, reply) {
        if (err) {
          callback(err);
        } else {
          callback(null);
        }
      })
    };
    
    var findBestWebsocketFrameSize = function(track, i, lastn) {
      var MAX_SIZE = 1024 * 63; // 63KB, leave some room for overhead
      var maxn = track.points.length - i;
      var curn = lastn;
      var curs = curn >> 1;
      var mindist = Number.MAX_VALUE;
      var minn = maxn;
      
      if (curn < 2) {
        return curn;
      }
      
      while (true) {
        var ps = track.points.slice(i, i + curn);
        var json = JSON.stringify(ps);
        json = JSON.stringify({"json": json}); // escape JSON
        var dist = MAX_SIZE - json.length;
        if (dist >= 0 && mindist > Math.abs(dist)) {
          mindist = Math.abs(dist);
          minn = curn;
        }
        if (dist < 0) {
          curn = curn - curs;
        } else if (dist > 0) {
          curn = curn + curs;
        }
        curs = curs >> 1;
        if (curs == 0) {
          return minn;
        }
      }
    };
  };
});
