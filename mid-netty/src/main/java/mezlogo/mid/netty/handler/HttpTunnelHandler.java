package mezlogo.mid.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

public class HttpTunnelHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final static Logger logger = LoggerFactory.getLogger(HttpTunnelHandler.class);
    private final Function<String, Optional<URI>> parseProxyUri;
    private final Function<String, Optional<HostAndPort>> parseProxySocket;
    private final NettyNetworkClient client;
    private final AppFactory factory;

    public HttpTunnelHandler(Function<String, Optional<URI>> parseProxyUri, Function<String, Optional<HostAndPort>> parseProxySocket, NettyNetworkClient client, AppFactory factory) {
        this.parseProxyUri = parseProxyUri;
        this.parseProxySocket = parseProxySocket;
        this.client = client;
        this.factory = factory;
    }

    private static void responseErrorAndClose(Channel ch, HttpResponseStatus status, String msg) {
        logger.info("send error response. ch: '{}', status: '{}', msg: '{}'", ch, status, msg);
        ch.writeAndFlush(NettyUtils.createResponse(status, Optional.of(msg))).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest req) {
            if (HttpMethod.CONNECT.equals(req.method())) {
                var hostAndPort = parseProxySocket.apply(req.uri());

                if (hostAndPort.isEmpty()) {
                    responseErrorAndClose(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: '" + req.uri() + "'");
                    return;
                }

                var ch = ctx.channel();
                logger.info("tunneltries to open connection. uri: '{}', ch: '{}'", hostAndPort.get(), ch);

                var requestPublisher = factory.createBytebufPublisher();
                var future = client.openStreamConnection(hostAndPort.get().host(), hostAndPort.get().port(), requestPublisher);
                future.thenAccept(response -> {
                    logger.info("proxy raw client established connection. uri: '{}', ch: '{}'", hostAndPort.get(), ch);
                    ctx.writeAndFlush(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.empty()))
                            .addListener(NettyUtils.twoCallbacks(
                                    channel -> {
                                        channel.pipeline().remove(HttpServerCodec.class);
                                    },
                                    exc -> responseErrorAndClose(ch, HttpResponseStatus.BAD_REQUEST, exc.toString())));
                    var newHandler = factory.createServerProxyBytesHandler(requestPublisher);
                    ctx.pipeline().replace(this, "bytes-server-publisher-handler", newHandler);
                    response.subscribe(factory.subscribeBytes(ch));
                });
                future.exceptionally(exc -> {
                    logger.info("proxy client can not established connection. uri: '{}', ch: '{}'", hostAndPort, ch);
                    responseErrorAndClose(ch, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: '" + hostAndPort.get().host() + ":" + hostAndPort.get().port() + "' is unreachable");
                    return null;
                });
            } else {
                var targetUriOpt = parseProxyUri.apply(req.uri());

                if (targetUriOpt.isEmpty()) {
                    responseErrorAndClose(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: '" + req.uri() + "'");
                    return;
                }

                var uri = targetUriOpt.get();
                var host = uri.getHost();
                int port = -1 == uri.getPort() ? 80 : uri.getPort();
                var requestPublisher = factory.createHttpPublisher();
                var future = client.openHttpConnection(host, port, requestPublisher);
                var ch = ctx.channel();

                var proxyHandler = factory.createServerProxyHandler(requestPublisher);
                ctx.pipeline().replace(this, "http-server-publisher-handler", proxyHandler);
                req.setUri(uri.getPath() + Optional.ofNullable(uri.getQuery()).map(it -> "?" + it).orElse(""));
                ctx.fireChannelRead(req);

                logger.info("tunnel tries to open connection. uri: '{}', ch: '{}'", uri, ch);

                future.thenAccept(response -> {
                    logger.info("proxy client established connection. uri: '{}', ch: '{}'", uri, ch);
                    response.subscribe(factory.subscribeHttpObject(ch));
                });
                future.exceptionally(exc -> {
                    logger.info("proxy client can not established connection. uri: '{}', ch: '{}'", uri, ch);
                    responseErrorAndClose(ch, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: '" + host + ":" + port + "' is unreachable");
                    return null;
                });
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel added. ctx: '{}'", ctx);
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        logger.info("tunnel removed. ctx: '{}'", ctx);
        super.handlerRemoved(ctx);
    }
}
