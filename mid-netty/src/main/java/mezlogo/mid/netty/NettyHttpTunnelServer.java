package mezlogo.mid.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import mezlogo.mid.api.HttpTunnelServer;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
                        .addLast("http-echo-handler", new SimpleChannelInboundHandler<HttpObject>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                                if (msg instanceof HttpRequest request) {
                                    var body = "You asked for: " + request.uri() + " using method: " + request.method();
                                    ctx.writeAndFlush(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(body)))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        });

            }
        };
    }

    public static ServerBootstrap createServer(ChannelInitializer<Channel> handlers) {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(new NioEventLoopGroup())
                .childHandler(handlers)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        return serverBootstrap;
    }

    @Override
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        bootstrap.bind(8080).addListener(NettyUtils.toFuture(future));
        return future;
    }
}
