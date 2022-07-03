package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.concurrent.Flow;

public class NettyResponseSubscriber implements Flow.Subscriber<HttpObject> {
    private final ChannelHandlerContext context;

    public NettyResponseSubscriber(ChannelHandlerContext context) {
        this.context = context;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
    }

    @Override
    public void onNext(HttpObject item) {
        if (item instanceof HttpResponse response) {
            context.writeAndFlush(response);
        }

        if (item instanceof HttpContent httpContent) {
            var buffer = httpContent.content().retainedDuplicate();
            var newContent = buffer instanceof LastHttpContent ? new DefaultLastHttpContent(buffer) : new DefaultHttpContent(buffer);
            context.writeAndFlush(newContent);
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }
}
