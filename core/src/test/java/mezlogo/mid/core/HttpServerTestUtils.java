package mezlogo.mid.core;

import mezlogo.mid.api.model.BodyType;
import mezlogo.mid.api.model.HttpHandlerToWebsocketAdapter;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.api.model.HttpStatus;
import mezlogo.mid.api.utils.Matchers;
import mezlogo.mid.api.utils.Tuple;
import mezlogo.mid.core.netty.NettyHttpServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.net.http.HttpRequest.BodyPublisher;
import static java.net.http.HttpRequest.BodyPublishers;
import static mezlogo.mid.api.model.HttpResponse.noBody;
import static mezlogo.mid.api.model.WebsocketHandlers.onEchoMsg;
import static mezlogo.mid.api.model.WebsocketHandlers.onOpenSendAndClose;
import static mezlogo.mid.api.utils.HttpHandlers.asyncStreamByEachBody;
import static mezlogo.mid.api.utils.HttpHandlers.syncNoBody;
import static mezlogo.mid.api.utils.HttpHandlers.syncRequestBodyToString;
import static mezlogo.mid.api.utils.HttpHandlers.syncString;

public class HttpServerTestUtils {
    public static final HttpClient client = HttpClient.newBuilder().sslContext(buildInsecureContext()).build();
    static final Random random = new Random();

    public static String generateHugeString(int length) {
        var buf = new StringBuffer();
        var srcs = Arrays.asList(
                "ABCDEFGHIK",
                "LMNOPQRSTU",
                "VWXYZ(){}_",
                "0123456789"
        );
        for (int processed = 0; processed < length; ) {
            int index = random.nextInt(srcs.size());
            String data = srcs.get(index);
            processed += data.length();
            buf.append(data);
        }
        return buf.toString();
    }

    public static NettyHttpServer simpleServer(boolean useSsl) {
        var server = new NettyHttpServer(useSsl ? SslFactory.nettySsl() : null);
        server.addHandler(Matchers.onExactMatch("greet"), syncString(req -> "hello"));
        server.addHandler(Matchers.onExactMatch("nobody"), syncNoBody(req ->
                noBody(HttpStatus.byCode(req.getParam("code").map(Integer::parseInt).orElse(404)))));
        server.addHandler(Matchers.onExactMatch("echostream"), asyncStreamByEachBody(
                req -> String.format("method: %s, type: %s", req.method.name(), req.bodyType().name()),
                body -> "bodylength: " + body.length()));
        server.addHandler(Matchers.onExactMatch("echo"), syncRequestBodyToString(
                (req, body) -> String.format("method: %s, type: %s, bodylength: %d", req.method.name(), req.bodyType().name(), body.length()))
        );
        server.addHandler(Matchers.onExactMatch("/websocket_greet"), new HttpHandlerToWebsocketAdapter(onOpenSendAndClose(List.of("hello"))));
        server.addHandler(Matchers.onExactMatch("/websocket_echo"), new HttpHandlerToWebsocketAdapter(onEchoMsg(msg -> "echo: [" + msg + "]")));
        return server;
    }

    public static CompletableFuture<HttpResponse<String>> sendAsync(boolean useSsl, int port, String url, HttpMethod method, InputStream body) {
        return sendAsync(useSsl, port, url, method, BodyPublishers.ofInputStream(() -> body));
    }

    public static CompletableFuture<HttpResponse<String>> sendAsync(boolean useSsl, int port, String url, BodyType type, HttpMethod method, String body) {
        var bodyPublisher = BodyPublishers.noBody();

        if (body != null) {
            bodyPublisher = BodyType.LENGTH == type
                    ? BodyPublishers.ofString(body)
                    : BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }

        return sendAsync(useSsl, port, url, method, bodyPublisher);
    }

    public static CompletableFuture<HttpResponse<String>> sendAsync(boolean useSsl, int port, String url, HttpMethod method, BodyPublisher bodyPublisher) {
        HttpRequest request = defaultReq(port, url, useSsl)
                .method(method.name(), bodyPublisher)
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpRequest.Builder defaultReq(int port, String url, boolean isSsl) {
        var checkedUrl = url.startsWith("/") ? url : "/" + url;
        var protocol = isSsl ? "https" : "http";
        return HttpRequest.newBuilder()
                .uri(URI.create(protocol + "://localhost:" + port + checkedUrl))
                .timeout(Duration.ofMillis(1000))
                .version(HttpClient.Version.HTTP_1_1);
    }

    public static SSLContext buildInsecureContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new InsecureX509TrustManager()}, new SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        simpleServer(false).start(Arrays.asList(Tuple.of(8080, false)));
    }

    public static Map.Entry<WebSocket, JdkWebsocketListener> callWebsocket(URI uri, List<String> send) {
        var listener = new JdkWebsocketListener();
        var ws = client.newWebSocketBuilder()
                .buildAsync(uri, listener).join();
        send.forEach(it -> ws.sendText(it,true));
        return Tuple.of(ws, listener);
    }

    public static class InsecureX509TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }


    public static class JdkWebsocketListener implements WebSocket.Listener {
        public final CompletableFuture<List<String>> futureClose = new CompletableFuture<>();
        private final List<String> buffer = new ArrayList<>();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.add(data.toString());
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            futureClose.complete(buffer);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}
