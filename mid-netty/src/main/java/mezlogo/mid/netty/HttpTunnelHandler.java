package mezlogo.mid.netty;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.BufferedPublisher;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class HttpTunnelHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final NettyNetworkClient client;
    private final HttpTunnelHandlerFactory factory;

    public HttpTunnelHandler(NettyNetworkClient client, HttpTunnelHandlerFactory factory) {
        this.client = client;
        this.factory = factory;
    }

    private static void writeAndClose(ChannelHandlerContext ctx, String msg, HttpResponseStatus status) {
        ctx.writeAndFlush(NettyUtils.createResponse(status, Optional.of(msg))).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest request) {
            URI uri = URI.create(request.uri());

            if (!"http".equals(uri.getScheme()) || uri.getPort() < 0 || null == uri.getHost()) {
                writeAndClose(ctx, "Supported only http scheme and positive number as port: '" + request.uri() + "'", HttpResponseStatus.BAD_REQUEST);
                return;
            }

            var toTargetPublisher = new BufferedPublisher<HttpObject>();
            CompletableFuture<Flow.Publisher<HttpObject>> futureResponse = client.openHttpConnection(uri.getHost(), uri.getPort(), toTargetPublisher);
            futureResponse.exceptionally(err -> {
                writeAndClose(ctx, "Unreachable target url: '" + request.uri() + "'", HttpResponseStatus.SERVICE_UNAVAILABLE);
                return null;
            });
        }
    }
}
