package mezlogo.mid.netty.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

public class OutboundBytebufHandlerAdapter extends ChannelOutboundHandlerAdapter {
    private final EmbeddedChannel channel;

    public OutboundBytebufHandlerAdapter(EmbeddedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) {
            channel.writeInbound(buf);
            promise.setSuccess();
        } else {
            throw new IllegalArgumentException("not a buffer: " + msg);
        }
    }
}
