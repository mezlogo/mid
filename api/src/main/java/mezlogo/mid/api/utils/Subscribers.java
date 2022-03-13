package mezlogo.mid.api.utils;

import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.BodySubscriber;
import mezlogo.mid.api.model.HttpBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Subscribers {
    public static BodySubscriber emptySubscriber() {
        return it -> {
        };
    }

    public static class ToPublisherSubscriber implements BodySubscriber {
        private final Function<HttpBuffer, HttpBuffer> onEachMessage;
        private final BodyPublisher publisher;

        public ToPublisherSubscriber(Function<HttpBuffer, HttpBuffer> onEachMessage, BodyPublisher publisher) {
            this.onEachMessage = onEachMessage;
            this.publisher = publisher;
        }

        @Override
        public void onNext(HttpBuffer item) {
            publisher.publish(onEachMessage.apply(item));
        }

        @Override
        public void onComplete() {
            publisher.complete();
        }
    }

    public static class AggregateSubscriber implements BodySubscriber {
        public final CompletableFuture<List<String>> future = new CompletableFuture<>();
        private final List<String> buffers = new ArrayList<>();

        @Override
        public void onNext(HttpBuffer item) {
            buffers.add(item.asString());
        }

        @Override
        public void onComplete() {
            future.complete(buffers);
        }
    }

}
