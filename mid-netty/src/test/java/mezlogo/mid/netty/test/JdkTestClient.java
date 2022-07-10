package mezlogo.mid.netty.test;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class JdkTestClient {
    private final HttpClient client;
    private final String host;
    private final int port;
    private final boolean isSecure;

    public JdkTestClient(String host, int port, boolean isSecure, Optional<Integer> proxyPort) {
        this.host = host;
        this.port = port;
        this.isSecure = isSecure;
        var builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);
        proxyPort.ifPresent(it -> builder.proxy(ProxySelector.of(new InetSocketAddress(it))));
        this.client = builder.build();
    }

    public static JdkTestClient createTestClient(String localhost, int port, boolean isSecure, Integer proxyPort) {
        return new JdkTestClient(localhost, port, isSecure, Optional.ofNullable(proxyPort));
    }

    private URI createUri(String uri) {
        var protocol = isSecure ? "https" : "http";
        uri = uri.startsWith("/") ? uri : ("/" + uri);
        var effectiveUri = protocol + "://" + host + ":" + port + uri;
        return URI.create(effectiveUri);
    }

    public CompletableFuture<HttpResponse<String>> get(String uri) {
        var req = HttpRequest.newBuilder()
                .GET()
                .uri(createUri(uri))
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> post(String uri, String payload) {
        var req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .uri(createUri(uri))
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }
}
