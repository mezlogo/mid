package mezlogo.mid.netty.test;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObject;

import java.util.concurrent.Flow;

public class HttpSubscriber implements Flow.Subscriber<HttpObject> {
    private final EmbeddedChannel channel;

    public HttpSubscriber(EmbeddedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
    }

    @Override
    public void onNext(HttpObject item) {
        channel.writeOutbound(item);
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {
        channel.close();
    }
}
