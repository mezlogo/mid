package mezlogo.mid.api.model;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class WebsocketHandlers {
    public static final BiConsumer<BodyPublisher, HttpBuffer> NOOP = (publisher, buffer) -> {
    };


    public static WebsocketHandler onEchoMsg(Function<String, String> onMsg) {
        return new WebsocketHandler(((publisher, buffer) ->
                publisher.publish(new StringBuffer(onMsg.apply(buffer.asString())))
        ));
    }

    public static WebsocketHandler onOpenSendAndClose(List<String> msgs) {
        return new WebsocketHandler(NOOP) {
            @Override
            public void subscribe(Flow.Subscriber<? super HttpBuffer> subscriber) {
                super.subscribe(subscriber);
                msgs.forEach(it -> publish(new StringBuffer(it)));
                complete();
            }
        };
    }
}
