package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.FlowPublisher;

public class HttpRequestInboundProxy extends SimpleChannelInboundHandler<HttpObject> {
    private final FlowPublisher<HttpObject> target;

    public HttpRequestInboundProxy(FlowPublisher<HttpObject> target) {
        this.target = target;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest request) {
            target.next(request);
        }

        if (msg instanceof HttpContent httpContent) {
            var buffer = httpContent.content().retainedDuplicate();
            var newContent = buffer instanceof LastHttpContent ? new DefaultLastHttpContent(buffer) : new DefaultHttpContent(buffer);
            target.next(newContent);
        }
    }
}
