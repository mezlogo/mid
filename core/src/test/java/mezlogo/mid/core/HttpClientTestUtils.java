package mezlogo.mid.core;

import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import mezlogo.mid.api.model.BodySubscriber;
import mezlogo.mid.api.model.DefaultHttpServer;
import mezlogo.mid.api.model.HttpBuffer;
import mezlogo.mid.api.model.HttpHandler;
import mezlogo.mid.api.model.HttpHandlerToWebsocketAdapter;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.model.StringBuffer;
import mezlogo.mid.api.utils.HttpHandlers;
import mezlogo.mid.api.utils.Matchers;
import mezlogo.mid.api.utils.Publishers;

import javax.net.ssl.SSLContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static io.undertow.Handlers.websocket;
import static mezlogo.mid.api.model.WebsocketHandlers.onEchoMsg;
import static mezlogo.mid.api.model.WebsocketHandlers.onOpenSendAndClose;
import static mezlogo.mid.api.utils.HttpHandlers.syncRequestBodyToString;
import static mezlogo.mid.api.utils.Tuple.of;

public class HttpClientTestUtils {
    public static List<Map.Entry<Predicate<HttpRequest>, HttpHandler>> testHandlers() {
        return Arrays.asList(
                of(Matchers.onExactMatch("/greet"), HttpHandlers.syncString(req -> "hello")),
                of(Matchers.onExactMatch("/websocket_greet"), new HttpHandlerToWebsocketAdapter(onOpenSendAndClose(List.of("hello")))),
                of(Matchers.onExactMatch("/websocket_echo"), new HttpHandlerToWebsocketAdapter(onEchoMsg(msg -> "echo: [" + msg + "]"))),
                of(Matchers.onExactMatch("/echo"), syncRequestBodyToString(
                        (req, body) -> req.method.name() + "," + req.bodyType().name() + "," + body.length()))
        );
    }

    public static void main(String[] args) {
        /*
        const socketGreet = new WebSocket('ws://localhost:8080/websocket_greet');
        socketGreet.addEventListener('open', event => console.log('open'));
        socketGreet.addEventListener('message', event => console.log('Input: ' + event.data));
        socketGreet.addEventListener('close', () => console.log('closed'));

        const socketEcho = new WebSocket('ws://localhost:8080/websocket_echo');
        socketEcho.addEventListener('open', event => socketEcho.send('Hello Server!'));
        socketEcho.addEventListener('message', event => { console.log('Input: ' + event.data); socketEcho.close(); });
        socketEcho.addEventListener('close', () => console.log('closed'));
         */
        simpleServer(true).start(Arrays.asList(of(8080, false), of(8081, true)));
    }

    public static UndertowHttpServer simpleServer(boolean useSsl) {
        var server = new UndertowHttpServer(useSsl ? SslFactory.buildSSLContext() : null);
        testHandlers().forEach(it -> server.addHandler(it.getKey(), it.getValue()));
        return server;
    }

    public static HttpRequest convert(HttpServerExchange exchange) {
        var method = HttpMethod.valueOf(exchange.getRequestMethod().toString());
        var url = exchange.getRequestURI();
        var req = new HttpRequest(url, method);
        var requestHeaders = exchange.getRequestHeaders();
        requestHeaders.getHeaderNames().forEach(key -> {
            var value = String.join(",", requestHeaders.get(key));
            req.addHeader(key.toString(), value);
        });
        exchange.getQueryParameters().forEach((key, vals) -> req.addQuery(key, String.join(",", vals)));
        return req;
    }

    public static void fillResponse(HttpResponse response, HttpServerExchange exchange) {
        exchange.setStatusCode(response.status.code);
        response.headers.forEach(it ->
                exchange.getResponseHeaders().add(Headers.fromCache(it.getKey()), it.getValue()));
    }

    public static class UndertowBodySubscriber implements BodySubscriber {
        private final Sender sender;

        public UndertowBodySubscriber(Sender sender) {
            this.sender = sender;
        }

        @Override
        public void onNext(HttpBuffer item) {
            sender.send(item.asString());
        }

        @Override
        public void onComplete() {
            sender.close(IoCallback.END_EXCHANGE);
        }
    }

    public static class UndertowHttpServer extends DefaultHttpServer {
        private final SSLContext ssl;
        private Undertow server;

        public UndertowHttpServer(SSLContext ssl) {
            this.ssl = ssl;
        }

        @Override
        public CompletableFuture<Void> start(List<? extends Map.Entry<Integer, Boolean>> ports) {
            Undertow.Builder builder = Undertow.builder();

            SSLContext ssl = null;
            if (ports.stream().map(Map.Entry::getValue).filter(it -> it).findAny().orElse(false)) {
                ssl = SslFactory.buildSSLContext();
            }
            var effectiveSsl = ssl;

            ports.forEach(it -> {
                if (it.getValue()) {
                    builder.addHttpsListener(it.getKey(), "localhost", effectiveSsl);
                } else {
                    builder.addHttpListener(it.getKey(), "localhost");
                }
            });

            server = builder.setHandler(exchange -> {
                var midReq = convert(exchange);
                var publisher = new Publishers.SimplePublisher();
                HttpHandler midHandler = findHandler(midReq);
                if (midHandler.isWebsocket()) {
                    var ws = websocket((_exch, channel) -> {
                        channel.getReceiveSetter().set(new AbstractReceiveListener() {
                            @Override
                            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                publisher.publish(new StringBuffer(message.getData()));
                            }

                            @Override
                            protected void onCloseMessage(CloseMessage cm, WebSocketChannel channel) {
                                publisher.complete();
                            }
                        });
                        channel.resumeReceives();
                        var result = midHandler.handle(midReq, publisher);

                        result.getValue().subscribe(new BodySubscriber() {
                            @Override
                            public void onNext(HttpBuffer item) {
                                WebSockets.sendText(item.asString(), channel, null);
                            }

                            @Override
                            public void onComplete() {
                                WebSockets.sendClose(CloseMessage.GOING_AWAY, null, channel, null);
                            }
                        });
                    });
                    ws.handleRequest(exchange);
                } else {
                    var result = midHandler.handle(midReq, publisher);
                    exchange.getRequestReceiver().receivePartialString((ex, msg, last) -> {
                        if (!msg.isBlank()) {
                            publisher.publish(new StringBuffer(msg));
                        }
                        if (last) {
                            publisher.complete();
                        }
                    });
                    result.getKey().thenAccept(resp -> fillResponse(resp, exchange));
                    result.getValue().subscribe(new UndertowBodySubscriber(exchange.getResponseSender()));
                }
            }).build();
            server.start();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stop(int timeout) {
            server.stop();
            return CompletableFuture.completedFuture(null);
        }
    }
}
