package mezlogo.mid.core.netty;

import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import mezlogo.mid.api.model.BodySubscriber;
import mezlogo.mid.api.model.HttpBuffer;
import mezlogo.mid.api.model.StringBuffer;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class NettyWebsocketWriteBodySubscriber implements BodySubscriber {
    private final Consumer<Object> write;

    public NettyWebsocketWriteBodySubscriber(Consumer<Object> write) {
        this.write = write;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
    }

    @Override
    public void onNext(HttpBuffer item) {
        if (item instanceof StringBuffer) {
            var content = ((StringBuffer) item).data;
            var sendIt = new TextWebSocketFrame(content);
            write.accept(sendIt);
        } else {
            throw new UnsupportedOperationException("Supproted only String and ByteBuf, get: " + item);
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {
        write.accept(new CloseWebSocketFrame());
    }
}
