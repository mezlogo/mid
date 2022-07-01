package mezlogo.mid.netty;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.BufferedPublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class HttpRequestInboundProxyTest {

    @Test
    void should_proxy_GET_message() {
        template((requestConsumer, proxyPublisher) -> {
            var req = NettyUtils.createRequest("/echo", "localhost", HttpMethod.GET, Optional.empty());
            requestConsumer.accept(req);
            Assertions.assertInstanceOf(HttpRequest.class, proxyPublisher.buffer.get(0));
            Assertions.assertInstanceOf(HttpContent.class, proxyPublisher.buffer.get(1));
        });
    }

    void template(BiConsumer<Consumer<FullHttpRequest>, BufferedPublisher<HttpObject>> testBody) {
        var channel = NettyTestUtils.createEmbeddedHttpServer();
        var data = new BufferedPublisher<HttpObject>();
        channel.pipeline().addLast("http-server-proxy-handler", new HttpRequestInboundProxy(data));
        testBody.accept(channel::writeInbound, data);
    }


}