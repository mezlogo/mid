package mezlogo.mid.netty.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

public class NettyOutboundAdapter extends ChannelOutboundHandlerAdapter {
    private final EmbeddedChannel channel;

    public NettyOutboundAdapter(EmbeddedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        channel.writeInbound(msg);
        channel.outboundMessages().forEach(it -> {
            if (it instanceof ByteBuf byteBuf) {
                ctx.fireChannelRead(byteBuf.retainedDuplicate());
            } else {
                ctx.fireChannelRead(it);
            }
        });
    }
}
