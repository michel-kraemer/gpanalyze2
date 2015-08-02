package de.undercouch.gpanalyze2;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.referencing.GeodeticCalculator;

import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.TimeZoneApi;
import com.google.maps.model.LatLng;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import rx.Observable;

public class TracksVerticle extends AbstractVerticle {
    private static final String ACTION = "action";
    private static final String POINTS = "points";
    private static final String TRACK_ID = "trackId";
    private static final String MAX_POINTS = "maxPoints";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";
    private static final String START_TIME_LOCAL = "startTimeLocal";
    private static final String END_TIME_LOCAL = "endTimeLocal";
    private static final String TIME_ZONE_ID = "timeZoneId";
    private static final String TIME_ZONE_OFFSET = "timeZoneOffset";
    private static final String BOUNDS = "bounds";
    private static final String RESOLUTION = "resolution";
    
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String ELE = "ele";
    private static final String TIME = "time";
    private static final String DIST = "dist";
    private static final String SPEED = "speed";

    private static final String LOC = "loc";
    
    private static final String MINX = "minX";
    private static final String MINY = "minY";
    private static final String MAXX = "maxX";
    private static final String MAXY = "maxY";
    
    private static final String OBJECT_ID = "_id";
    private static final String TRACKS_COLLECTION = "tracks";
    
    private static final String GOOGLE_APIS_KEY;
    static {
        try {
            GOOGLE_APIS_KEY = IOUtils.toString(TracksVerticle.class.getResource("/google_apis_key.txt"));
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Please put a file called 'google_apis_key.txt' in your classpath", e);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TracksVerticle.class.getName());
    
    private MongoClient client;
    private GeodeticCalculator geodeticCalculator = new GeodeticCalculator();
    
    @Override
    public void start() {
        client = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "gpanalyze2"));
        
        vertx.eventBus().consumer("tracks", (Message<JsonObject> msg) -> {
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
            
            case "findTracks":
                onFindTracks(msg);
                break;
            
            case "getTrack":
                onGetTrack(msg);
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
            Double lat = op.getDouble(LAT);
            Double lon = op.getDouble(LON);
            String time = op.getString(TIME);
            return (lat != null && lon != null && time != null);
        });
        if (!valid) {
            msg.fail(400, "Invalid track points");
            return;
        }
        
        // remove unnecessary attributes and convert lat/lon to GeoJSON
        Stream<JsonObject> newPoints = origPoints.stream().map(p -> {
            JsonObject op = (JsonObject)p;
            double lat = op.getDouble(LAT);
            double lon = op.getDouble(LON);
            JsonArray loc = new JsonArray().add(lon).add(lat);
            return new JsonObject()
                    .put(LOC, loc)
                    .put(ELE, op.getDouble(ELE)) // ele may be null!
                    .put(TIME, op.getString(TIME));
        });
        
        List<JsonObject> newPointsList = newPoints.collect(Collectors.toList());
        
        // parse time string
        newPointsList.forEach(p -> p.put(TIME, Instant.parse(p.getString(TIME)).getEpochSecond() * 1000));
        
        // find oldest and newest point
        Comparator<JsonObject> timeComparator = (a, b) ->
            Long.compare(a.getLong(TIME), b.getLong(TIME));
        long minTimestamp = newPointsList.stream().min(timeComparator).get().getLong(TIME);
        long maxTimestamp = newPointsList.stream().max(timeComparator).get().getLong(TIME);
        
        JsonArray points = new JsonArray(newPointsList);
        
        // check if there is a track with the given id
        countTracks(trackId)
            .map(n -> {
                // if there is no track throw an exception
                if (n == 0) {
                    throw new NoSuchElementException("Unknown track: " + trackId);
                }
                return n;
            })
            .flatMap(n -> updateStartTime(trackId, minTimestamp))
            .flatMap(n -> updateEndTime(trackId, maxTimestamp))
            .flatMap(n -> updateTimeZone(trackId, newPointsList.get(0)))
            .flatMap(timeZoneOffset -> updateStartTimeLocal(trackId, minTimestamp + timeZoneOffset).map(v -> timeZoneOffset))
            .flatMap(timeZoneOffset -> updateEndTimeLocal(trackId, maxTimestamp + timeZoneOffset))
            .flatMap(n -> addPointsToTrack(trackId, points))
            .subscribe(n -> {
                msg.reply(new JsonObject().put("count", n));
                log.info("Added " + n + " points to track " + trackId);
            }, err -> {
                log.error("Could not add points to track: " + trackId, err);
                msg.fail(500, "Could not add points to track: " + trackId);
            });
    }
    
