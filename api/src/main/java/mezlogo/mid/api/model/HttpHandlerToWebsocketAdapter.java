package mezlogo.mid.api.model;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static mezlogo.mid.api.utils.Tuple.of;

public class HttpHandlerToWebsocketAdapter implements HttpHandler {
    private final WebsocketHandler websocketHandler;

    public HttpHandlerToWebsocketAdapter(WebsocketHandler websocketHandler) {
        this.websocketHandler = websocketHandler;
    }

    @Override
    public Map.Entry<CompletableFuture<HttpResponse>, BodyPublisher> handle(HttpRequest request, BodyPublisher body) {
        body.subscribe(websocketHandler);
        return of(CompletableFuture.completedFuture(null), websocketHandler);
    }

    @Override
    public boolean isWebsocket() {
        return true;
    }
}
