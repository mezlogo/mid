package mezlogo.mid.netty;

import io.netty.handler.codec.http.HttpObject;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;

import java.util.concurrent.Flow;

public class AppFactory {
    public HttpProxyHandlerToPublisher createProxyHandler(Flow.Publisher<HttpObject> requestPublisher) {
        return new HttpProxyHandlerToPublisher(requestPublisher);
    }

    public BufferedPublisher<HttpObject> createPublisher() {
        return new BufferedPublisher<>();
    }
}
