package mezlogo.mid.netty.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.function.Consumer;

public class StringToCallbackHandler extends SimpleChannelInboundHandler<String> {
    private final Consumer<String> callback;

    public StringToCallbackHandler(Consumer<String> callback) {
        this.callback = callback;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        callback.accept(msg);
    }
}
