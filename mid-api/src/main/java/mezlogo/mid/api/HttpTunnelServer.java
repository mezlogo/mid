package mezlogo.mid.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class HttpTunnelServer {
    protected List<Integer> ports = new ArrayList<>();

    public HttpTunnelServer bind(int port) {
        ports.add(port);
        return this;
    }

    public abstract CompletableFuture<Void> start();

    public abstract CompletableFuture<Void> stop();
}
