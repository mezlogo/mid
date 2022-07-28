package mezlogo.mid.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Flow;

public class TraceSubscription<T> implements Flow.Subscriber<T> {
    private final static Logger logger = LoggerFactory.getLogger(TraceSubscription.class);
    private final Flow.Subscriber<T> delegate;
    private final String id;

    public TraceSubscription(String id, Flow.Subscriber<T> delegate) {
        this.delegate = delegate;
        this.id = id;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        logger.info("onSubscribe. id: '{}', sub: '{}'", id, subscription);
        delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(T item) {
        logger.info("onNext. id: '{}', item: '{}'", id, item);
        delegate.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.info("onError. id: '{}', throwable: '{}'", id, throwable);
        delegate.onError(throwable);
    }

    @Override
    public void onComplete() {
        logger.info("onComplete. id: '{}'", id);
        delegate.onComplete();
    }
}
