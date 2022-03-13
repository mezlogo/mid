package mezlogo.mid.netty;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedStream;
import mezlogo.mid.api.model.BodySubscriber;
import mezlogo.mid.api.model.HttpBuffer;
import mezlogo.mid.api.model.StringBuffer;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class NettyWriteHttpBodySubscriber implements BodySubscriber {
    private final Consumer<Object> write;
    public boolean isChunk = false;

    public NettyWriteHttpBodySubscriber(Consumer<Object> write) {
        this.write = write;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
    }

    @Override
    public void onNext(HttpBuffer item) {
        if (item instanceof StringBuffer) {
            var content = ((StringBuffer) item).data;
            Object httpObj;
            if (isChunk) {
                httpObj = new ChunkedStream(new ByteArrayInputStream(content.getBytes()));
            } else {
                httpObj = new DefaultHttpContent(Unpooled.copiedBuffer(content, Charset.defaultCharset()));
            }
            write.accept(httpObj);
        } else {
            throw new UnsupportedOperationException("Supproted only String and ByteBuf, get: " + item);
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {
        write.accept(LastHttpContent.EMPTY_LAST_CONTENT);
    }
}
