package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.api.model.SubscriberToCallback;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.ChannelInitializerCallback;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.handler.PublishBytebufHandler;
import mezlogo.mid.netty.handler.PublishHttpObjectHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class AppFactory {
    private final AppConfig config;
    private NioEventLoopGroup group;
    private Bootstrap clientBootstrap;
    private ServerBootstrap serverBootstrap;
    private NettyNetworkClient client;
    private NettyHttpTunnelServer server;

    public AppFactory(AppConfig config) {
        this.config = config;
    }

    public static AppFactory createProduction(AppConfig config) {
        var factory = new AppFactory(config);
        return factory;
    }

    public static Bootstrap createClient(NioEventLoopGroup group) {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .handler(new ChannelInitializerCallback(ch -> {
                }))
                .channel(NioSocketChannel.class);
        return bootstrap;
    }

    public static ServerBootstrap createServer(Consumer<Channel> callback, NioEventLoopGroup group) {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(group)
                .childHandler(new ChannelInitializerCallback(callback))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        return serverBootstrap;
    }

    public NioEventLoopGroup getGroup() {
        if (null == group) {
            group = new NioEventLoopGroup();
        }
        return group;
    }

    public Bootstrap getClientBootstrap() {
        if (null == clientBootstrap) {
            clientBootstrap = createClient(getGroup());
        }
        return clientBootstrap;
    }


    public ServerBootstrap getServerBootstrap() {
        if (null == serverBootstrap) {
            serverBootstrap = createServer(this::initTunnelChannel, getGroup());
        }
        return serverBootstrap;
    }

    public NettyHttpTunnelServer getServer() {
        if (null == server) {
            server = new NettyHttpTunnelServer(getServerBootstrap());
        }
        return server;
    }

    public NettyNetworkClient getClient() {
        if (null == client) {
            client = new NettyNetworkClientFunction(this::openChannelFactory, this);
        }
        return client;
    }

    public void initTunnelChannel(Channel ch) {
        if (config.verboseServer()) {
            ch.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.server"));
        }
        ch.pipeline().addLast("http-server-codec", new HttpServerCodec());
        ch.pipeline().addLast("http-tunnel-handler", createHttpTunnelHandler());
    }

    public CompletableFuture<Channel> openChannelFactory(String host, int port) {
        return NettyUtils.openChannel(getClientBootstrap(), host, port);
    }

    public HttpTunnelHandler createHttpTunnelHandler() {
        return new HttpTunnelHandler(MidUtils::uriParser, MidUtils::socketParser, getClient(), this);
    }

    public PublishHttpObjectHandler createServerProxyHandler(FlowPublisher<HttpObject> requestPublisher) {
        return new PublishHttpObjectHandler(requestPublisher, ctx -> NettyUtils.resetHttpTunnel(ctx, this));
    }

    public BufferedPublisher<ByteBuf> createBytebufPublisher() {
        return new BufferedPublisher<>();
    }

    public BufferedPublisher<HttpObject> createHttpPublisher() {
        return new BufferedPublisher<>();
    }

    public AppConfig getConfig() {
        return config;
    }

    public BufferedPublisher<ByteBuf> initBytesClient(Channel channel) {
        var responsePublisher = createBytebufPublisher();

        if (config.verboseClient()) {
            channel.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
        }
        channel.pipeline().addLast("http-client-publisher-handler", new PublishBytebufHandler(responsePublisher));

        return responsePublisher;
    }

    public BufferedPublisher<HttpObject> initHttpClient(Channel channel) {
        var responsePublisher = createHttpPublisher();

        if (config.verboseClient()) {
            channel.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
        }
        channel.pipeline().addLast("http-client-codec", new HttpClientCodec())
                .addLast("http-client-publisher-handler", new PublishHttpObjectHandler(responsePublisher));

        return responsePublisher;
    }

    public Flow.Subscriber<? super ByteBuf> subscribeBytes(Channel channel) {
        return new TraceSubscription<>(channel.toString(), new SubscriberToCallback<>(channel::writeAndFlush, channel::close));
    }

    public Flow.Subscriber<? super HttpObject> subscribeHttpObject(Channel channel) {
        return new TraceSubscription<>(channel.toString(), new SubscriberToCallback<>(channel::writeAndFlush, channel::close));
    }

    public PublishBytebufHandler createServerProxyBytesHandler(FlowPublisher<ByteBuf> requestPublisher) {
        return new PublishBytebufHandler(requestPublisher);
    }
}
