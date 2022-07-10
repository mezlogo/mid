package mezlogo.mid.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.netty.NettyUtils;

public class HttpProxyHandlerToPublisher extends SimpleChannelInboundHandler<HttpObject> {
    private final FlowPublisher<HttpObject> publisher;

    public HttpProxyHandlerToPublisher(FlowPublisher<HttpObject> publisher) {
        this.publisher = publisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest request) {
            var proxy = NettyUtils.createPartialRequest(request.uri(), request.headers().get(HttpHeaderNames.HOST), request.method());
            proxy.headers().add(request.headers());
            publisher.next(proxy);
        } else if (msg instanceof HttpResponse response) {
            var proxy = new DefaultHttpResponse(response.protocolVersion(), response.status());
            proxy.headers().add(response.headers());
            publisher.next(proxy);
        }

        if (msg instanceof HttpContent content) {
            var buf = content.content().retainedDuplicate();
            var proxy = msg instanceof LastHttpContent ? new DefaultLastHttpContent(buf) : new DefaultHttpContent(buf);
            publisher.next(proxy);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.isRemoved()) {
            publisher.complete();
        }
        super.channelInactive(ctx);
    }
}
