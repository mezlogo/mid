package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.api.model.SubscriberToCallback;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.ChannelInitializerCallback;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.handler.PublishBytebufHandler;
import mezlogo.mid.netty.handler.PublishHttpObjectHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AppFactory {
    private final AppConfig config;
    private NioEventLoopGroup group;
    private Bootstrap clientBootstrap;
    private ServerBootstrap serverBootstrap;
    private NettyNetworkClient client;
    private NettyHttpTunnelServer server;
    private Predicate<HostAndPort> isDecrypt;
    private SslContext clientSslContext;
    private SslContext serverSslContext;

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
        return new HttpTunnelHandler(MidUtils::uriParser, MidUtils::socketParser, getIsDecrypt(), getClient(), this);
    }

    public PublishHttpObjectHandler createServerProxyHandler(FlowPublisher<HttpObject> requestPublisher) {
        return new PublishHttpObjectHandler(requestPublisher, ctx -> NettyUtils.resetHttpTunnel(ctx, this));
    }

    public FlowPublisher<ByteBuf> createBytebufPublisher() {
        return new BufferedPublisher<>();
    }

    public FlowPublisher<HttpObject> createHttpPublisher() {
        return new BufferedPublisher<>();
    }

    public AppConfig getConfig() {
        return config;
    }

    public FlowPublisher<ByteBuf> initBytesClient(Channel channel, boolean useDecryptor) {
        var responsePublisher = createBytebufPublisher();

        ChannelPipeline pipeline = channel.pipeline();
        if (useDecryptor) {
            pipeline.addLast("ssl-client", new SslHandler(getClientSslContext().newEngine(channel.alloc())));
        }

        if (config.verboseClient()) {
            pipeline.addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
        }
        pipeline.addLast("http-client-publisher-handler", new PublishBytebufHandler(responsePublisher));

        return responsePublisher;
    }

    public FlowPublisher<HttpObject> initHttpClient(Channel channel) {
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

    public void setSocketsToDecrypt(Predicate<HostAndPort> isDecrypt) {
        this.isDecrypt = isDecrypt;
    }

    public Predicate<HostAndPort> getIsDecrypt() {
        return null == isDecrypt ? socket -> false : isDecrypt;
    }

    public SslContext getServerSslContext() {
        if (null == serverSslContext) {
            serverSslContext = NettyUtils.serverSsl();
        }
        return serverSslContext;
    }

    public SslContext getClientSslContext() {
        if (null == clientSslContext) {
            clientSslContext = NettyUtils.clientSsl();
        }
        return clientSslContext;
    }

    public void turnHttpServerToRawBytes(Channel channel, boolean useDecryptor, FlowPublisher<ByteBuf> requestPublisher) {
        var newHandler = this.createServerProxyBytesHandler(requestPublisher);
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.remove(HttpServerCodec.class);
        pipeline.remove(HttpTunnelHandler.class);
        if (useDecryptor) {
            if (null != pipeline.get(LoggingHandler.class)) {
                pipeline.addBefore("logger", "ssl-server", getServerSslContext().newHandler(channel.alloc()));
            } else {
                pipeline.addLast("ssl-server", getServerSslContext().newHandler(channel.alloc()));
            }
        }
        pipeline.addLast( "bytes-server-publisher-handler", newHandler);
    }

    public void turnHttpServerToHttpProxy(Channel channel, FlowPublisher<HttpObject> requestPublisher) {
        var proxyHandler = this.createServerProxyHandler(requestPublisher);
        channel.pipeline().replace(HttpTunnelHandler.class, "http-server-publisher-handler", proxyHandler);
    }
}
