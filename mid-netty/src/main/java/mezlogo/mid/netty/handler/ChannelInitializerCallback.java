package mezlogo.mid.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

import java.util.function.Consumer;

public class ChannelInitializerCallback extends ChannelInitializer<Channel> {
    private final Consumer<Channel> callback;

    public ChannelInitializerCallback(Consumer<Channel> callback) {
        this.callback = callback;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        callback.accept(ch);
    }
}
