package mezlogo.mid.netty;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.BufferedPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpTunnelHandlerTest {
    NettyNetworkClient client;

    HttpTunnelHandlerFactory factory;

    @Test
    void when_uri_does_not_contain_HOST_return_400() {
        template(helper -> {
            var req = NettyTestUtils.createRequestForProxy("http://:8080", "localhost", HttpMethod.GET, Optional.empty());
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 400, "Supported only http scheme and positive number as port: 'http://:8080/'");
        });
    }
    @Test
    void when_uri_does_not_contain_PORT_return_400() {
        template(helper -> {
            var req = NettyTestUtils.createRequestForProxy("http://localhost", "localhost", HttpMethod.GET, Optional.empty());
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 400, "Supported only http scheme and positive number as port: 'http://localhost/'");
        });
    }

    @Test
    void when_uri_does_not_contain_HTTP_scheme_return_400() {
        template(helper -> {
            var req = NettyTestUtils.createRequestForProxy("not_a_uri", "localhost", HttpMethod.GET, Optional.empty());
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 400, "Supported only http scheme and positive number as port: 'not_a_uri'");
        });
    }

    @Test
    void when_target_url_unreachable_should_return_503() {
        template(helper -> {
            when(client.openHttpConnection(any(), anyInt(), any())).thenReturn(CompletableFuture.failedFuture(new LightweightException("")));
            var req = NettyTestUtils.createRequestForProxy("http://localhost:65432", "localhost", HttpMethod.GET, Optional.empty());
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 503, "Unreachable target url: 'http://localhost:65432/'");
        });
    }

    @Test
    void when_GET_requested_should_return_hello() {
        var response = new BufferedPublisher<HttpObject>();
        template(helper -> {
            when(client.openHttpConnection(any(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(response));
            var req = NettyTestUtils.createRequestForProxy("http://localhost:8080", "localhost", HttpMethod.GET, Optional.empty());
            response.next(NettyTestUtils.createResponse(HttpResponseStatus.OK, Optional.of("hello")));
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 200, "hello");
        });
    }

    @Test
    void when_POST_requested_should_return_hello() {
        var response = new BufferedPublisher<HttpObject>();
        template(helper -> {
            when(client.openHttpConnection(any(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(response));
            var req = NettyTestUtils.createRequestForProxy("http://localhost:8080", "localhost", HttpMethod.POST, Optional.of("this is request"));
            response.next(NettyTestUtils.createResponse(HttpResponseStatus.OK, Optional.of("hello")));
            var resp = helper.sendHttpRequest(req);
            assertResp(resp, 200, "hello");
        });
    }

    void template(Consumer<NettyTestUtils.NettyTestAdapter> testBody) {
        client = mock(NettyNetworkClient.class);
        factory = mock(HttpTunnelHandlerFactory.class);

        var channel = NettyTestUtils.createEmbeddedHttpServer();
        channel.pipeline().addLast("http-tunnel-handler", new HttpTunnelHandler(client, factory));
        var testAdapter = new NettyTestUtils.LocalNettyTestAdapter(channel);
        testBody.accept(testAdapter);
    }

    public void assertResp(FullHttpResponse actual, int code, String body) {
        assertEquals(code, actual.status().code());
        if (null != body) {
            assertEquals(body, actual.content().toString(Charset.defaultCharset()));
        }
    }
}