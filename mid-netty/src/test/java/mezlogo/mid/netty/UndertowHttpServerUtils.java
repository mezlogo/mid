package mezlogo.mid.netty;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.MimeMappings;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;

public class UndertowHttpServerUtils {
    public static final Function<HttpServerExchange, SimpleResponse> NOT_FOUND = httpServerExchange -> new SimpleResponse(404, Optional.of("NOT FOUND: " + httpServerExchange.getRequestURI()));

    public static int parseStringOrDefault(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static UndertowTestHttpServer createDefaultTestServer() {
        UndertowTestHttpServer server = new UndertowTestHttpServer();
        server.addHandler(uri -> uri.equals("/status"), exchange -> new SimpleResponse(parseStringOrDefault(Optional.ofNullable(exchange.getQueryParameters().get("code")).map(Deque::getFirst).orElse("404"), 404), Optional.empty()));
        server.addHandler(uri -> uri.equals("/echo"), exchange -> {
            var sb = new StringBuilder();
            HeaderMap headers = exchange.getRequestHeaders();
            boolean isTransferEncoding = headers.contains(Headers.TRANSFER_ENCODING);
            sb.append(exchange.getRequestMethod())
                    .append(",").append(exchange.getProtocol())
                    .append(",").append(exchange.isSecure())
                    .append(",").append(exchange.getRequestContentLength())
                    .append(",").append(headers.get(Headers.CONTENT_TYPE, 0))
                    .append(",").append(headers.contains(Headers.CONTENT_LENGTH) ? "CONTENT_LENGTH" : (isTransferEncoding ? "TRANSFER_ENCODING" : "NO_BODY"));

            if (0 < exchange.getRequestContentLength() || isTransferEncoding) {
                var future = new CompletableFuture<String>();
                exchange.getRequestReceiver().receiveFullString((ctx, msg) -> future.complete(msg));
                String bodyIfExist = future.join();
                sb.append(",").append(bodyIfExist.length());
            }

            return new SimpleResponse(200, Optional.of(sb.toString()));
        });
        return server;
    }

    public static void main(String[] args) {
        createDefaultTestServer().start(8080).join();
    }

    public record SimpleResponse(int code, Optional<String> body) {
    }

    public record PredicateAndFunction(Predicate<String> predicate, Function<HttpServerExchange, SimpleResponse> function) {
    }

    public static class UndertowTestHttpServer {

        private final static ExecutorService executor = Executors.newSingleThreadExecutor();
        private final List<PredicateAndFunction> handlers = new ArrayList<>();
        private Undertow undertow;

        public void addHandler(Predicate<String> predicate, Function<HttpServerExchange, SimpleResponse> function) {
            handlers.add(new PredicateAndFunction(predicate, function));
        }

        public CompletableFuture<Void> start(int port) {
            undertow = Undertow.builder()
                    .addHttpListener(port, "localhost")
                    .setHandler(exchange -> {
                        var uri = exchange.getRequestURI();
                        var handler = handlers.stream()
                                .filter(it -> it.predicate.test(uri))
                                .map(PredicateAndFunction::function)
                                .findAny()
                                .orElse(NOT_FOUND);
                        var result = handler.apply(exchange);

                        exchange.setStatusCode(result.code());

                        if (result.body().isPresent()) {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
                            exchange.getResponseSender().send(result.body().get());
                        }
                    })
                    .build();

            CompletableFuture<Void> future = new CompletableFuture<>();

            executor.execute(() -> {
                undertow.start();
                future.complete(null);
            });

            return future;
        }

        public CompletableFuture<Void> stop() {
            CompletableFuture<Void> future = new CompletableFuture<>();

            executor.execute(() -> {
                undertow.stop();
                future.complete(null);
            });

            return future;
        }
    }
}
