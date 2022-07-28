package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.HostAndPort;

import java.net.URI;
import java.util.Optional;

public class MidUtils {
    public static Optional<URI> uriParser(String uri) {
        return Optional.of(URI.create(uri));
    }
    public static Optional<HostAndPort> socketParser(String uri) {
        var splited = uri.split(":");
        if (2 != splited.length) {
            return Optional.empty();
        }
        var effectivePort = splited[1];
        try {
            int port = Integer.parseInt(effectivePort);
            return Optional.of(new HostAndPort(splited[0], port));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
