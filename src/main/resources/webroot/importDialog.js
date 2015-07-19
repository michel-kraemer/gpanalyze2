angular.module("importDialog", ["ngMaterial", "ngFileUpload"])

.factory("ImportDialogCtrl", function() {
  return function($scope, $mdDialog) {
    $scope.files = [];
    $scope.importing = false;
    
    $scope.cancel = function() {
      $mdDialog.cancel();
    };
    
    $scope.ok = function() {
      $scope.importing = true;
      var fr = new FileReader();
      fr.onload = function(e) {
        var xmlstr = e.target.result;
        loadRawTracksFromFile(xmlstr, function(track) {
          $mdDialog.hide(track);
        });
      };
      fr.readAsText($scope.files[0]);
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
  };
});
