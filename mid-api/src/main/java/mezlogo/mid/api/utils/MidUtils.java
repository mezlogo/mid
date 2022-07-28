package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.HostAndPort;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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

    public static Predicate<HostAndPort> isDecrypt(List<HostAndPort> socketsToDecrypt) {
        return hostAndPort -> socketsToDecrypt.stream().anyMatch(socket -> socket.equals(hostAndPort));
    }
}
