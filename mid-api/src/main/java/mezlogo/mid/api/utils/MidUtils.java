package mezlogo.mid.api.utils;

import java.net.URI;
import java.util.Optional;

public class MidUtils {
    public static Optional<URI> uriParser(String uri) {
        return Optional.of(URI.create(uri));
    }
}
