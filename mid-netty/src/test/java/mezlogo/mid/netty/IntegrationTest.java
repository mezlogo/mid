package mezlogo.mid.netty;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.netty.test.EmbeddedAppFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {
    EmbeddedAppFactory factory;
    EmbeddedAppFactory.EmbeddedTestClient testClient;

    static FullHttpResponse echoHandler(FullHttpRequest req) {
        return NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(req.method() + "," + req.content().readableBytes() + "," + req.uri() + "," + req.content().toString(StandardCharsets.UTF_8).trim()));
    }

    static String echoStringHandler(String msg) {
        return "echo: [" + msg + "]";
    }

    @Nested
    @DisplayName("open raw tunnel with CONNECT method")
    class RawBytesTunnelTest {
        @BeforeEach
        void before_each() {
            factory = new EmbeddedAppFactory(new AppConfig(true, true));
            testClient = factory.createTestClient(factory.initTestTcpServerChannel(IntegrationTest::echoStringHandler));
        }

        @Test
        void given_raw_bytes_echo_server_when_client_requests_CONNECT_method_should_return_publisher() {
            var actual = testClient.sendRequest(NettyUtils.createRequest("realserver:80", "realsserver", HttpMethod.CONNECT, Optional.empty())).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().readableBytes()).isZero();

            testClient.turnToTcp();

            var responseString = testClient.sendString("hello").join();
            assertThat(responseString).isEqualTo("echo: [hello]");
        }
    }

    @Nested
    @DisplayName("plain http test without CONNECT method")
    class PlainHttpTest {
        @BeforeEach
        void before_each() {
            factory = new EmbeddedAppFactory(new AppConfig(true, true));
            testClient = factory.createTestClient(factory.initTestHttpServerChannel(IntegrationTest::echoHandler));
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_GET_request_without_modifications() {
            var actual = testClient.sendRequest(NettyUtils.createRequest("http://realserver:80/echo?param=value", "realserver", HttpMethod.GET, Optional.empty())).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("GET,0,/echo?param=value,");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_with_DELAY() {
            var actual = testClient.sendRequest(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!"))).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_without_modifications() {
            var actual = testClient.sendRequest(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!"))).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_TWICE_should_handle_KEEP_ALIVE() {
            var actual = testClient.sendRequest(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("first"))).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,5,/echo,first");

            actual = testClient.sendRequest(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("second"))).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,6,/echo,second");
        }

    }
}
