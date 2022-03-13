package mezlogo.mid.core.netty;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.model.HttpStatus;

import java.util.stream.Collectors;

public class Utils {
    public static HttpResponse convert(io.netty.handler.codec.http.HttpResponse response) {
        HttpStatus status = HttpStatus.byCode(response.status().code());
        HttpResponse result = HttpResponse.noBody(status);
        String contentLength = response.headers().get("Content-Length");
        String contentType = response.headers().get("Content-Type");
        if (null != contentLength) {
            result = HttpResponse.length(status, contentType, Integer.parseInt(contentLength));
        } else if (response.headers().contains("Transfer-Encoding")) {
            result = HttpResponse.chunk(status, contentType);
        }
        return result;
    }

    public static io.netty.handler.codec.http.HttpRequest convert(HttpRequest request) {
        var query = request.queries.stream().map(it -> it.getKey() + "=" + it.getValue()).collect(Collectors.joining(","));
        String uri = query.isBlank() ? request.url : (request.url + "?" + query);
        var nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, convert(request.method), uri);
        request.headers.forEach(it -> nettyRequest.headers().add(it.getKey(), it.getValue()));
        return nettyRequest;
    }

    public static HttpRequest convert(io.netty.handler.codec.http.HttpRequest request) {
        var decoder = new QueryStringDecoder(request.uri());
        var uri = decoder.path();
        var method = HttpMethod.valueOf(request.method().name());
        var result = new HttpRequest(uri, method);
        request.headers().forEach(it -> result.addHeader(it.getKey(), it.getValue()));
        decoder.parameters().forEach((key, values) -> result.addQuery(key, String.join(",", values)));
        return result;
    }

    public static io.netty.handler.codec.http.HttpMethod convert(HttpMethod method) {
        return io.netty.handler.codec.http.HttpMethod.valueOf(method.name());
    }

    public static HttpMethod convert(io.netty.handler.codec.http.HttpMethod method) {
        return HttpMethod.valueOf(method.name());
    }

    public static HttpStatus convert(HttpResponseStatus status) {
        return HttpStatus.byCode(status.code());
    }

    public static HttpResponseStatus convert(HttpStatus status) {
        return HttpResponseStatus.valueOf(status.code);
    }

    public static io.netty.handler.codec.http.HttpResponse convert(HttpResponse response) {
        var headers = new DefaultHttpHeaders();
        response.headers.forEach(it -> headers.add(it.getKey(), it.getValue()));
        return new DefaultHttpResponse(HttpVersion.HTTP_1_1, convert(response.status), headers);
    }
}
