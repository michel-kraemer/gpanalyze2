package de.undercouch.gpanalyze2;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

public class TracksVerticle extends AbstractVerticle {
    private static final String ACTION = "action";
    private static final String POINTS = "points";
    private static final String TRACK_ID = "trackId";

    private static final String OBJECT_ID = "_id";
    private static final String TRACKS_COLLECTION = "tracks";

    private static final Logger log = LoggerFactory.getLogger(TracksVerticle.class.getName());
    
    private MongoClient client;
    
    @Override
    public void start() {
        client = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "gpanalyze2"));
        
        vertx.eventBus().consumer(TRACKS_COLLECTION, (Message<JsonObject> msg) -> {
            String action = msg.body().getString(ACTION);
            if (action == null) {
                msg.fail(400, "No action given");
                return;
            }
            
            switch (action) {
            case "addTrack":
                onAddTrack(msg);
                break;
            
            case "addPoints":
                onAddPoints(msg);
                break;
            
            case "deleteTrack":
                onDeleteTrack(msg);
                break;
            
            default:
                msg.fail(400, "Unknown action: " + action);
                break;
            }
        });
        
        log.info("Successfully deployed tracks verticle");
    }

    private void onAddTrack(Message<JsonObject> msg) {
        JsonObject newTrack = new JsonObject();
        client.insert(TRACKS_COLLECTION, newTrack, ar -> {
            if (ar.succeeded()) {
                String id = ar.result();
                msg.reply(new JsonObject().put(TRACK_ID, id));
                log.info("Added track " + id);
            } else {
                log.error("Could not insert track", ar.cause());
                msg.fail(500, "Could not insert track");
            }
        });
    }
    
    private void onAddPoints(Message<JsonObject> msg) {
        String trackId = msg.body().getString(TRACK_ID);
        if (trackId == null) {
            msg.fail(400, "No trackId given");
            return;
        }
        
        JsonArray origPoints = msg.body().getJsonArray(POINTS);
        if (origPoints == null) {
            msg.fail(400, "No points given");
            return;
        }
        
        // check if points are valid
        boolean valid = origPoints.stream().allMatch(p -> {
            if (!(p instanceof JsonObject)) {
                return false;
            }
            JsonObject op = (JsonObject)p;
            Double lat = op.getDouble("lat");
            Double lon = op.getDouble("lon");
            Double ele = op.getDouble("ele");
            String time = op.getString("time");
            return (lat != null && lon != null && ele != null && time != null);
        });
        if (!valid) {
            msg.fail(400, "Invalid track points");
            return;
        }
        
        // remove unnecessary attributes
        Stream<JsonObject> newPoints = origPoints.stream().map(p -> {
            JsonObject op = (JsonObject)p;
            return new JsonObject()
                    .put("lat", op.getDouble("lat"))
                    .put("lon", op.getDouble("lon"))
                    .put("ele",  op.getDouble("ele"))
                    .put("time", op.getString("time"));
        });
        JsonArray points = new JsonArray(newPoints.collect(Collectors.toList()));
        
        // first, check if there is a track with the given ID
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        client.count(TRACKS_COLLECTION, trackQuery, arCount -> {
            if (arCount.succeeded()) {
                long n = arCount.result();
                if (n == 0) {
                    msg.fail(400, "Unknown track: " + trackId);
                    return;
                }
                
                // second, update the track
                JsonObject update = new JsonObject()
                        .put("$push", new JsonObject()
                                .put(POINTS, new JsonObject()
                                        .put("$each", points)));
                client.update(TRACKS_COLLECTION, trackQuery, update, arUpdate -> {
                    if (arUpdate.succeeded()) {
                        msg.reply(new JsonObject().put("count", points.size()));
                        log.info("Added " + points.size() + " points to track " + trackId);
                    } else {
                        log.error("Could not update track: " + trackId, arUpdate.cause());
                        msg.fail(500, "Could not update track: " + trackId);
                    }
                });
            } else {
                log.error("Could not count tracks with id: " + trackId, arCount.cause());
                msg.fail(500, "Could not count tracks with id: " + trackId);
            }
        });
    }
    
    private void onDeleteTrack(Message<JsonObject> msg) {
        Integer trackId = msg.body().getInteger(TRACK_ID);
        if (trackId == null) {
            // no trackId given. we don't have to do anything
        } else {
            JsonObject query = new JsonObject().put(OBJECT_ID, trackId);
            client.remove(TRACKS_COLLECTION, query, ar -> {
                if (ar.succeeded()) {
                    log.info("Deleted track " + trackId);
                } else {
                    log.error("Could not delete track " + trackId, ar.cause());
                    msg.fail(500, "Could not delete track " + trackId);
                }
            });
        }
    }
}
