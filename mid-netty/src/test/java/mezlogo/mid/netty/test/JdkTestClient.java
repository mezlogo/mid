package mezlogo.mid.netty.test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
        if (isSecure) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            builder.sslContext(buildInsecureContext());
        }
        proxyPort.ifPresent(it -> builder.proxy(ProxySelector.of(new InetSocketAddress(it))));
        this.client = builder.build();
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

    public static JdkTestClient createTestClient(String localhost, int port, boolean isSecure, Integer proxyPort) {
        return new JdkTestClient(localhost, port, isSecure, Optional.ofNullable(proxyPort));
    }

    public static void main(String[] args) {
        var b = createTestClient("localhost", 8443, true, null).get("/echo").join().body();
        System.out.println(b);
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
                .header("content-type", "text/plain")
                .uri(createUri(uri))
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
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
}
