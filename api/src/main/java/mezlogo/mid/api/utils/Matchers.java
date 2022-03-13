package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.HttpRequest;

import java.util.function.Predicate;

public class Matchers {
    public static Predicate<HttpRequest> onExactMatch(String endpoint) {
        String cleanEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return req -> req.url.equals(cleanEndpoint);
    }
}
