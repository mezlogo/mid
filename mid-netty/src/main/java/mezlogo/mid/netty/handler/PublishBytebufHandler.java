package mezlogo.mid.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import mezlogo.mid.api.model.FlowPublisher;

public class PublishBytebufHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final FlowPublisher<ByteBuf> publisher;

    public PublishBytebufHandler(FlowPublisher<ByteBuf> publisher) {
        this.publisher = publisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        publisher.next(msg.retainedDuplicate());
    }
}
