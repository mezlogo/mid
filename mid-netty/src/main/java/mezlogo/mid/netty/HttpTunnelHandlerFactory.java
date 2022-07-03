package mezlogo.mid.netty;

import io.netty.handler.codec.http.HttpObject;
import mezlogo.mid.api.model.FlowPublisher;

public interface HttpTunnelHandlerFactory {
    HttpRequestInboundProxy createHttpRequestInboundProxy(FlowPublisher<HttpObject> target);
}
