package mezlogo.mid.netty.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.SubscriberToCallback;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyUtils;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

public class HttpTunnelHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final Function<String, Optional<URI>> parseProxyUri;
    private final NettyNetworkClient client;
    private final AppFactory factory;

    public HttpTunnelHandler(Function<String, Optional<URI>> parseProxyUri, NettyNetworkClient client, AppFactory factory) {
        this.parseProxyUri = parseProxyUri;
        this.client = client;
        this.factory = factory;
    }

    private static void responseErrorAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        ctx.writeAndFlush(NettyUtils.createResponse(status, Optional.of(msg))).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest req) {
            var targetUriOpt = parseProxyUri.apply(req.uri());

            if (targetUriOpt.isEmpty()) {
                responseErrorAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: '" + req.uri() + "'");
                return;
            }

            var uri = targetUriOpt.get();
            var host = uri.getHost();
            int port = uri.getPort();
            var requestPublisher = factory.createPublisher();
            var future = client.openHttpConnection(host, port, requestPublisher);
            future.thenAccept(response -> {
                var proxyHandler = factory.createProxyHandler(requestPublisher);
                ctx.pipeline().replace(this, "proxy", proxyHandler);
                var ch = ctx.channel();
                response.subscribe(new SubscriberToCallback<>(ch::writeAndFlush, ch::close));
                req.setUri(uri.getPath() + Optional.ofNullable(uri.getQuery()).map(it -> "?" + it).orElse(""));
                ctx.fireChannelRead(req);
            });
            future.exceptionally(exc -> {
                responseErrorAndClose(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: '" + host + ":" + port + "' is unreachable");
                return null;
            });
        }
    }
}
