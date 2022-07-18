package mezlogo.mid.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMessage;
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

    private static void responseErrorAndClose(Channel ch, HttpResponseStatus status, String msg) {
        ch.writeAndFlush(NettyUtils.createResponse(status, Optional.of(msg))).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest req) {
            var targetUriOpt = parseProxyUri.apply(req.uri());

            if (targetUriOpt.isEmpty()) {
                responseErrorAndClose(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: '" + req.uri() + "'");
                return;
            }

            var uri = targetUriOpt.get();
            var host = uri.getHost();
            int port = uri.getPort();
            var requestPublisher = factory.createPublisher();
            var future = client.openHttpConnection(host, port, requestPublisher);
            var ch = ctx.channel();

            var proxyHandler = factory.createProxyHandler(requestPublisher, () -> new HttpTunnelHandler(parseProxyUri, client, factory));
            ctx.pipeline().replace(this, "proxy", proxyHandler);
            req.setUri(uri.getPath() + Optional.ofNullable(uri.getQuery()).map(it -> "?" + it).orElse(""));
            ctx.fireChannelRead(req);

            future.thenAccept(response -> {
                response.subscribe(new SubscriberToCallback<>(ch::writeAndFlush, ch::close));
            });
            future.exceptionally(exc -> {
                responseErrorAndClose(ch, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: '" + host + ":" + port + "' is unreachable");
                return null;
            });
        }
    }
}
