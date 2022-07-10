package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import mezlogo.mid.netty.handler.HttpTunnelHandler;

public class AppFactory {
    public HttpTunnelHandler createTunnelHandler() {
        return null;
    }

    public void makeItProxy(ChannelHandlerContext ctx) {
    }
}
