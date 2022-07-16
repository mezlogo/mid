package mezlogo.mid.netty;

import io.netty.handler.codec.http.HttpObject;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;

public class AppFactory {
    public HttpProxyHandlerToPublisher createProxyHandler(FlowPublisher<HttpObject> requestPublisher) {
        return new HttpProxyHandlerToPublisher(requestPublisher);
    }

    public BufferedPublisher<HttpObject> createPublisher() {
        return new BufferedPublisher<>();
    }
}
