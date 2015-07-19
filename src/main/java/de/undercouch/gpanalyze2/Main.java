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
        BridgeOptions bridgeOptions = new BridgeOptions()
                .addInboundPermitted(inboundPermittedTracks);
        sockJSHandler.bridge(bridgeOptions);
        router.route("/eventbus/*").handler(sockJSHandler);
        
        // serve our static resources
        StaticHandler staticHandler = StaticHandler.create();
        router.route("/*").handler(staticHandler);
        
        server.requestHandler(router::accept).listen(8080, ar -> {
            if (ar.succeeded()) {
                vertx.deployVerticle(TracksVerticle.class.getName(), artv -> {
                    if (artv.succeeded()) {
                        log.info("Listening on port 8080");
                        startFuture.complete();
                    } else {
                        log.error("Could not deploy tracks verticle", artv.cause());
                        startFuture.fail(artv.cause());
                    }
                });
            } else {
                log.error("Could not run HTTP server", ar.cause());
                startFuture.fail(ar.cause());
            }
        });
    }
}
