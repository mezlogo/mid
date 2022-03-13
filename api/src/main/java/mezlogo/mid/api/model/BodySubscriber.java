package mezlogo.mid.api.model;

import java.util.concurrent.Flow;

@FunctionalInterface
public interface BodySubscriber extends Flow.Subscriber<HttpBuffer> {
    default void onSubscribe(Flow.Subscription subscription) {
    }

    default void onError(Throwable throwable) {

    }

    default void onComplete() {

    }
}
