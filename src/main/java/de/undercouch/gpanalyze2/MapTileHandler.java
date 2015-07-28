package de.undercouch.gpanalyze2;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.auxiliary.AuxiliaryCache;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class MapTileHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(MapTileHandler.class.getName());
    
    private final HttpClient client;
    private CacheAccess<String, byte[]> cache;
    
    public MapTileHandler(Vertx vertx) {
        client = vertx.createHttpClient();
        cache = JCS.getInstance("default");
        
        // flush the cache to disk from time to time
        vertx.setPeriodic(10000, l -> {
            vertx.executeBlocking(f -> {
                for (AuxiliaryCache<?, ?> a : cache.getCacheControl().getAuxCaches()) {
                    Class<?> cls = a.getClass();
                    Method m;
                    try {
                        m = cls.getDeclaredMethod("saveKeys");
                    } catch (NoSuchMethodException e) {
                        log.warn("Could not save cache keys", e);
                        continue;
                    }
                    m.setAccessible(true);
                    try {
                        m.invoke(a);
                    } catch (ReflectiveOperationException e) {
                        log.warn("Could not save cache keys", e);
                    }
                }
                f.complete();
            }, ar -> {
                if (!ar.succeeded()) {
                    log.error("Could not save cache keys", ar.cause());
                }
            });
        });
    }
    
    @Override
    public void handle(RoutingContext event) {
        String urlStr;
        try {
            urlStr = URLDecoder.decode(event.request().getParam("param0"),
                    StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        
        byte[] cachedBuf = cache.get(urlStr);
        if (cachedBuf != null) {
            event.response().end(Buffer.buffer(cachedBuf));
            return;
        }
        
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            event.response().setStatusCode(400).end("Invalid URL");
            return;
        }
        
        int port = url.getPort() < 0 ? url.getDefaultPort() : url.getPort();
        client.getNow(port, url.getHost(), url.getFile(), clientResponse -> {
            event.response().setStatusCode(clientResponse.statusCode())
                .setStatusMessage(clientResponse.statusMessage());
            event.response().headers().setAll(clientResponse.headers());
            clientResponse.bodyHandler(buf -> {
                cache.put(urlStr, buf.getBytes());
                event.response().end(buf);
            });
        });
    }
}
