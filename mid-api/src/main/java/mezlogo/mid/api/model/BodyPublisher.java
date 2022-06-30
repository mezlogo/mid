package mezlogo.mid.api.model;

import java.util.concurrent.Flow;

@FunctionalInterface
public interface BodyPublisher extends Flow.Publisher<HttpBuffer> {
    default void publish(HttpBuffer buffer) {
    }

    default void complete() {
    }
}
