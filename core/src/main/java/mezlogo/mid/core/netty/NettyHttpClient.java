package mezlogo.mid.core.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;
import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.BodyType;
import mezlogo.mid.api.model.HttpClient;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.utils.Publishers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static mezlogo.mid.api.utils.Tuple.of;

public class NettyHttpClient implements HttpClient {
    private static final Supplier<List<Map.Entry<String, ChannelHandler>>> websocketClientHandlers = () -> Arrays.asList(
            of("http-client-codec", new HttpClientCodec()),
            of("http-aggregator", new HttpObjectAggregator(1024 * 64))
    );
    private static final Supplier<List<Map.Entry<String, ChannelHandler>>> httpClientHandlers = () -> Arrays.asList(
            of("http-client-codec", new HttpClientCodec()),
            of("http-chunk-writer", new ChunkedWriteHandler())
    );
    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    public NettyHttpClient() {
        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
    }

    private static void initChannel(List<Map.Entry<String, ChannelHandler>> handlers, ChannelPipeline pipeline) {
        handlers.stream().filter(Objects::nonNull).forEach(pair -> pipeline.addLast(pair.getKey(), pair.getValue()));
    }

    private static ChannelInitializer<SocketChannel> empty() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {

            }
        };
    }

    @Override
    public Map.Entry<CompletableFuture<HttpResponse>, BodyPublisher> request(String host, int port, HttpRequest request, BodyPublisher body, boolean isSsl) {
        ChannelFuture connect = bootstrap.connect(host, port);

        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        BodyPublisher publisher = new Publishers.SimplePublisher();

        connect.addListener((ChannelFutureListener) future -> {
            Channel channel = future.channel();
            var pipeline = channel.pipeline();

            if (isSsl) {
                pipeline.addFirst("ssl", SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build()
                        .newHandler(channel.alloc(), host, port)
                );
            }

            if (request.isWebsocket()) {
                initChannel(websocketClientHandlers.get(), pipeline);

                URI uri = URI.create("ws://" + host + ":" + port + request.url);
                var headers = EmptyHttpHeaders.INSTANCE;
                var handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri,
                        WebSocketVersion.V13, null, false, headers, 1024 * 128);

                var handler = new NettyWebsocketClientChannelHandlerToMid(handshaker, responseFuture, publisher);
                pipeline.addLast("websocket-handler-adapter", handler);

                var pub = new NettyWebsocketWriteBodySubscriber(channel::writeAndFlush);
                responseFuture.thenAccept(it -> body.subscribe(pub));
            } else {
                initChannel(httpClientHandlers.get(), pipeline);

                pipeline.addLast("http-handler-adapter", new NettyHttpResponseInboundChannelHandlerToMid(responseFuture, publisher));

                var nettyRequest = Utils.convert(request);
                channel.writeAndFlush(nettyRequest);
                var pub = new NettyWriteHttpBodySubscriber(channel::writeAndFlush);
                pub.isChunk = BodyType.CHUNK == request.bodyType();
                body.subscribe(pub);
            }
        });

        return of(responseFuture, publisher);
    }

    @Override
    public CompletableFuture<Void> start() {
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(empty())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        group.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS);
        return CompletableFuture.completedFuture(null);
    }
}
