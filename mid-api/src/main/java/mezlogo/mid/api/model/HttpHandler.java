package mezlogo.mid.api.model;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface HttpHandler {
    Map.Entry<CompletableFuture<HttpResponse>, BodyPublisher> handle(HttpRequest request, BodyPublisher body);

    default boolean isWebsocket() {
        return false;
    }
}
