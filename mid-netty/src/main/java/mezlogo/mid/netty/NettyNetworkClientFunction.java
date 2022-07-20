package mezlogo.mid.netty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.logging.LoggingHandler;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.SubscriberToCallback;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

public class NettyNetworkClientFunction implements NettyNetworkClient {
    private final BiFunction<String, Integer, CompletableFuture<Channel>> connectionFactory;
    private final AppFactory factory;

    public NettyNetworkClientFunction(BiFunction<String, Integer, CompletableFuture<Channel>> connectionFactory, AppFactory factory) {
        this.connectionFactory = connectionFactory;
        this.factory = factory;
    }

    @Override
    public CompletableFuture<Flow.Publisher<HttpObject>> openHttpConnection(String host, int port, Flow.Publisher<HttpObject> toTargetPublisher) {
        CompletableFuture<Flow.Publisher<HttpObject>> future = new CompletableFuture<>();

        CompletableFuture<Channel> channelFuture = connectionFactory.apply(host, port);

        channelFuture.thenAccept(channel -> {
            var responsePublisher = factory.initHttpClient(channel);
            toTargetPublisher.subscribe(factory.subscribe(channel));
            future.complete(responsePublisher);
        });
        channelFuture.exceptionally(thr -> {
            future.completeExceptionally(thr);
            return null;
        });

        return future;
    }
}
