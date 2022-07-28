package mezlogo.mid.netty.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.AppConfig;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyNetworkClientFunction;
import mezlogo.mid.netty.handler.HttpTunnelHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class EmbeddedAppFactory extends AppFactory {
    private final boolean throwOnBootstrap;
    public Supplier<NettyNetworkClient> clientSupplier;
    private ScheduledExecutorService executor;

    public EmbeddedAppFactory(AppConfig config) {
        this(config, true);
    }

    public EmbeddedAppFactory(AppConfig config, boolean throwOnBootstrap) {
        super(config);
        this.throwOnBootstrap = throwOnBootstrap;
    }

    public ScheduledExecutorService getExecutor() {
        if (null == executor) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        return executor;
    }

    public NettyNetworkClient createTestNetworkClient(BiFunction<String, Integer, CompletableFuture<Channel>> connector) {
        return new NettyNetworkClientFunction(connector, this);
    }

    @Override
    public Bootstrap getClientBootstrap() {
        if (throwOnBootstrap) {
            throw new IllegalArgumentException("you should not call this method");
        }
        return super.getClientBootstrap();
    }

    @Override
    public ServerBootstrap getServerBootstrap() {
        if (throwOnBootstrap) {
            throw new IllegalArgumentException("you should not call this method");
        }
        return super.getServerBootstrap();
    }

    @Override
    public HttpTunnelHandler createHttpTunnelHandler() {
        return new HttpTunnelHandler(MidUtils::uriParser, MidUtils::socketParser, clientSupplier.get(), this);
    }

    @Override
    public NioEventLoopGroup getGroup() {
        if (throwOnBootstrap) {
            throw new IllegalArgumentException("you should not call this method");
        }
        return super.getGroup();
    }

    public EmbeddedTestClient createTestClient(InitPipeline initTestServer) {
        // 4. channel test-server: [adapter(mid-client) | logger | http-server | http-aggregator | to-function(req -> resp)]
        // 3. channel mid-client : [adapter(test-server)| logger | http-client | publisher(response-publisher)]
        // 2. channel mid-server : [adapter(test-client)| logger | http-server | http-tunnel(factory)]
        // 1. channel test-client: [adapter(mid-server) | logger | http-client | http-aggregator | to-future(resp)]
        Supplier<Channel> createTestServerForMidClient = () -> {
            var testServer = new NamedEmbeddedChannel("mid-client", "test-server", (ChannelHandler) null);
            initTestServer.init(testServer.pipeline());

            var midClient = new NamedEmbeddedChannel("mid-client", "test-server", new OutboundBytebufHandlerAdapter(testServer));
            testServer.pipeline().addFirst(new OutboundBytebufHandlerAdapter(midClient));
            return midClient;
        };

        var executor = getExecutor();

        this.clientSupplier = () -> createTestNetworkClient((host, port) -> {
            CompletableFuture<Channel> future = new CompletableFuture<>();
            executor.schedule(() -> future.complete(createTestServerForMidClient.get()), 5, TimeUnit.MILLISECONDS);
            return future;
        });

        var midServer = new NamedEmbeddedChannel("test-client", "mid-server", (ChannelHandler) null);
        initTunnelChannel(midServer);

        var testClient = new NamedEmbeddedChannel("test-client", "mid-server",
                new OutboundBytebufHandlerAdapter(midServer),
                new LoggingHandler("mezlogo.mid.netty.test.client"),
                new HttpClientCodec(),
                new HttpObjectAggregator(1024)
        );
        midServer.pipeline().addFirst(new OutboundBytebufHandlerAdapter(testClient));

        return new EmbeddedTestClient(testClient);
    }

    public InitPipeline initTestTcpServerChannel(Function<String, String> handler) {
        return pipeline ->
                pipeline.addLast(new LoggingHandler("mezlogo.mid.netty.test.server"))
                        .addLast(new CombinedChannelDuplexHandler<>(new StringDecoder(), new StringEncoder()))
                        .addLast(new StringToFunctionHandler(handler));
    }

    public InitPipeline initTestHttpServerChannel(Function<FullHttpRequest, FullHttpResponse> handler) {
        return pipeline ->
                pipeline.addLast(new LoggingHandler("mezlogo.mid.netty.test.server"))
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(1024))
                        .addLast(new FullHttpRequestToFunctionHandler(handler));
    }

    @FunctionalInterface
    interface InitPipeline {
        void init(ChannelPipeline pipeline);
    }

    public static class EmbeddedTestClient {
        private final EmbeddedChannel channel;
        private State state = State.HTTP_CLIENT;

        public EmbeddedTestClient(EmbeddedChannel testClient) {
            this.channel = testClient;
        }

        public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
            if (State.HTTP_CLIENT != this.state) {
                throw new IllegalStateException("expected to be in http mode");
            }
            this.state = State.HTTP_WAIT_FOR_RESPONSE;
            CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
            ChannelPipeline pipeline = channel.pipeline();
            if (null != pipeline.get(FullHttpResponseToCallback.class)) {
                throw new IllegalArgumentException("expected FullHttpResponseToCallback to be removed");
            }
            pipeline.addLast(new FullHttpResponseToCallback(future::complete));
            future.thenAccept(it -> {
                this.state = State.HTTP_CLIENT;
                channel.pipeline().remove(FullHttpResponseToCallback.class);
            });
            channel.writeOutbound(request);
            return future;
        }

        public void turnToTcp() {
            if (State.HTTP_CLIENT != this.state) {
                throw new IllegalStateException("expected to be in http mode");
            }
            this.state = State.TCP_CLIENT;

            ChannelPipeline pipeline = channel.pipeline();
            pipeline.remove(HttpClientCodec.class);
            pipeline.remove(HttpObjectAggregator.class);
            pipeline.addLast(new CombinedChannelDuplexHandler<>(new StringDecoder(), new StringEncoder()));
        }

        public CompletableFuture<String> sendString(String message) {
            if (State.TCP_CLIENT != this.state) {
                throw new IllegalStateException("expected to be in http mode");
            }
            this.state = State.TCP_WAIT_FOR_RESPONSE;

            CompletableFuture<String> future = new CompletableFuture<>();
            ChannelPipeline pipeline = channel.pipeline();
            if (null != pipeline.get(StringToCallbackHandler.class)) {
                throw new IllegalArgumentException("expected StringToCallbackHandler to be removed");
            }
            pipeline.addLast(new StringToCallbackHandler(future::complete));
            future.thenAccept(it -> {
                this.state = State.TCP_CLIENT;
                channel.pipeline().remove(StringToCallbackHandler.class);
            });
            channel.writeOutbound(message);
            return future;
        }

        enum State {HTTP_CLIENT, HTTP_WAIT_FOR_RESPONSE, TCP_CLIENT, TCP_WAIT_FOR_RESPONSE,}
    }

    public static class FullHttpRequestToFunctionHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final Function<FullHttpRequest, FullHttpResponse> handler;

        public FullHttpRequestToFunctionHandler(Function<FullHttpRequest, FullHttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            var resp = handler.apply(msg);
            ctx.writeAndFlush(resp);
        }
    }

    public static class StringToFunctionHandler extends SimpleChannelInboundHandler<String> {
        private final Function<String, String> handler;

        public StringToFunctionHandler(Function<String, String> handler) {
            this.handler = handler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            var resp = handler.apply(msg);
            ctx.writeAndFlush(resp);
        }
    }
}
