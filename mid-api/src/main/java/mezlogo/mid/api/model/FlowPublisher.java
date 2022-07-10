package mezlogo.mid.api.model;

import java.util.concurrent.Flow;

public interface FlowPublisher<T> extends Flow.Publisher<T> {
    void next(T t);

    void complete();
}
