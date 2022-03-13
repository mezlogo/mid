package mezlogo.mid.api.model;

import mezlogo.mid.api.utils.HttpHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static mezlogo.mid.api.utils.Tuple.of;

public abstract class DefaultHttpServer implements HttpServer {
    private static final HttpHandler notFound = HttpHandlers.notFound("RESOURCE NOT FOUND");

    protected final List<Map.Entry<Predicate<HttpRequest>, HttpHandler>> handlers = new ArrayList<>();

    @Override
    public void addHandler(Predicate<HttpRequest> predicate, HttpHandler handler) {
        handlers.add(of(predicate, handler));
    }

    public HttpHandler findHandler(HttpRequest request) {
        return handlers.stream().filter(it -> it.getKey().test(request)).map(Map.Entry::getValue).findFirst().orElse(notFound);
    }
}
