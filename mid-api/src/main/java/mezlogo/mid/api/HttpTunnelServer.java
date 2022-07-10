package mezlogo.mid.api;

import java.util.concurrent.CompletableFuture;

public abstract class HttpTunnelServer {
    public HttpTunnelServer bind(int port) {
        return this;
    }

    public abstract CompletableFuture<Void> start();
}
