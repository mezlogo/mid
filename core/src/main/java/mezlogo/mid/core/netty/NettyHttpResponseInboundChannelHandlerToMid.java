package mezlogo.mid.core.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.BodyPublisher;

import java.util.concurrent.CompletableFuture;

public class NettyHttpResponseInboundChannelHandlerToMid extends TraceHttpResponseInboundChannelHandler {
    private final CompletableFuture<mezlogo.mid.api.model.HttpResponse> responseFuture;
    private final BodyPublisher publisher;

    public NettyHttpResponseInboundChannelHandlerToMid(CompletableFuture<mezlogo.mid.api.model.HttpResponse> responseFuture, BodyPublisher publisher) {
        this.responseFuture = responseFuture;
        this.publisher = publisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse) {
            var response = Utils.convert((HttpResponse) msg);
            responseFuture.complete(response);
        } else if (msg instanceof LastHttpContent) {
            var content = (LastHttpContent) msg;
            if (0 < content.content().readableBytes()) {
                NettyBuffer nettyBuffer = new NettyBuffer(content.content());
                publisher.publish(nettyBuffer);
            }
            publisher.complete();
        } else if (msg instanceof HttpContent) {
            var content = (HttpContent) msg;
            NettyBuffer nettyBuffer = new NettyBuffer(content.content());
            publisher.publish(nettyBuffer);
        } else {
            throw new UnsupportedOperationException("Can not handle: " + msg);
        }
    }
}
