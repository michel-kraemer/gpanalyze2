package de.undercouch.gpanalyze2;

import static de.undercouch.gpanalyze2.TrackConstants.DIST;
import static de.undercouch.gpanalyze2.TrackConstants.ELE;
import static de.undercouch.gpanalyze2.TrackConstants.END_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.POINTS;
import static de.undercouch.gpanalyze2.TrackConstants.START_TIME_LOCAL;
import static de.undercouch.gpanalyze2.TrackConstants.TIME;
import static de.undercouch.gpanalyze2.TrackConstants.TIME_ZONE_OFFSET;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StatisticsVerticle extends AbstractVerticle {
    private static final String ACTION = "action";
    
    private static final String TRACK_IDS = "trackIds";
    private static final String DISTANCE = "distance";
    private static final String ELEVATION_GAIN = "elevationGain";
    private static final String ELEVATION_LOSS = "elevationLoss";
    private static final String ELEVATION_MAX = "elevationMax";
    private static final String ELEVATION_MIN = "elevationMin";
    
    private static final Logger log = LoggerFactory.getLogger(StatisticsVerticle.class.getName());
    
    private TrackDao trackDao;
    
    @Override
    public void start() {
        trackDao = new TrackDao(vertx);
        
        vertx.eventBus().consumer("statistics", (Message<JsonObject> msg) -> {
            String action = msg.body().getString(ACTION);
            if (action == null) {
                msg.fail(400, "No action given");
                return;
            }
            
            switch (action) {
            case "calculate":
                onCalculate(msg);
                break;
            }
        });
    }
    
    private void onCalculate(Message<JsonObject> msg) {
        Long startTimeLocal = msg.body().getLong(START_TIME_LOCAL);
        if (startTimeLocal == null) {
            msg.fail(400, "Missing local start time");
            return;
        }
        
        Long endTimeLocal = msg.body().getLong(END_TIME_LOCAL);
        if (endTimeLocal == null) {
            msg.fail(400, "Missing local end time");
            return;
        }
        
        JsonArray trackIdsArr = msg.body().getJsonArray(TRACK_IDS);
        if (trackIdsArr == null) {
            msg.fail(400, "Missing array of track IDs");
            return;
        }
        
        List<String> trackIds = trackIdsArr.stream()
                .map(id -> id.toString())
                .collect(Collectors.toList());
        
        log.info("Loading " + trackIds.size() + " tracks ...");
        
        trackDao.getTracks(trackIds).subscribe(tracks -> {
            JsonObject result = calculateStatistics(tracks, startTimeLocal, endTimeLocal);
            msg.reply(result);
        }, err -> {
            log.error("Could not fetch tracks from database", err);
            msg.fail(500, "Could not fetch tracks from database");
        });
    }
    
    private JsonObject calculateStatistics(List<JsonObject> tracks,
            long startTimeLocal, long endTimeLocal) {
        log.info("Calculating statistics for " + tracks.size() + " tracks ...");
        
        long time = 0;
        double distance = 0;
        double elevation_gain = 0;
        double elevation_loss = 0;
        Double elevation_max = null;
        Double elevation_min = null;
        for (JsonObject track : tracks) {
            JsonObject ts = calculateStatistics(track, startTimeLocal, endTimeLocal);
            
            time += ts.getLong(TIME);
            distance += ts.getDouble(DISTANCE);
            elevation_gain += ts.getDouble(ELEVATION_GAIN);
            elevation_loss += ts.getDouble(ELEVATION_LOSS);
            
            Double emx = ts.getDouble(ELEVATION_MAX);
            if (emx != null && (elevation_max == null || elevation_max < emx)) {
                elevation_max = emx;
            }
            Double emn = ts.getDouble(ELEVATION_MIN);
            if (emn != null && (elevation_min == null || elevation_min > emn)) {
                elevation_min = emn;
            }
        }
        
        JsonObject result = new JsonObject()
                .put(TIME, time)
                .put(DISTANCE, distance)
                .put(ELEVATION_GAIN, elevation_gain)
                .put(ELEVATION_LOSS, elevation_loss);
        if (elevation_max != null) {
            result.put(ELEVATION_MAX, elevation_max);
        }
        if (elevation_min != null) {
            result.put(ELEVATION_MIN, elevation_min);
        }
        return result;
    }
    
    private JsonObject calculateStatistics(JsonObject track,
            long startTimeLocal, long endTimeLocal) {
        long total_time = 0;
        double distance = 0;
        double elevation_gain = 0.0;
        double elevation_loss = 0.0;
        Double elevation_max = null;
        Double elevation_min = null;
        int timeZoneOffset = track.getInteger(TIME_ZONE_OFFSET);
        
        @SuppressWarnings("unchecked")
        List<JsonObject> points = track.getJsonArray(POINTS).getList();
        
        // find first point to analyze
        int first = Collections.binarySearch(points,
                new JsonObject().put(TIME, startTimeLocal - timeZoneOffset),
                (a, b) -> a.getLong(TIME).compareTo(b.getLong(TIME)));
        if (first < 0) {
            first = -first - 1;
        }
        int last = first;
        
        // do analysis
        JsonObject prevp = null;
        List<Double> xvalues = new ArrayList<Double>();
        List<Double> yvalues = new ArrayList<Double>();
        for (int i = first; i < points.size(); ++i) {
            JsonObject p = points.get(i);
            
            long time = p.getLong(TIME);
            if (time + timeZoneOffset > endTimeLocal) {
                break;
            }
            
            double ele = p.getDouble(ELE);
            if (elevation_max == null || elevation_max < ele) {
                elevation_max = ele;
            }
            if (elevation_min == null || elevation_min > ele) {
                elevation_min = ele;
            }
            
            if (prevp != null) {
                double prevele = prevp.getDouble(ELE);
                double gain = ele - prevele;
                xvalues.add(Double.valueOf(time));
                yvalues.add(gain);
            }
            
            last = i;
            prevp = p;
        }
        
        if (last != first && first < points.size()) {
            // calculate moving average for elevation values (window size = 120s)
            Averager averager = new Averager(120, 1000,
                    xvalues.stream().mapToDouble(d -> d).toArray(),
                    yvalues.stream().mapToDouble(d -> d).toArray());
            for (Double x : xvalues) {
                double gain = averager.apply(x);
                if (gain > 0) {
                    elevation_gain += gain;
                } else {
                    elevation_loss -= gain;
                }
            }
            
            // calculate total time and distance
            total_time = (points.get(last).getLong(TIME) - points.get(first).getLong(TIME));
            distance = (points.get(last).getDouble(DIST) - points.get(first).getDouble(DIST)) / 1000;
        }
        
        JsonObject result = new JsonObject()
                .put(TIME, total_time)
                .put(DISTANCE, distance)
                .put(ELEVATION_GAIN, elevation_gain)
                .put(ELEVATION_LOSS, elevation_loss);
        if (elevation_max != null) {
            result.put(ELEVATION_MAX, elevation_max);
        }
        if (elevation_min != null) {
            result.put(ELEVATION_MIN, elevation_min);
        }
        return result;
    }
}
