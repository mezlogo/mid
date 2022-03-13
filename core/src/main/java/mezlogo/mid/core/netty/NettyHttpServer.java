package mezlogo.mid.core.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import mezlogo.mid.api.model.DefaultHttpServer;
import mezlogo.mid.api.model.HttpHandler;
import mezlogo.mid.api.utils.CommonUtils;
import mezlogo.mid.api.utils.Publishers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NettyHttpServer extends DefaultHttpServer {
    private final ServerBootstrap bootstrap;
    private final EventLoopGroup group;
    private final SslContext sslContext;
    private List<ChannelFuture> channelFutures = Collections.emptyList();

    public NettyHttpServer(SslContext sslContext) {
        this.bootstrap = new ServerBootstrap();
        this.group = new NioEventLoopGroup();
        this.sslContext = sslContext;
    }

    public static ChannelInitializer<SocketChannel> toInit(Consumer<SocketChannel> handler) {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                handler.accept(ch);
            }
        };
    }

    @Override
    public CompletableFuture<Void> start(List<? extends Map.Entry<Integer, Boolean>> ports) {
        bootstrap.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        toInit(socketChannel -> {
                            var port = socketChannel.parent().localAddress().getPort();
                            var pipeline = socketChannel.pipeline();
                            if (ports.stream().filter(it -> it.getKey() == port).map(Map.Entry::getValue).findAny().orElse(false)) {
                                pipeline.addFirst("ssl", sslContext.newHandler(socketChannel.alloc()));
                            }
                            pipeline.addLast("http-server-codec", new HttpServerCodec());
                            pipeline.addLast("http-decider", new ChannelDecideHandler());
                        }))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        channelFutures = ports.stream().map(Map.Entry::getKey)
                .map(bootstrap::bind).collect(Collectors.toList());

        List<CompletableFuture<Void>> futures = channelFutures.stream().map(it -> {
            var result = new CompletableFuture<Void>();
            it.addListener(future -> {
                if (future.isSuccess()) {
                    result.complete(null);
                } else {
                    result.completeExceptionally(new RuntimeException("something goes wrong: " + future));
                }
            });
            return result;
        }).collect(Collectors.toList());

        return CommonUtils.allOf(futures);
    }

    @Override
    public CompletableFuture<Void> stop(int timeout) {
        channelFutures.forEach(it -> it.channel().closeFuture());
        var stopFuture = group.shutdownGracefully(0l, timeout, TimeUnit.MILLISECONDS);
        var result = new CompletableFuture<Void>();
        stopFuture.addListener(future -> result.complete(null));
        return result;
    }

    public class ChannelDecideHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof HttpRequest)) {
                ctx.close();
                throw new UnsupportedOperationException("Expected http request: " + msg);
            }
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove(this);
            var nettyReq = (HttpRequest) msg;
            var request = Utils.convert(nettyReq);
            HttpHandler handler = findHandler(request);

            if (handler.isWebsocket()) {
                var headers = nettyReq.headers();
                if (!"Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION)) ||
                        !"WebSocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE))) {
                    throw new UnsupportedOperationException("Expected WebSocket connection: " + msg);
                }
                var handshakerFactory = new WebSocketServerHandshakerFactory("", null, false);
                var handshaker = handshakerFactory.newHandshaker(nettyReq);
                if (handshaker == null) {
                    throw new UnsupportedOperationException("Expected supported WebSocket request: " + msg);
                }
                handshaker.handshake(ctx.channel(), nettyReq);
                var requestBodyPublisher = new Publishers.SimplePublisher();
                var result = handler.handle(request, requestBodyPublisher);
                pipeline.addLast("websocket-server-adapter", new NettyWebsocketRequestInboundChannelHandlerToMid(requestBodyPublisher));
                var responseWriter = new NettyWebsocketWriteBodySubscriber(ctx::writeAndFlush);
                result.getValue().subscribe(responseWriter);
            } else {
                pipeline.addLast("http-chunk-writer", new ChunkedWriteHandler());
                var mid = new NettyHttpRequestInboundChannelHandlerToMid((newRequest, body) ->
                        findHandler(newRequest).handle(newRequest, body)
                );
                pipeline.addLast("http-server-adapter", mid);
                mid.channelRead0(ctx, nettyReq);
            }
        }
    }
}
