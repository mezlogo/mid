package mezlogo.mid.netty;

import io.netty.handler.codec.http.HttpObject;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;
import mezlogo.mid.netty.handler.HttpTunnelHandler;

import java.util.function.Supplier;

public class AppFactory {
    private final AppConfig config;

    public AppFactory(AppConfig config) {
        this.config = config;
    }

    public HttpProxyHandlerToPublisher createProxyHandler(FlowPublisher<HttpObject> requestPublisher, Supplier<HttpTunnelHandler> tunnelFactory) {
        return new HttpProxyHandlerToPublisher(requestPublisher, ctx -> NettyUtils.resetHttpTunnel(ctx, tunnelFactory));
    }

    public BufferedPublisher<HttpObject> createPublisher() {
        return new BufferedPublisher<>();
    }

    public AppConfig getConfig() {
        return config;
    }
}
