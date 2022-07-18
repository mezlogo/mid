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
    private final AppConfig config;

    public NettyNetworkClientFunction(BiFunction<String, Integer, CompletableFuture<Channel>> connectionFactory, AppConfig config) {
        this.connectionFactory = connectionFactory;
        this.config = config;
    }

    @Override
    public CompletableFuture<Flow.Publisher<HttpObject>> openHttpConnection(String host, int port, Flow.Publisher<HttpObject> toTargetPublisher) {
        CompletableFuture<Flow.Publisher<HttpObject>> future = new CompletableFuture<>();

        CompletableFuture<Channel> channelFuture = connectionFactory.apply(host, port);

        channelFuture.thenAccept(channel -> {
            var responsePublisher = new BufferedPublisher<HttpObject>();

            if (config.verboseClient()) {
                channel.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
            }
            channel.pipeline().addLast("http-client-codec", new HttpClientCodec())
                    .addLast("adapter", new HttpProxyHandlerToPublisher(responsePublisher));

            toTargetPublisher.subscribe(new SubscriberToCallback<>(channel::writeAndFlush, channel::close));
            future.complete(responsePublisher);
        });
        channelFuture.exceptionally(thr -> {
            future.completeExceptionally(thr);
            return null;
        });

        return future;
    }
}
