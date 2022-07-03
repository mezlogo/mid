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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class HttpRequestInboundProxyTest {

    @Test
    void should_proxy_POST_message() {
        template((requestConsumer, proxyPublisher) -> {
            var req = NettyUtils.createRequest("/echo", "localhost", HttpMethod.GET, Optional.of("hello"));
            requestConsumer.accept(req);
            List<HttpObject> buffer = proxyPublisher.getBuffer();
            Assertions.assertInstanceOf(HttpRequest.class, buffer.get(0));
            HttpRequest actualReq = (HttpRequest) buffer.get(0);
            Assertions.assertEquals("/echo", actualReq.uri());
            Assertions.assertInstanceOf(HttpContent.class, buffer.get(1));
            HttpContent actualContent = (HttpContent) buffer.get(1);
            Assertions.assertEquals("hello", actualContent.content().toString(StandardCharsets.UTF_8));
        });
    }

    @Test
    void should_proxy_GET_message() {
        template((requestConsumer, proxyPublisher) -> {
            var req = NettyUtils.createRequest("/echo", "localhost", HttpMethod.GET, Optional.empty());
            requestConsumer.accept(req);
            List<HttpObject> buffer = proxyPublisher.getBuffer();
            Assertions.assertInstanceOf(HttpRequest.class, buffer.get(0));
            HttpRequest actualReq = (HttpRequest) buffer.get(0);
            Assertions.assertEquals("/echo", actualReq.uri());
            Assertions.assertInstanceOf(HttpContent.class, buffer.get(1));
            HttpContent actualContent = (HttpContent) buffer.get(1);
            Assertions.assertEquals(0, actualContent.content().readableBytes());
        });
    }

    void template(BiConsumer<Consumer<FullHttpRequest>, BufferedPublisher<HttpObject>> testBody) {
        var channel = NettyTestUtils.createEmbeddedHttpServer();
        var data = new BufferedPublisher<HttpObject>();
        channel.pipeline().addLast("http-server-proxy-handler", new HttpRequestInboundProxy(data));
        testBody.accept(channel::writeInbound, data);
    }
}