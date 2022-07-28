package mezlogo.mid.netty.test;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.SocketAddress;

public class NamedEmbeddedChannel extends EmbeddedChannel {
    private final NamedEmbeddedSocketAddress local, remote;

    public NamedEmbeddedChannel(String local, String remote, ChannelHandler... handlers) {
        this.local = new NamedEmbeddedSocketAddress(local);
        this.remote = new NamedEmbeddedSocketAddress(remote);
        if (null != handlers) {
            for (ChannelHandler handler : handlers) {
                this.pipeline().addLast(handler);
            }
        }
    }

    @Override
    public SocketAddress remoteAddress() {
        return isActive() ? remote : null;
    }

    @Override
    public SocketAddress localAddress() {
        return isActive() ? local : null;
    }

    @Override
    public String toString() {
        return String.format("[embedded channel. local: '%s' to remote: '%s']", local, remote);
    }

    public static class NamedEmbeddedSocketAddress extends SocketAddress {
        private final String name;

        public NamedEmbeddedSocketAddress(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