    /**
     * Counts the number of tracks with the given id
     * @param trackId the track id
     * @return an observable emitting the number of tracks
     */
    private Observable<Long> countTracks(String trackId) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        ObservableFuture<Long> f = RxHelper.observableFuture();
        client.count(TRACKS_COLLECTION, trackQuery, f.toHandler());
        return f;
    }
    
    private Observable<Void> updateTime(String trackId, long time, String op, String attr) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject update = new JsonObject()
                .put(op, new JsonObject()
                        .put(attr, time));
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.update(TRACKS_COLLECTION, trackQuery, update, f.toHandler());
        return f;
    }
    
    /**
     * Update a track's start time
     * @param trackId the track's id
     * @param startTime the new start time
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Void> updateStartTime(String trackId, long startTime) {
        // only update the start time if the given value is less than the
        // one already stored in the database
        return updateTime(trackId, startTime, "$min", START_TIME);
    }
    
    /**
     * Update a track's end time
     * @param trackId the track's id
     * @param endTime the new end time
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Void> updateEndTime(String trackId, long endTime) {
        // only update the end time if the given value is greater than the
        // one already stored in the database
        return updateTime(trackId, endTime, "$max", END_TIME);
    }
    
    /**
     * Update a track's local start time
     * @param trackId the track's id
     * @param startTime the new start time
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Void> updateStartTimeLocal(String trackId, long startTime) {
        // only update the start time if the given value is less than the
        // one already stored in the database
        return updateTime(trackId, startTime, "$min", START_TIME_LOCAL);
    }
    
    /**
     * Update a track's local end time
     * @param trackId the track's id
     * @param endTime the new end time
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Void> updateEndTimeLocal(String trackId, long endTime) {
        // only update the end time if the given value is greater than the
        // one already stored in the database
        return updateTime(trackId, endTime, "$max", END_TIME_LOCAL);
    }
    
    /**
     * Update a track's time zone
     * @param trackId the track's id
     * @param point one of the track's points
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Integer> updateTimeZone(String trackId, JsonObject point) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        ObservableFuture<JsonObject> f = RxHelper.observableFuture();
        // check if the track already has time zone information
        client.findOne(TRACKS_COLLECTION, trackQuery,
                new JsonObject().put(TIME_ZONE_OFFSET, 1), f.toHandler());
        return f.flatMap(obj -> {
                // if the track has no time zone information yet retrieve it
                // and then store it
                Integer offset = obj.getInteger(TIME_ZONE_OFFSET);
                if (offset == null) {
                    return getTimeZone(point)
                            .flatMap(newOffset -> updateTimeZone(trackId, newOffset));
                }
                // the track already has time zone information. do nothing.
                return Observable.just(offset);
            });
    }
    
    /**
     * Get time zone information for a given point
     * @param point the point
     * @return an observable emitting a pair with the time zone identifier and
     * a time zone offset to be added to UTC time to get the local time for
     * the given point
     */
    private Observable<Pair<String, Integer>> getTimeZone(JsonObject point) {
        JsonArray loc = point.getJsonArray(LOC);
        LatLng location = new LatLng(loc.getDouble(1), loc.getDouble(0));
        GeoApiContext context = new GeoApiContext().setApiKey(GOOGLE_APIS_KEY);
        PendingResult<TimeZone> req = TimeZoneApi.getTimeZone(context, location);
        ObservableFuture<Pair<String, Integer>> f = RxHelper.observableFuture();
        Handler<AsyncResult<Pair<String, Integer>>> h = f.toHandler();
        req.setCallback(new PendingResult.Callback<TimeZone>() {
            @Override
            public void onResult(TimeZone result) {
                String id = result.getID();
                int offset = result.getOffset(point.getLong(TIME));
                h.handle(Future.succeededFuture(Pair.of(id, offset)));
            }

            @Override
            public void onFailure(Throwable e) {
                h.handle(Future.failedFuture(e));
            }
        });
        return f;
    }
    
    /**
     * Store a track's time zone information in the database
     * @param trackId the track's id
     * @param offset the time zone information as retrieved through {@link #getTimeZone(JsonObject)}
     * @return an observable that will complete when the operation has finished 
     */
    private Observable<Integer> updateTimeZone(String trackId, Pair<String, Integer> offset) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put(TIME_ZONE_ID, offset.getLeft())
                        .put(TIME_ZONE_OFFSET, offset.getRight()));
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.update(TRACKS_COLLECTION, trackQuery, update, f.toHandler());
        return f.map(v -> offset.getRight());
    }
    
    /**
     * Add all given points to a track in the database
     * @param trackId the track's id
     * @param points the points to add
     * @return an observable emitting the number of points added
     */
    private Observable<Integer> addPointsToTrack(String trackId, JsonArray points) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject update = new JsonObject()
                .put("$push", new JsonObject()
                        .put(POINTS, new JsonObject()
                                .put("$each", points)));
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.update(TRACKS_COLLECTION, trackQuery, update, f.toHandler());
        return f.map(n -> {
            return points.size();
        });
    }
    
    private void onDeleteTrack(Message<JsonObject> msg) {
        String trackId = msg.body().getString(TRACK_ID);
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
    
    private void onFindTracks(Message<JsonObject> msg) {
        Integer maxPoints = msg.body().getInteger(MAX_POINTS, 60 * 60 * 24);
        Long startTime = msg.body().getLong(START_TIME, 0L);
        Long endTime = msg.body().getLong(END_TIME, System.currentTimeMillis());
        Long startTimeLocal = msg.body().getLong(START_TIME_LOCAL);
        Long endTimeLocal = msg.body().getLong(END_TIME_LOCAL);
        JsonObject bounds = msg.body().getJsonObject(BOUNDS);
        
        String startTimeAttr = START_TIME;
        if (startTimeLocal != null) {
            startTimeAttr = START_TIME_LOCAL;
            startTime = startTimeLocal;
        }
        String endTimeAttr = END_TIME;
        if (endTimeLocal != null) {
            endTimeAttr = END_TIME_LOCAL;
            endTime = endTimeLocal;
        }
        
        // find all tracks in the given search window
        JsonObject query = new JsonObject()
                .put("$or", new JsonArray()
                        .add(new JsonObject()
                            .put(startTimeAttr, new JsonObject()
                                    .put("$gte", startTime)
                                    .put("$lte", endTime)))
                        .add(new JsonObject()
                            .put(endTimeAttr, new JsonObject()
                                    .put("$gte", startTime)
                                    .put("$lte", endTime)))
                        .add(new JsonObject()
                            .put(startTimeAttr, new JsonObject()
                                    .put("$lte", startTime))
                            .put(endTimeAttr, new JsonObject()
                                    .put("$gte", endTime))));
        
        if (bounds != null && !bounds.isEmpty()) {
            double minX = bounds.getDouble(MINX);
            double minY = bounds.getDouble(MINY);
            double maxX = bounds.getDouble(MAXX);
            double maxY = bounds.getDouble(MAXY);
            query
                    .put(POINTS + "." + LOC, new JsonObject()
                            .put("$geoWithin", new JsonObject()
                                    .put("$box", new JsonArray()
                                        .add(new JsonArray().add(minY).add(minX))
                                        .add(new JsonArray().add(maxY).add(maxX)))));
        }
        
        // retrieve everything except 'points'
        JsonObject fields = new JsonObject().put(POINTS, 0);
        FindOptions findOptions = new FindOptions().setFields(fields);
        client.findWithOptions(TRACKS_COLLECTION, query, findOptions, ar -> {
            if (ar.succeeded()) {
                List<JsonObject> tracks = ar.result();
                if (tracks.size() == 0) {
                    msg.reply(new JsonArray());
                    return;
                }
                
                // calculate duration of all tracks and resolution
                long duration = tracks.stream().mapToLong(a -> a.getLong(END_TIME) - a.getLong(START_TIME)).sum();
                float resolution = (float)duration / Math.max(maxPoints - tracks.size() * 2, 1);
                
                // rename _id -> trackId and add resolution
                tracks.forEach(t -> {
                    t.put(TRACK_ID, t.getString(OBJECT_ID));
                    t.remove(OBJECT_ID);
                    t.put(RESOLUTION, resolution);
                });
                
                msg.reply(new JsonArray(tracks));
            } else {
                log.error("Could not fetch all tracks", ar.cause());
                msg.fail(500, "Could not fetch all tracks");
            }
        });
    }
    
    private JsonArray resamplePoints(JsonArray points, float resolution) {
        JsonArray filteredPoints = new JsonArray();
        int i = 0;
        while (i < points.size()) {
            JsonObject p = points.getJsonObject(i);
            filteredPoints.add(p);
            long time = p.getLong(TIME);
            ++i;
            while (i < points.size() && points.getJsonObject(i).getLong(TIME) - time < resolution) ++i;
        }
        if (!filteredPoints.getJsonObject(filteredPoints.size() - 1).equals(points.getJsonObject(points.size() - 1))) {
            filteredPoints.add(points.getJsonObject(points.size() - 1));
        }
        return filteredPoints;
    }
    
    private JsonArray filterInvalidPoints(JsonArray points) {
        // filter out points without elevation
        JsonArray result = new JsonArray(points.stream().filter(p ->
            ((JsonObject)p).getFloat(ELE) != null).collect(Collectors.toList()));
        if (result.size() != points.size()) {
            log.warn("Removed " + (points.size() - result.size()) + " invalid points.");
        }
        return result;
    }
    
    private void onGetTrack(Message<JsonObject> msg) {
        String trackId = msg.body().getString(TRACK_ID);
        if (trackId == null) {
            msg.fail(400, "No track id given");
            return;
        }
        
        Float resolution = msg.body().getFloat(RESOLUTION);
        
        JsonObject query = new JsonObject().put(OBJECT_ID, trackId);
        client.findOne(TRACKS_COLLECTION, query, null, ar -> {
            if (ar.succeeded()) {
                JsonArray points = ar.result().getJsonArray(POINTS);
                
                points = filterInvalidPoints(points);
                
                // TODO calculate distance and speed only once and save in database
                calculateDistanceAndSpeed(points);
                
                // resample points
                if (resolution != null) {
                    points = resamplePoints(points, resolution);
                    ar.result().put(POINTS, points);
                }
                
                // convert coordinates
                points.forEach(obj -> {
                    JsonObject p = (JsonObject)obj;
                    JsonArray loc = p.getJsonArray(LOC);
                    p.put(LAT, loc.getDouble(1));
                    p.put(LON, loc.getDouble(0));
                    p.remove(LOC);
                });
                
                // rename _id -> trackId and add resolution
                ar.result().put(TRACK_ID, ar.result().getString(OBJECT_ID));
                ar.result().remove(OBJECT_ID);
                
                if (resolution != null) {
                    ar.result().put(RESOLUTION, resolution);
                }
                
                msg.reply(ar.result());
            } else {
                log.error("Could not fetch points of track: " + trackId, ar.cause());
                msg.fail(500, "Could not fetch points of track: " + trackId);
            }
        });
    }
    
    private double calculateDistanceAndSpeed(JsonArray points) {
        double totalDistance = 0.0;
        double lastlat = 0.0;
        double lastlon = 0.0;
        double lastele = 0.0;
        long lasttime = 0;
        for (int i = 0; i < points.size(); ++i) {
            JsonObject p = points.getJsonObject(i);
            JsonArray loc = p.getJsonArray(LOC);
            double lat = loc.getDouble(1);
            double lon = loc.getDouble(0);
            double ele = p.getDouble(ELE);
            long time = p.getLong(TIME);
            
            double speed = 0.0;
            if (i > 0) {
                double dist = distance(lastlat, lastlon, lastele, lat, lon, ele); // in meters
                totalDistance += dist;
                
                double timedist = (time - lasttime) / 1000; // in seconds
                speed = dist / timedist * 3.6; // in km/h
            }
            
            p.put(SPEED, speed);
            p.put(DIST, totalDistance);
            
            lastlat = lat;
            lastlon = lon;
            lastele = ele;
            lasttime = time;
        }
        return totalDistance;
    }
    
    private double distance(double lat1, double lon1, double ele1,
            double lat2, double lon2, double ele2) {
        geodeticCalculator.setStartingGeographicPoint(lon1, lat1);
        geodeticCalculator.setDestinationGeographicPoint(lon2, lat2);
        double dist = geodeticCalculator.getOrthodromicDistance();
        double eledx = Math.abs(ele1 - ele2);
        dist = Math.sqrt(dist * dist + eledx * eledx);
        return dist;
    }
}
