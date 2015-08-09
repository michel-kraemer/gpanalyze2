package de.undercouch.gpanalyze2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;

public class Main extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(Main.class.getName());
    
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(Main.class.getName());
    }
    
    @Override
    public void start(Future<Void> startFuture) {
        HttpServer server = vertx.createHttpServer();
        
        Router router = Router.router(vertx);
        
        // serve webjars resources
        StaticHandler assetHandler = StaticHandler.create();
        assetHandler.setWebRoot("META-INF/resources/webjars");
        router.route("/assets/*").handler(assetHandler);
        
        // initialize SockJS
        SockJSHandlerOptions sockJSOptions = new SockJSHandlerOptions()
                .setHeartbeatInterval(2000);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, sockJSOptions);
        PermittedOptions inboundPermittedTracks = new PermittedOptions()
                .setAddress("tracks");
        PermittedOptions inboundPermittedStatistics = new PermittedOptions()
                .setAddress("statistics");
        BridgeOptions bridgeOptions = new BridgeOptions()
                .addInboundPermitted(inboundPermittedTracks)
                .addInboundPermitted(inboundPermittedStatistics);
        sockJSHandler.bridge(bridgeOptions);
        router.route("/eventbus/*").handler(sockJSHandler);
        
        // serve cached map tiles
        MapTileHandler mapTileHandler = new MapTileHandler(vertx);
        router.routeWithRegex("/map/(.+)$").handler(mapTileHandler);
        
        // serve our static resources
        StaticHandler staticHandler = StaticHandler.create();
        router.route("/*").handler(staticHandler);
        
        ObservableFuture<String> tvf = RxHelper.observableFuture();
        vertx.deployVerticle(TracksVerticle.class.getName(), tvf.toHandler());
        tvf.flatMap(tvid -> {
            ObservableFuture<String> svf = RxHelper.observableFuture();
            vertx.deployVerticle(StatisticsVerticle.class.getName(), svf.toHandler());
            return svf;
        }).flatMap(svid -> {
            ObservableFuture<HttpServer> hsf = RxHelper.observableFuture();
            server.requestHandler(router::accept).listen(8080, hsf.toHandler());
            return hsf;
        }).subscribe(hsr -> {
            log.info("Listening on port 8080");
            startFuture.complete();
        }, err -> {
            log.error("Could not start application", err);
            startFuture.fail(err);
        });
    }
}
