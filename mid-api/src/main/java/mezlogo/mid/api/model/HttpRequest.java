package mezlogo.mid.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static mezlogo.mid.api.utils.Tuple.of;

public class HttpRequest {
    public final boolean isWebsocket;
    public final String url;
    public final HttpMethod method;
    public final List<Map.Entry<String, String>> headers = new ArrayList<>();
    public final List<Map.Entry<String, String>> queries = new ArrayList<>();

    public HttpRequest(String url, HttpMethod method, boolean isWebsocket) {
        this.url = url;
        this.method = method;
        this.isWebsocket = isWebsocket;
    }

    public HttpRequest(String url, HttpMethod method) {
        this(url, method, false);
    }

    public static HttpRequest buildFrom(String url, HttpMethod method, String host, String contentType, int length, BodyType type) {
        url = url.startsWith("/") ? url : "/" + url;
        HttpRequest request = new HttpRequest(url, method);
        request.addHeader("Accept", "*/*");
        request.addHeader("Host", host);

        if (BodyType.NO_BODY != type) {
            request.addHeader("Content-Type", contentType);
        }

        if (BodyType.LENGTH == type) {
            request.addHeader("Content-Length", "" + length);
        } else if (BodyType.CHUNK == type) {
            request.addHeader("Transfer-Encoding", "chunked");
        }

        return request;
    }

    public static HttpRequest postChunk(String uri, String type) {
        return buildFrom(uri, HttpMethod.POST, "localhost", type, -1, BodyType.CHUNK);
    }

    public static HttpRequest postLength(String uri, String type, int length) {
        return buildFrom(uri, HttpMethod.POST, "localhost", type, length, BodyType.LENGTH);
    }

    public static HttpRequest get(String uri) {
        return new HttpRequest(uri, HttpMethod.GET);
    }

    public static HttpRequest openWebsocketAt(String url) {
        return new HttpRequest(url, HttpMethod.GET, true);
    }

    public BodyType bodyType() {
        var length = headers.stream().filter(it -> it.getKey().equalsIgnoreCase("Content-Length")).findAny();
        var chunk = headers.stream().filter(it -> it.getKey().equalsIgnoreCase("Transfer-Encoding")).findAny();
        return length.isPresent() ? BodyType.LENGTH : (chunk.isPresent() ? BodyType.CHUNK : BodyType.NO_BODY);
    }

    public boolean isWebsocket() {
        return isWebsocket;
    }

    public void addHeader(String key, String vale) {
        headers.add(of(key, vale));
    }

    public void addQuery(String key, String value) {
        queries.add(of(key, value));
    }

    public Optional<String> getParam(String key) {
        return queries.stream().filter(it -> it.getKey().equals(key)).map(Map.Entry::getValue).findFirst();
    }
}
