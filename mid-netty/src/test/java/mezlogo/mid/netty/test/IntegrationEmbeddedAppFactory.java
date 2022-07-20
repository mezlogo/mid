package mezlogo.mid.netty.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class IntegrationEmbeddedAppFactory extends AppFactory {
    private ScheduledExecutorService executor;
    public Supplier<NettyNetworkClient> clientSupplier;
    private final boolean throwOnBootstrap;

    public IntegrationEmbeddedAppFactory(AppConfig config) {
        this(config, true);
    }
    public IntegrationEmbeddedAppFactory(AppConfig config, boolean throwOnBootstrap) {
        super(config);
        this.throwOnBootstrap = throwOnBootstrap;
    }

    public ScheduledExecutorService getExecutor() {
        if (null == executor) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        return executor;
    }

    public NettyNetworkClient createTestClient(BiFunction<String, Integer, CompletableFuture<Channel>> connector) {
        return new NettyNetworkClientFunction(connector, this);
    }

    public EmbeddedChannel createPlainChannelWithHttpServer(Function<FullHttpRequest, FullHttpResponse> serverHandler) {
        var serverChannel = new EmbeddedChannel(new LoggingHandler("mezlogo.mid.netty.test.server"), new HttpServerCodec(), new HttpObjectAggregator(1024), new NettySyncHttpHandler(serverHandler));
        return new EmbeddedChannel(new NettyOutboundAdapter(serverChannel));
    }

    public EmbeddedChannel createTunnelChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        initTunnelChannel(channel);
        return channel;
    }

    public EmbeddedChannel createHttpClientWithTunnel() {
        return new EmbeddedChannel(new NettyOutboundAdapter(createTunnelChannel()), new LoggingHandler("mezlogo.mid.netty.test.client"), new HttpClientCodec(), new HttpObjectAggregator(1024));
    }

    public Function<FullHttpRequest, CompletableFuture<FullHttpResponse>> createFullHttpClientWithTunnel() {
        var client = createHttpClientWithTunnel();
        return req -> {
            var future = new CompletableFuture<FullHttpResponse>();
            if (null != client.pipeline().get(FullHttpResponseToCallback.class)) {
                client.pipeline().remove(FullHttpResponseToCallback.class);
            }
            client.pipeline().addLast(new FullHttpResponseToCallback(future::complete));
            client.writeOutbound(req);
            return future;
        };
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
        return new HttpTunnelHandler(MidUtils::uriParser, clientSupplier.get(), this);
    }

    @Override
    public NioEventLoopGroup getGroup() {
        if (throwOnBootstrap) {
            throw new IllegalArgumentException("you should not call this method");
        }
        return super.getGroup();
    }
}
