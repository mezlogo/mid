package mezlogo.mid.api.model;

import mezlogo.mid.api.utils.Publishers;

import java.util.function.BiConsumer;

public class WebsocketHandler extends Publishers.SimplePublisher implements BodySubscriber {
    private final BiConsumer<BodyPublisher, HttpBuffer> messageHandler;

    public WebsocketHandler(BiConsumer<BodyPublisher, HttpBuffer> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void onNext(HttpBuffer item) {
        messageHandler.accept(this, item);
    }
}
