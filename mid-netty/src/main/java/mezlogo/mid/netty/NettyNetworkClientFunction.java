package mezlogo.mid.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpObject;

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
            toTargetPublisher.subscribe(factory.subscribeHttpObject(channel));
            future.complete(responsePublisher);
        });
        channelFuture.exceptionally(thr -> {
            future.completeExceptionally(thr);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<Flow.Publisher<ByteBuf>> openStreamConnection(String host, int port, Flow.Publisher<ByteBuf> toTargetPublisher) {
        CompletableFuture<Flow.Publisher<ByteBuf>> future = new CompletableFuture<>();

        CompletableFuture<Channel> channelFuture = connectionFactory.apply(host, port);

        channelFuture.thenAccept(channel -> {
            var responsePublisher = factory.initBytesClient(channel, false);
            toTargetPublisher.subscribe(factory.subscribeBytes(channel));
            future.complete(responsePublisher);
        });
        channelFuture.exceptionally(thr -> {
            future.completeExceptionally(thr);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<Flow.Publisher<ByteBuf>> openDecryptedStreamConnection(String host, int port, Flow.Publisher<ByteBuf> toTargetPublisher) {
        CompletableFuture<Flow.Publisher<ByteBuf>> future = new CompletableFuture<>();

        CompletableFuture<Channel> channelFuture = connectionFactory.apply(host, port);

        channelFuture.thenAccept(channel -> {
            var responsePublisher = factory.initBytesClient(channel, true);
            toTargetPublisher.subscribe(factory.subscribeBytes(channel));
            future.complete(responsePublisher);
        });
        channelFuture.exceptionally(thr -> {
            future.completeExceptionally(thr);
            return null;
        });

        return future;    }
}
