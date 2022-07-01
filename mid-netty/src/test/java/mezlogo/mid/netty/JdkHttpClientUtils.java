package mezlogo.mid.netty;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class JdkHttpClientUtils {
    public static JdkTestHttpClient createDefaultTestClient(Integer proxyPort) {
        var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        if (null != proxyPort) {
            client.proxy(ProxySelector.of(new InetSocketAddress(proxyPort)));
        }

        return new JdkTestHttpClient(client.build());
    }

    public static void main(String[] args) {
        var client = createDefaultTestClient(null);
//        System.out.println(client.get("http://localhost:8080/hello").join().body());
        System.out.println(client.get("http://localhost:8080/echo").join().body());
        System.out.println(client.post("http://localhost:8080/echo", "hello", "plain/text").join().body());
    }

    public static class JdkTestHttpClient {
        private final HttpClient httpClient;

        public JdkTestHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public CompletableFuture<HttpResponse<String>> post(String uri, String data, String type) {
            return httpClient.sendAsync(HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(data)).header("Content-Type", type).uri(URI.create(uri)).build(), HttpResponse.BodyHandlers.ofString());
        }

        public CompletableFuture<HttpResponse<String>> get(String uri) {
            return httpClient.sendAsync(HttpRequest.newBuilder().GET().uri(URI.create(uri)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
