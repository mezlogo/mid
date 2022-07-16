package mezlogo.mid.netty.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.function.Function;

public class NettySyncHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final Function<FullHttpRequest, FullHttpResponse> callback;

    public NettySyncHttpHandler(Function<FullHttpRequest, FullHttpResponse> callback) {
        this.callback = callback;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        ctx.writeAndFlush(callback.apply(msg));
    }
}
