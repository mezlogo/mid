package mezlogo.mid.api.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public interface HttpServer {
    void addHandler(Predicate<HttpRequest> predicate, HttpHandler handler);

    CompletableFuture<Void> start(List<? extends Map.Entry<Integer, Boolean>> ports);

    CompletableFuture<Void> stop(int timeout);
}
