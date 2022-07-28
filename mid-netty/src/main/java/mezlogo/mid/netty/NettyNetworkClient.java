package mezlogo.mid.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public interface NettyNetworkClient {
    CompletableFuture<Flow.Publisher<HttpObject>> openHttpConnection(String host, int port, Flow.Publisher<HttpObject> toTargetPublisher);

    CompletableFuture<Flow.Publisher<ByteBuf>> openStreamConnection(String host, int port, Flow.Publisher<ByteBuf> toTargetPublisher);
}
