package de.undercouch.gpanalyze2;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TracksVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(TracksVerticle.class.getName());
    
    private List<JsonObject> tracks = new ArrayList<>();
    
    @Override
    public void start() {
        // TODO save tracks in MongoDB
        
        vertx.eventBus().consumer("tracks", (Message<JsonObject> msg) -> {
            JsonObject b = msg.body();
            String action = b.getString("action");
            if (action == null) {
                msg.fail(400, "No action given");
                return;
            }
            
            switch (action) {
            case "addTrack": {
                JsonObject newTrack = new JsonObject();
                tracks.add(newTrack);
                int trackId = tracks.size() - 1;
                msg.reply(new JsonObject().put("id", trackId));
                log.info("Added track " + trackId);
                break;
            }
            
            case "addPoints": {
                Integer trackId = b.getInteger("trackId");
                if (trackId == null) {
                    msg.fail(400, "No trackId given");
                    break;
                }
                JsonArray points = b.getJsonArray("points");
                if (points == null) {
                    msg.fail(400, "No points given");
                    break;
                }
                JsonObject oldTrack = tracks.get(trackId);
                JsonArray oldPoints = oldTrack.getJsonArray("points");
                if (oldPoints == null) {
                    oldTrack.put("points", points);
                } else {
                    oldPoints.addAll(points);
                }
                msg.reply(new JsonObject().put("count", points.size()));
                log.info("Added " + points.size() + " points to track " + trackId);
                break;
            }
            
            case "deleteTrack": {
                Integer trackId = b.getInteger("trackId");
                if (trackId == null) {
                    // no trackId given. we don't have to do anything
                } else {
                    tracks.remove(trackId.intValue());
                    log.info("Deleted track " + trackId);
                }
                break;
            }
            
            default:
                msg.fail(400, "Unknown action: " + action);
                break;
            }
        });
        log.info("Successfully deployed tracks verticle");
    }
}
