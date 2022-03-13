package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.HttpHandler;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.model.HttpStatus;
import mezlogo.mid.api.model.StringBuffer;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static mezlogo.mid.api.utils.Tuple.of;

public class HttpHandlers {
    public static HttpHandler asyncStreamByEachBody(Function<HttpRequest, String> onRequest, Function<String, String> onBody) {
        return (request, body) -> {
            var future = CompletableFuture.completedFuture(HttpResponse.chunk(HttpStatus.OK, "text/plain; charset=UTF-8"));
            var publisher = new Publishers.QueuedPublisher(Arrays.asList(new StringBuffer(onRequest.apply(request))));
            var subscriber = new Subscribers.ToPublisherSubscriber(buf -> new StringBuffer(onBody.apply(buf.asString())), publisher);
            body.subscribe(subscriber);
            return of(future, publisher);
        };
    }

    public static HttpHandler syncRequestBodyToString(BiFunction<HttpRequest, String, String> endpoint) {
        return (request, body) -> {
            var waitForBody = new Subscribers.AggregateSubscriber();
            body.subscribe(waitForBody);

            var future = new CompletableFuture<HttpResponse>();
            var pub = new Publishers.DeferredPublisher();

            waitForBody.future.thenAccept(bodies -> {
                var bodyAsString = String.join("", bodies);
                var result = endpoint.apply(request, bodyAsString);
                var response = HttpResponse.length(HttpStatus.OK, "text/plain; charset=UTF-8", result.length());
                future.complete(response);
                pub.sendAndComplete(Collections.singletonList(new StringBuffer(result)));
            });

            return of(future, pub);
        };
    }

    public static HttpHandler syncNoBody(Function<HttpRequest, HttpResponse> endpoint) {
        return (request, body) -> {
            var response = endpoint.apply(request);
            var future = CompletableFuture.completedFuture(response);
            var responseBody = Publishers.fromList(Collections.emptyList());
            return of(future, responseBody);
        };
    }

    public static HttpHandler syncString(Function<HttpRequest, String> endpoint) {
        return (request, body) -> {
            var result = endpoint.apply(request);
            var response = HttpResponse.length(HttpStatus.OK, "text/plain; charset=UTF-8", result.length());
            var future = CompletableFuture.completedFuture(response);
            var responseBody = Publishers.fromList(Arrays.asList(result));
            return of(future, responseBody);
        };
    }

    public static HttpHandler syncString(HttpStatus status, Function<HttpRequest, String> endpoint) {
        return (request, body) -> {
            var result = endpoint.apply(request);
            var response = HttpResponse.length(status, "text/plain; charset=UTF-8", result.length());
            var future = CompletableFuture.completedFuture(response);
            var responseBody = Publishers.fromList(Arrays.asList(result));
            return of(future, responseBody);
        };
    }

    public static HttpHandler notFound(String msg) {
        return syncString(req -> msg + ": " + req.url);
    }
}
