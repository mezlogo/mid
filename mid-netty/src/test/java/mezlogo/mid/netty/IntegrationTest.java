package mezlogo.mid.netty;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.test.EmbeddedAppFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {
    private static final String HOST = "testhost";
    private static final int PORT = 9876;
    private static final String TESTURL = "http://" + HOST + ":" + PORT;
    EmbeddedAppFactory factory;
    EmbeddedAppFactory.EmbeddedTestClient testClient;

    static FullHttpResponse echoHandler(FullHttpRequest req) {
        return NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(req.method() + "," + req.content().readableBytes() + "," + req.uri() + "," + req.content().toString(StandardCharsets.UTF_8).trim()));
    }

    static String echoStringHandler(String msg) {
        return "echo: [" + msg + "]";
    }

    @BeforeEach
    void before_each() {
        factory = new EmbeddedAppFactory(new AppConfig(true, true));
    }

    @Nested
    @DisplayName("open raw tunnel and decrypt it with CONNECT method")
    class DecryptedRawBytesTunnelTest {
        @BeforeEach
        void before_each() {
            factory.setSocketsToDecrypt(MidUtils.isDecrypt(Arrays.asList(new HostAndPort(HOST, PORT))));
            testClient = factory.createTestClient(factory.initTestTcpServerChannel(IntegrationTest::echoStringHandler), true);
        }

        @Test
        void given_raw_bytes_echo_server_when_client_requests_CONNECT_method_should_return_publisher() {
            var actual = testClient.sendRequest(connect()).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().readableBytes()).isZero();

            testClient.turnToTcp(true);

            var responseString = testClient.sendString("hello").join();
            assertThat(responseString).isEqualTo("echo: [hello]");
        }
    }

    @Nested
    @DisplayName("open raw tunnel with CONNECT method")
    class RawBytesTunnelTest {
        @BeforeEach
        void before_each() {
            testClient = factory.createTestClient(factory.initTestTcpServerChannel(IntegrationTest::echoStringHandler), false);
        }

        @Test
        void given_raw_bytes_echo_server_when_client_requests_CONNECT_method_should_return_publisher() {
            var actual = testClient.sendRequest(connect()).join();
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
            testClient = factory.createTestClient(factory.initTestHttpServerChannel(IntegrationTest::echoHandler), false);
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_GET_request_without_modifications() {
            var actual = testClient.sendRequest(get("/echo?param=value")).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("GET,0,/echo?param=value,");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_with_DELAY() {
            var actual = testClient.sendRequest(post("/echo", "Hello, server!")).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_without_modifications() {
            var actual = testClient.sendRequest(post("/echo", "Hello, server!")).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
        }

        @Test
        void given_ECHO_server_when_client_calls_it_TWICE_should_handle_KEEP_ALIVE() {
            var actual = testClient.sendRequest(post("/echo","first")).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,5,/echo,first");

            actual = testClient.sendRequest(post("/echo","second")).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
            assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,6,/echo,second");
        }

    }

    private FullHttpRequest get(String uri) {
        return NettyUtils.createRequest(TESTURL + uri, HOST, HttpMethod.GET, Optional.empty());
    }

    private FullHttpRequest connect() {
        return NettyUtils.createRequest(HOST + ":" + PORT, HOST, HttpMethod.CONNECT, Optional.empty());
    }

    private FullHttpRequest post(String uri, String body) {
        return NettyUtils.createRequest(TESTURL + uri, HOST, HttpMethod.POST, Optional.of(body));
    }
}
