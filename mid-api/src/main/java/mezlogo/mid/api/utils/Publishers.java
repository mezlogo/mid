package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.HttpBuffer;
import mezlogo.mid.api.model.StringBuffer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

public class Publishers {

    public static BodyPublisher noBody() {
        return Flow.Subscriber::onComplete;
    }

    public static BodyPublisher fromList(List<String> data, boolean close) {
        return subscriber -> {
            data.forEach(it -> subscriber.onNext(new StringBuffer(it)));
            if (close) {
                subscriber.onComplete();
            }
        };
    }

    public static BodyPublisher fromList(List<String> data) {
        return fromList(data, true);
    }

    public static class SimplePublisher implements BodyPublisher {
        protected Flow.Subscriber<? super HttpBuffer> subscriber = Subscribers.emptySubscriber();

        @Override
        public void subscribe(Flow.Subscriber<? super HttpBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void publish(HttpBuffer buffer) {
            subscriber.onNext(buffer);
        }

        @Override
        public void complete() {
            subscriber.onComplete();
        }
    }

    public static class QueuedPublisher extends SimplePublisher {
        private final List<HttpBuffer> queue;

        public QueuedPublisher(List<HttpBuffer> queue) {
            this.queue = queue;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super HttpBuffer> subscriber) {
            super.subscribe(subscriber);
            queue.forEach(this::publish);
        }
    }

    public static class DeferredPublisher implements BodyPublisher {
        private Optional<List<HttpBuffer>> data = Optional.empty();
        private Optional<Flow.Subscriber<? super HttpBuffer>> subscriber = Optional.empty();

        @Override
        public void subscribe(Flow.Subscriber<? super HttpBuffer> subscriber) {
            if (this.subscriber.isPresent()) {
                throw new IllegalStateException("Expected to be fired only once");
            }
            this.subscriber = Optional.of(subscriber);
            writeData();
        }

        private void writeData() {
            if (this.data.isPresent() && this.subscriber.isPresent()) {
                final var sub = this.subscriber.get();
                this.data.get().forEach(sub::onNext);
                sub.onComplete();
            }
        }

        public void sendAndComplete(List<HttpBuffer> data) {
            if (this.data.isPresent()) {
                throw new IllegalStateException("Expected to be fired only once");
            }
            this.data = Optional.of(data);
            writeData();
        }
    }
}
