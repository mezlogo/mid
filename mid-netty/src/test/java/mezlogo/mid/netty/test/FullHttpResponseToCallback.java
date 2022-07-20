package mezlogo.mid.netty.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.function.Consumer;

public class FullHttpResponseToCallback extends SimpleChannelInboundHandler<FullHttpResponse> {
    private final Consumer<FullHttpResponse> callback;

    public FullHttpResponseToCallback(Consumer<FullHttpResponse> callback) {
        this.callback = callback;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        callback.accept(msg.retainedDuplicate());
    }
}
