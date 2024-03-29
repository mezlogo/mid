package mezlogo.mid.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import mezlogo.mid.api.HttpTunnelServer;
import mezlogo.mid.netty.handler.HttpTunnelHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class NettyHttpTunnelServer extends HttpTunnelServer {
    private final ServerBootstrap bootstrap;

    public NettyHttpTunnelServer(ServerBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public static ChannelInitializer<Channel> tunnelInitializer(Supplier<HttpTunnelHandler> tunnelFactory, AppFactory factory) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                if (factory.getConfig().verboseServer()) {
                    ch.pipeline().addLast("logger", new LoggingHandler("mezlogo.mid.netty.server"));
                }
                ch.pipeline().addLast("http-server-codec", new HttpServerCodec());
                ch.pipeline().addLast("http-tunnel-handler", tunnelFactory.get());
            }
        };
    }


    @Override
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ports.forEach(port -> {
            bootstrap.bind(port).addListener(NettyUtils.toFuture(future));
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        bootstrap.config().group().shutdownGracefully().addListener(it -> future.complete(null));
        return future;
    }
}
