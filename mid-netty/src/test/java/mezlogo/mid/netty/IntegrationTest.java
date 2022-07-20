package mezlogo.mid.netty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.netty.test.IntegrationEmbeddedAppFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {
    IntegrationEmbeddedAppFactory factory;

    static FullHttpResponse echoHandler(FullHttpRequest req) {
        return NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(req.method() + "," + req.content().readableBytes() + "," + req.uri() + "," + req.content().toString(StandardCharsets.UTF_8).trim()));
    }

    @BeforeEach
    void before_each() {
        //1 testClient: is an embeddedChannel consists of http-client AND adapter of proxyServer on the other side
        //2 proxyServer: is an embeddedChannel consists of http-tunnel-server AND proxyClientFactory on the other side
        //3 proxyClient: is an embeddedChannel consists of http-tunnel-client AND adapter of testServer on the other side
        //4 testServer: is an embeddedChannel consists of http-echo-server
        factory = new IntegrationEmbeddedAppFactory(new AppConfig(true, true));

        factory.clientSupplier = () -> factory.createTestClient((host, port) -> {
            var proxyClient = factory.createPlainChannelWithHttpServer(IntegrationTest::echoHandler);
            return CompletableFuture.completedFuture(proxyClient);
        });
    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_GET_request_without_modifications() {
        var client = factory.createFullHttpClientWithTunnel();
        var actual = client.apply(NettyUtils.createRequest("http://realserver:80/echo?param=value", "realserver", HttpMethod.GET, Optional.empty())).join();
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("GET,0,/echo?param=value,");
    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_with_DELAY() {
        var executor = factory.getExecutor();
        factory.clientSupplier = () -> factory.createTestClient((host, port) -> {
            var future = new CompletableFuture<Channel>();
            executor.schedule(() -> {
                var proxyClient = factory.createPlainChannelWithHttpServer(IntegrationTest::echoHandler);
                future.complete(proxyClient);
            }, 10, TimeUnit.MILLISECONDS);
            return future;
        });
        var client = factory.createFullHttpClientWithTunnel();

        var actual = client.apply(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!"))).join();
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_without_modifications() {
        var client = factory.createFullHttpClientWithTunnel();
        var actual = client.apply(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!"))).join();
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
    }

    @Test
    void given_ECHO_server_when_client_calls_it_TWICE_should_handle_KEEP_ALIVE() {
        var client = factory.createFullHttpClientWithTunnel();

        var actual = client.apply(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("first"))).join();
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,5,/echo,first");

        actual = client.apply(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("second"))).join();
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,6,/echo,second");
    }
}
