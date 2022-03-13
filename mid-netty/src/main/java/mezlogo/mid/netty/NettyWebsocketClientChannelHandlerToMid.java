package mezlogo.mid.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.model.StringBuffer;

import java.util.concurrent.CompletableFuture;

public class NettyWebsocketClientChannelHandlerToMid extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private final BodyPublisher publisher;
    private final CompletableFuture<HttpResponse> future;


    public NettyWebsocketClientChannelHandlerToMid(WebSocketClientHandshaker handshaker, CompletableFuture<HttpResponse> future, BodyPublisher publisher) {
        this.handshaker = handshaker;
        this.publisher = publisher;
        this.future = future;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        handshaker.handshake(ctx.channel());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete() && msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            handshaker.finishHandshake(ch, response);
            future.complete(Utils.convert(response));
            return;
        }

        if (handshaker.isHandshakeComplete() && msg instanceof FullHttpResponse) {
            throw new IllegalStateException("HttpResponse after handshaker");
        }

        if (!(msg instanceof WebSocketFrame)) {
            ch.close();
            throw new IllegalArgumentException("Expected websocket frame");
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            publisher.publish(new StringBuffer(textFrame.text()));
        } else if (frame instanceof PongWebSocketFrame) {
        } else if (frame instanceof CloseWebSocketFrame) {
            ch.close();
            publisher.complete();
        } else {
            throw new UnsupportedOperationException("Don not know how to handle: " + msg);
        }

    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        cause.printStackTrace();

        if (!future.isDone()) {
            future.completeExceptionally(cause);
        }

        ctx.close();
    }
}
