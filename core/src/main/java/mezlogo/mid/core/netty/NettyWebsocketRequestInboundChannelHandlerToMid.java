package mezlogo.mid.core.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.StringBuffer;

public class NettyWebsocketRequestInboundChannelHandlerToMid extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final BodyPublisher inboundBodyPublisher;

    public NettyWebsocketRequestInboundChannelHandlerToMid(BodyPublisher inboundBodyPublisher) {
        this.inboundBodyPublisher = inboundBodyPublisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            inboundBodyPublisher.publish(new StringBuffer(frame.text()));
        } else if (msg instanceof CloseWebSocketFrame) {
            inboundBodyPublisher.complete();
            ctx.close();
        } else {
            System.out.println("I DON KNOW WHAT TO DO WITH THIS: " + msg);
        }
    }
}
