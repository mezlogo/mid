package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
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
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;
import mezlogo.mid.netty.handler.HttpTunnelHandler;

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

    public static Bootstrap createClient(NioEventLoopGroup group, AppConfig config) {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .handler(new ChannelInitializerCallback(ch -> {
                    if (config.verboseClient()) {
                        ch.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
                    }
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
            clientBootstrap = createClient(getGroup(), config);
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
        return new HttpTunnelHandler(MidUtils::uriParser, getClient(), this);
    }

    public HttpProxyHandlerToPublisher createServerProxyHandler(FlowPublisher<HttpObject> requestPublisher) {
        return new HttpProxyHandlerToPublisher(requestPublisher, ctx -> NettyUtils.resetHttpTunnel(ctx, this));
    }

    public BufferedPublisher<HttpObject> createPublisher() {
        return new BufferedPublisher<>();
    }

    public AppConfig getConfig() {
        return config;
    }

    public BufferedPublisher<HttpObject> initHttpClient(Channel channel) {
        var responsePublisher = createPublisher();

        if (config.verboseClient()) {
            channel.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.client"));
        }
        channel.pipeline().addLast("http-client-codec", new HttpClientCodec())
                .addLast("adapter", new HttpProxyHandlerToPublisher(responsePublisher));

        return responsePublisher;
    }

    public Flow.Subscriber<? super HttpObject> subscribe(Channel channel) {
        return new SubscriberToCallback<>(channel::writeAndFlush, channel::close);
    }
}
