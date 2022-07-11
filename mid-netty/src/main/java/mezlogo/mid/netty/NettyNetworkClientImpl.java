package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.HttpObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class NettyNetworkClientImpl implements NettyNetworkClient {
    private final Bootstrap bootstrap;

    public NettyNetworkClientImpl(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public CompletableFuture<Flow.Publisher<HttpObject>> openHttpConnection(String host, int port, Flow.Publisher<HttpObject> toTargetPublisher) {
        CompletableFuture<Flow.Publisher<HttpObject>> future = new CompletableFuture<>();
        bootstrap.connect(host, port).addListener(NettyUtils.twoCallbacks(channel -> {
        }, future::completeExceptionally));
        return future;
    }
}
