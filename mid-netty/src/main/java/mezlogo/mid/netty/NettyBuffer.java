package mezlogo.mid.netty;

import io.netty.buffer.ByteBuf;
import mezlogo.mid.api.model.HttpBuffer;

import java.nio.charset.Charset;

public class NettyBuffer implements HttpBuffer {
    public final ByteBuf buf;

    public NettyBuffer(ByteBuf buf) {
        this.buf = buf;
    }

    @Override
    public String asString() {
        return buf.toString(Charset.defaultCharset());
    }
}
