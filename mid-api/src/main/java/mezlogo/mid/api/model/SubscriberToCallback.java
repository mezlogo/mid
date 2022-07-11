package mezlogo.mid.api.model;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class SubscriberToCallback <T> implements Flow.Subscriber<T> {
    private final Consumer<T> onNext;
    private final Runnable onComplete;

    public SubscriberToCallback(Consumer<T> onNext, Runnable onComplete) {
        this.onNext = onNext;
        this.onComplete = onComplete;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
    }

    @Override
    public void onNext(T item) {
        onNext.accept(item);
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {
        onComplete.run();
    }
}
