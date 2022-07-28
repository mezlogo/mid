package mezlogo.mid.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class HttpTunnelHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final static Logger logger = LoggerFactory.getLogger(HttpTunnelHandler.class);
    private final Function<String, Optional<URI>> parseProxyUri;
    private final Function<String, Optional<HostAndPort>> parseProxySocket;
    private final Predicate<HostAndPort> isDecrypt;
    private final NettyNetworkClient client;
    private final AppFactory factory;

    public HttpTunnelHandler(Function<String, Optional<URI>> parseProxyUri, Function<String, Optional<HostAndPort>> parseProxySocket, Predicate<HostAndPort> isDecrypt, NettyNetworkClient client, AppFactory factory) {
        this.parseProxyUri = parseProxyUri;
        this.parseProxySocket = parseProxySocket;
        this.isDecrypt = isDecrypt;
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
                var hostAndPortOption = parseProxySocket.apply(req.uri());

                if (hostAndPortOption.isEmpty()) {
                    responseErrorAndClose(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: '" + req.uri() + "'");
                    return;
                }

                var ch = ctx.channel();
                HostAndPort hostAndPort = hostAndPortOption.get();
                logger.info("tunneltries to open connection. uri: '{}', ch: '{}'", hostAndPort, ch);

                var requestPublisher = factory.createBytebufPublisher();
                boolean useDecryptor = isDecrypt.test(hostAndPort);

                var future = (useDecryptor)
                        ? client.openDecryptedStreamConnection(hostAndPort.host(), hostAndPort.port(), requestPublisher)
                        : client.openStreamConnection(hostAndPort.host(), hostAndPort.port(), requestPublisher);

                future.thenAccept(response -> {
                    logger.info("proxy raw client established connection. uri: '{}', ch: '{}'", hostAndPort, ch);

                    ctx.writeAndFlush(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.empty()))
                            .addListener(NettyUtils.twoCallbacks(
                                    channel -> factory.turnHttpServerToRawBytes(channel, useDecryptor, requestPublisher),
                                    exc -> responseErrorAndClose(ch, HttpResponseStatus.BAD_REQUEST, exc.toString())));

                    response.subscribe(factory.subscribeBytes(ch));
                });
                future.exceptionally(exc -> {
                    logger.info("proxy client can not established connection. uri: '{}', ch: '{}'", hostAndPortOption, ch);
                    responseErrorAndClose(ch, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: '" + hostAndPort.host() + ":" + hostAndPort.port() + "' is unreachable");
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

                factory.turnHttpServerToHttpProxy(ch, requestPublisher);
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
