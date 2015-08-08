package de.undercouch.gpanalyze2;

import static de.undercouch.gpanalyze2.TrackConstants.DIST;
import static de.undercouch.gpanalyze2.TrackConstants.ELE;
import static de.undercouch.gpanalyze2.TrackConstants.END_TIME;
import static de.undercouch.gpanalyze2.TrackConstants.END_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.LOC;
import static de.undercouch.gpanalyze2.TrackConstants.OBJECT_ID;
import static de.undercouch.gpanalyze2.TrackConstants.POINTS;
import static de.undercouch.gpanalyze2.TrackConstants.SPEED;
import static de.undercouch.gpanalyze2.TrackConstants.START_TIME;
import static de.undercouch.gpanalyze2.TrackConstants.START_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.TIME;
import static de.undercouch.gpanalyze2.TrackConstants.TIME_ZONE_OFFSET;
import static de.undercouch.gpanalyze2.TrackConstants.TRACK_ID;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
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
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import rx.Observable;

public class TracksVerticle extends AbstractVerticle {
    private static final String ACTION = "action";
    private static final String MAX_POINTS = "maxPoints";
    
    private static final String BOUNDS = "bounds";
    private static final String RESOLUTION = "resolution";
    
    private static final String LAT = "lat";
    private static final String LON = "lon";
    
    private static final String MINX = "minX";
    private static final String MINY = "minY";
    private static final String MAXX = "maxX";
    private static final String MAXY = "maxY";
    
