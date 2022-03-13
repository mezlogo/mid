package mezlogo.mid.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static mezlogo.mid.api.utils.Tuple.of;

public class HttpResponse {
    public final HttpStatus status;
    public final BodyType type;
    public final List<Map.Entry<String, String>> headers = new ArrayList<>();

    public HttpResponse(HttpStatus status, BodyType type) {
        this.status = status;
        this.type = type;
    }

    public static HttpResponse noBody(HttpStatus status) {
        HttpResponse response = new HttpResponse(status, BodyType.NO_BODY);
        response.addHeader("Content-Length", "0");
        return response;
    }

    public static HttpResponse length(HttpStatus status, String type, int length) {
        HttpResponse result = new HttpResponse(status, BodyType.LENGTH);
        result.addHeader("Content-Length", "" + length);
        result.addHeader("Content-Type", type);
        return result;
    }

    public static HttpResponse chunk(HttpStatus status, String type) {
        HttpResponse result = new HttpResponse(status, BodyType.CHUNK);
        result.addHeader("Transfer-Encoding", "chunked");
        result.addHeader("Content-Type", type);
        return result;
    }

    public void addHeader(String key, String value) {
        headers.add(of(key, value));
    }
}
