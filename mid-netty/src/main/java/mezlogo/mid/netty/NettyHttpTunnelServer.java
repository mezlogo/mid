package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import mezlogo.mid.api.HttpTunnelServer;

import java.util.concurrent.CompletableFuture;

public class NettyHttpTunnelServer extends HttpTunnelServer {
    private final ServerBootstrap bootstrap;

    public NettyHttpTunnelServer(ServerBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public static ChannelInitializer<Channel> tunnelInitializer() {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("http-server-codec", new HttpServerCodec())
                        .addLast("http-tunnel-handler", new HttpTunnelHandler(null, null));
            }
        };
    }

    public static ServerBootstrap createServer(ChannelInitializer<Channel> handlers, NioEventLoopGroup group) {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(group)
                .childHandler(handlers)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        return serverBootstrap;
    }

    public static Bootstrap createClient(ChannelInitializer<Channel> handlers, NioEventLoopGroup group) {
        Bootstrap clientBootstrap = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(group)
                .handler(handlers)
                .option(ChannelOption.SO_KEEPALIVE, true);
        return clientBootstrap;
    }

    @Override
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        bootstrap.bind(8080).addListener(NettyUtils.toFuture(future));
        return future;
    }
}
