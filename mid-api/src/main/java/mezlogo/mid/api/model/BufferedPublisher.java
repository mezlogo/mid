package mezlogo.mid.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

public class BufferedPublisher<T> implements FlowPublisher<T> {
    private final List<T> buffer = new ArrayList<>();
    private boolean isCompleted = false;
    private Flow.Subscriber<? super T> subscriber;

    @Override
    public void next(T t) {
        if (null == subscriber) {
            buffer.add(t);
        } else {
            subscriber.onNext(t);
        }
    }

    @Override
    public void complete() {
        this.isCompleted = true;
        if (null != subscriber) {
            subscriber.onComplete();
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
        if (!buffer.isEmpty()) {
            buffer.forEach(subscriber::onNext);
            buffer.clear();
        }
        if (isCompleted) {
            subscriber.onComplete();
        }
    }
}