    private static final String GOOGLE_APIS_KEY;
    static {
        try {
            GOOGLE_APIS_KEY = IOUtils.toString(TracksVerticle.class.getResource("/google_apis_key.txt"));
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Please put a file called 'google_apis_key.txt' in your classpath", e);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TracksVerticle.class.getName());
    
    private TrackDao trackDao;
    private GeodeticCalculator geodeticCalculator = new GeodeticCalculator();
    
    @Override
    public void start() {
        trackDao = new TrackDao(vertx);
        
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
        trackDao.createEmptyTrack().subscribe(id -> {
            msg.reply(new JsonObject().put(TRACK_ID, id));
            log.info("Added track " + id);
        }, err -> {
            log.error("Could not insert track", err);
            msg.fail(500, "Could not insert track");
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
        
        // NOTE: we can't filter for invalid points here and we cannot calculate
        // distance and speed values for each point because we need the whole
        // track for this. we are going to update the track later when it is
        // retrieved the first time (see #updateTrack(JsonObject))
        
        // check if there is a track with the given id
        trackDao.countTracks(trackId)
            .map(n -> {
                // if there is no track throw an exception
                if (n == 0) {
                    throw new NoSuchElementException("Unknown track: " + trackId);
                }
                return n;
            })
            .flatMap(n -> trackDao.updateStartTime(trackId, minTimestamp))
            .flatMap(n -> trackDao.updateEndTime(trackId, maxTimestamp))
            .flatMap(n -> updateTimeZone(trackId, newPointsList.get(0)))
            .flatMap(timeZoneOffset -> trackDao.updateStartTimeLocal(trackId, minTimestamp + timeZoneOffset).map(v -> timeZoneOffset))
            .flatMap(timeZoneOffset -> trackDao.updateEndTimeLocal(trackId, maxTimestamp + timeZoneOffset))
            .flatMap(n -> trackDao.addPointsToTrack(trackId, points))
            .subscribe(n -> {
                msg.reply(new JsonObject().put("count", n));
                log.info("Added " + n + " points to track " + trackId);
            }, err -> {
                log.error("Could not add points to track: " + trackId, err);
                msg.fail(500, "Could not add points to track: " + trackId);
            });
    }
    
    /**
     * Update a track's time zone
     * @param trackId the track's id
     * @param point one of the track's points
     * @return an observable that will complete when the operation has finished
     */
    private Observable<Integer> updateTimeZone(String trackId, JsonObject point) {
        // check if the track already has time zone information
        return trackDao.getTrackWithoutPoints(trackId).flatMap(obj -> {
            // if the track has no time zone information yet retrieve it
            // and then store it
            Integer offset = obj.getInteger(TIME_ZONE_OFFSET);
            if (offset == null) {
                return getTimeZone(point).flatMap(newOffset ->
                        trackDao.updateTimeZone(trackId, newOffset));
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
    
    private void onDeleteTrack(Message<JsonObject> msg) {
        String trackId = msg.body().getString(TRACK_ID);
        if (trackId == null) {
            // no trackId given. we don't have to do anything
        } else {
            trackDao.deleteTrack(trackId).subscribe(v -> {
                log.info("Deleted track " + trackId);
            }, err -> {
                log.error("Could not delete track " + trackId, err);
                msg.fail(500, "Could not delete track " + trackId);
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
        
        Double minX = null;
        Double minY = null;
        Double maxX = null;
        Double maxY = null;
        if (bounds != null && !bounds.isEmpty()) {
            minX = bounds.getDouble(MINX);
            minY = bounds.getDouble(MINY);
            maxX = bounds.getDouble(MAXX);
            maxY = bounds.getDouble(MAXY);
        }
        
        // find all tracks in the given search window
        trackDao.getTracksWithoutPoints(
                startTimeLocal != null ? startTimeLocal : startTime,
                startTimeLocal != null,
                endTimeLocal != null ? endTimeLocal : endTime,
                endTimeLocal != null,
                minX, minY, maxX, maxY).subscribe(tracks -> {
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
        }, err -> {
            log.error("Could not fetch all tracks", err);
            msg.fail(500, "Could not fetch all tracks");
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
        
        // filter out non-strictly increasing timestamps
        JsonArray increasingResult = new JsonArray();
        long lastTime = result.getJsonObject(0).getLong(TIME);
        for (int i = 1; i < result.size(); ++i) {
            JsonObject p = result.getJsonObject(i);
            long time = p.getLong(TIME);
            if (time > lastTime) {
                increasingResult.add(p);
            }
            lastTime = time;
        }
        result = increasingResult;
        
        return result;
    }
    
    private void onGetTrack(Message<JsonObject> msg) {
        String trackId = msg.body().getString(TRACK_ID);
        if (trackId == null) {
            msg.fail(400, "No track id given");
            return;
        }
        
        Float resolution = msg.body().getFloat(RESOLUTION);
        
        trackDao.getTrack(trackId).flatMap(this::updateTrack)
            .subscribe(track -> {
                JsonArray points = track.getJsonArray(POINTS);
                
                // resample points
                if (resolution != null) {
                    points = resamplePoints(points, resolution);
                    track.put(POINTS, points);
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
                track.put(TRACK_ID, track.getString(OBJECT_ID));
                track.remove(OBJECT_ID);
                
                if (resolution != null) {
                    track.put(RESOLUTION, resolution);
                }
                
                msg.reply(track);
            }, err -> {
                log.error("Could not fetch points of track: " + trackId, err);
                msg.fail(500, "Could not fetch points of track: " + trackId);
            });
    }
    
    private double calculateDistanceAndSpeed(JsonArray points) {
        double totalDistance = 0.0;
        double lastlat = 0.0;
        double lastlon = 0.0;
        double lastele = 0.0;
        long mintime = Long.MAX_VALUE;
        long maxtime = Long.MIN_VALUE;
        long lasttime = 0;
        double lastspeed = 0.0;
        double[] xvalues = new double[points.size()];
        double[] yvalues = new double[points.size()];
        for (int i = 0; i < points.size(); ++i) {
            JsonObject p = points.getJsonObject(i);
            JsonArray loc = p.getJsonArray(LOC);
            double lat = loc.getDouble(1);
            double lon = loc.getDouble(0);
            double ele = p.getDouble(ELE);
            long time = p.getLong(TIME);
            if (time < mintime) {
                mintime = time;
            }
            if (time > maxtime) {
                maxtime = time;
            }
            
            double speed = 0.0;
            if (i > 0) {
                double dist = distance(lastlat, lastlon, lastele, lat, lon, ele); // in meters
                totalDistance += dist;
                
                double timedist = (time - lasttime) / 1000; // in seconds
                speed = dist / timedist * 3.6; // in km/h
                if (Double.isInfinite(speed) || Double.isNaN(speed)) {
                    speed = lastspeed;
                }
            }
            
            xvalues[i] = time;
            yvalues[i] = speed;
            p.put(DIST, totalDistance);
            
            lastlat = lat;
            lastlon = lon;
            lastele = ele;
            lasttime = time;
            lastspeed = speed;
        }
        
        // calculate the average speed over 20 seconds
        LinearInterpolator lp = new LinearInterpolator();
        PolynomialSplineFunction spline = lp.interpolate(xvalues, yvalues);
        for (int i = 0; i < points.size(); ++i) {
            JsonObject p = points.getJsonObject(i);
            long time = p.getLong(TIME);
            final int N = 20;
            double[] speeds = new double[N];
            for (int j = 0; j < N; ++j) {
                speeds[j] = spline.value(Math.min(Math.max(time - (j - N / 2) * 1000, mintime), maxtime));
            }
            double speed = DoubleStream.of(speeds).sum() / N;
            p.put(SPEED, speed);
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
    
    private Observable<JsonObject> updateTrack(JsonObject track) {
        // check if track needs to be updated
        JsonArray points = track.getJsonArray(POINTS);
        if (points.getJsonObject(0).getDouble(SPEED) != null) {
            // no update necessary
            return Observable.just(track);
        } else {
            // calculate distance and speed
            ObservableFuture<JsonObject> of = RxHelper.observableFuture();
            vertx.executeBlocking((Future<JsonObject> f) -> {
                log.info("Updating track: calculating values for distance and speed ...");
                JsonArray filteredPoints = filterInvalidPoints(points);
                calculateDistanceAndSpeed(filteredPoints);
                track.put(POINTS, filteredPoints);
                f.complete(track);
            }, of.toHandler());
            
            // save track back to database
            return of.flatMap(updatedTrack -> {
                log.info("Updating track in database ...");
                return trackDao.updatePoints(updatedTrack.getString(OBJECT_ID),
                        updatedTrack.getJsonArray(POINTS)).map(v -> updatedTrack);
            });
        }
    }
}
