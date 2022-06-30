package mezlogo.mid.api.model;

import mezlogo.mid.api.utils.Publishers;
import mezlogo.mid.api.utils.Subscribers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static mezlogo.mid.api.utils.Tuple.of;

public interface HttpClient {
    Map.Entry<CompletableFuture<HttpResponse>, BodyPublisher> request(String host, int port, HttpRequest req, BodyPublisher body, boolean isSsl);

    default CompletableFuture<BodyPublisher> openWebsocket(String host, int port, String url, BodyPublisher body, boolean isSsl) {
        var result = new CompletableFuture<BodyPublisher>();

        Map.Entry<CompletableFuture<HttpResponse>, BodyPublisher> response = request(host, port, HttpRequest.openWebsocketAt(url), body, isSsl);
        response.getKey().thenAccept(r -> result.complete(response.getValue()));

        return result;
    }

    CompletableFuture<Void> start();

    CompletableFuture<Void> stop();

    default Map.Entry<CompletableFuture<HttpResponse>, CompletableFuture<String>> request(String host, int port, HttpRequest req, String body, boolean isSsl) {
        BodyPublisher bodyPublisher = BodyType.NO_BODY == req.bodyType() ? Publishers.noBody() : Publishers.fromList(List.of(body));
        var result = this.request(host, port, req, bodyPublisher, isSsl);
        var aggregateBody = new Subscribers.AggregateSubscriber();
        result.getValue().subscribe(aggregateBody);
        return of(
                result.getKey(),
                aggregateBody.future.thenApply(it -> String.join("", it)));
    }
}
