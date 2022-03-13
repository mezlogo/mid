package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.BodyType;
import mezlogo.mid.api.model.HttpHandler;
import mezlogo.mid.api.utils.Publishers;

public class NettyHttpRequestInboundChannelHandlerToMid extends TraceHttpRequestInboundChannelHandler {
    private final HttpHandler handler;
    private BodyPublisher requestBodyPublisher;
    private NettyWriteHttpBodySubscriber responseWriter;

    public NettyHttpRequestInboundChannelHandlerToMid(HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            var request = Utils.convert((HttpRequest) msg);
            this.requestBodyPublisher = new Publishers.SimplePublisher();
            this.responseWriter = new NettyWriteHttpBodySubscriber(ctx::writeAndFlush);
            var handled = handler.handle(request, this.requestBodyPublisher);

            handled.getKey().thenAccept(resp -> {
                this.responseWriter.isChunk = BodyType.CHUNK == resp.type;
                ctx.writeAndFlush(Utils.convert(resp));
            });
            handled.getValue().subscribe(this.responseWriter);
        } else if (msg instanceof LastHttpContent) {
            var content = (LastHttpContent) msg;
            if (0 < content.content().readableBytes()) {
                NettyBuffer nettyBuffer = new NettyBuffer(content.content());
                this.requestBodyPublisher.publish(nettyBuffer);
            }
            this.requestBodyPublisher.complete();
        } else if (msg instanceof HttpContent) {
            var content = (HttpContent) msg;
            NettyBuffer nettyBuffer = new NettyBuffer(content.content());
            this.requestBodyPublisher.publish(nettyBuffer);
        } else {
            throw new UnsupportedOperationException("Can not handle: " + msg);
        }
    }
}
