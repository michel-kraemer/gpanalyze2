package de.undercouch.gpanalyze2;

import static de.undercouch.gpanalyze2.TrackConstants.END_TIME;
import static de.undercouch.gpanalyze2.TrackConstants.END_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.LOC;
import static de.undercouch.gpanalyze2.TrackConstants.OBJECT_ID;
import static de.undercouch.gpanalyze2.TrackConstants.POINTS;
import static de.undercouch.gpanalyze2.TrackConstants.START_TIME;
import static de.undercouch.gpanalyze2.TrackConstants.START_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.TIME_ZONE_ID;
import static de.undercouch.gpanalyze2.TrackConstants.TIME_ZONE_OFFSET;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import rx.Observable;

public class TrackDao {
    private static final String TRACKS_COLLECTION = "tracks";
    
    private final MongoClient client;
    
    public TrackDao(Vertx vertx) {
        client = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "gpanalyze2"));
    }
    
    /**
     * Creates a new empty track in the database
     * @return an observable emitting the new track's id
     */
    public Observable<String> createEmptyTrack() {
        ObservableFuture<String> f = RxHelper.observableFuture();
        JsonObject newTrack = new JsonObject();
        client.insert(TRACKS_COLLECTION, newTrack, f.toHandler());
        return f;
    }
    
    /**
     * Counts the number of tracks with the given id
     * @param trackId the track id
     * @return an observable emitting the number of tracks
     */
    public Observable<Long> countTracks(String trackId) {
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
    public Observable<Void> updateStartTime(String trackId, long startTime) {
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
    public Observable<Void> updateEndTime(String trackId, long endTime) {
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
    public Observable<Void> updateStartTimeLocal(String trackId, long startTime) {
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
    public Observable<Void> updateEndTimeLocal(String trackId, long endTime) {
        // only update the end time if the given value is greater than the
        // one already stored in the database
        return updateTime(trackId, endTime, "$max", END_TIME_LOCAL);
    }
    
    /**
     * Store a track's time zone information in the database
     * @param trackId the track's id
     * @param offset the time zone information as retrieved through {@link #getTimeZone(JsonObject)}
     * @return an observable that will complete when the operation has finished 
     */
    public Observable<Integer> updateTimeZone(String trackId, Pair<String, Integer> offset) {
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
     * Update a track's points in the database
     * @param trackId the track's id
     * @param points the new track points
     * @return an observable that will complete when the operation has finished 
     */
    public Observable<Void> updatePoints(String trackId, JsonArray points) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put(POINTS, points));
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.update(TRACKS_COLLECTION, trackQuery, update, f.toHandler());
        return f;
    }
    
    /**
     * Add all given points to a track in the database
     * @param trackId the track's id
     * @param points the points to add
     * @return an observable emitting the number of points added
     */
    public Observable<Integer> addPointsToTrack(String trackId, JsonArray points) {
        JsonObject trackQuery = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject update = new JsonObject()
                .put("$push", new JsonObject()
                        .put(POINTS, new JsonObject()
                                .put("$each", points)));
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.update(TRACKS_COLLECTION, trackQuery, update, f.toHandler());
        return f.map(n -> points.size());
    }
    
    /**
     * Delete a track from the database
     * @param trackId the track's id
     * @return an observable that will complete when the operation has finished
     */
    public Observable<Void> deleteTrack(String trackId) {
        JsonObject query = new JsonObject().put(OBJECT_ID, trackId);
        ObservableFuture<Void> f = RxHelper.observableFuture();
        client.remove(TRACKS_COLLECTION, query, f.toHandler());
        return f;
    }
    
    /**
     * Retrieves a track from the database
     * @param trackId the track's id
     * @return an observable emitting the track
     */
    public Observable<JsonObject> getTrack(String trackId) {
        JsonObject query = new JsonObject().put(OBJECT_ID, trackId);
        ObservableFuture<JsonObject> f = RxHelper.observableFuture();
        client.findOne(TRACKS_COLLECTION, query, null, f.toHandler());
        return f;
    }
    
    /**
     * Retrieves a track from the database but does not load its points
     * @param trackId the track's id
     * @return an observable emitting the track
     */
    public Observable<JsonObject> getTrackWithoutPoints(String trackId) {
        JsonObject query = new JsonObject().put(OBJECT_ID, trackId);
        JsonObject fields = new JsonObject().put(POINTS, 0);
        ObservableFuture<JsonObject> f = RxHelper.observableFuture();
        client.findOne(TRACKS_COLLECTION, query, fields, f.toHandler());
        return f;
    }
    
    public Observable<List<JsonObject>> getTracksWithoutPoints(long startTime, boolean localStartTime,
            long endTime, boolean localEndTime, Double minX, Double minY, Double maxX, Double maxY) {
        String startTimeAttr = START_TIME;
        if (localStartTime) {
            startTimeAttr = START_TIME_LOCAL;
        }
        String endTimeAttr = END_TIME;
        if (localEndTime) {
            endTimeAttr = END_TIME_LOCAL;
        }
        
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
        
        if (minX != null && minY != null && maxX != null && maxY != null) {
            query.put(POINTS + "." + LOC, new JsonObject()
                    .put("$geoWithin", new JsonObject()
                            .put("$box", new JsonArray()
                                    .add(new JsonArray().add(minY).add(minX))
                                    .add(new JsonArray().add(maxY).add(maxX)))));
        }
        
        ObservableFuture<List<JsonObject>> f = RxHelper.observableFuture();
        JsonObject fields = new JsonObject().put(POINTS, 0);
        FindOptions findOptions = new FindOptions().setFields(fields);
        client.findWithOptions(TRACKS_COLLECTION, query, findOptions, f.toHandler());
        return f;
    }
}
