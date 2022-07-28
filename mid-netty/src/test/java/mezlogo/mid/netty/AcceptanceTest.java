package mezlogo.mid.netty;

import mezlogo.mid.netty.test.JdkTestClient;
import mezlogo.mid.netty.test.UndertowTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class AcceptanceTest {
    public static final int SERVER_HTTP_PORT = 54321;
    public static final int SERVER_HTTPS_PORT = 54320;
    public static final int PROXY_PORT = 54322;
    static JdkTestClient httpTestClient;
    static JdkTestClient httpsTestClient;
    static UndertowTestServer testServer;
    static NettyHttpTunnelServer mid;

    @BeforeAll
    static void before_all() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        System.setProperty("jdk.httpclient.HttpClient.log", "all");

        httpTestClient = JdkTestClient.createTestClient("localhost", SERVER_HTTP_PORT, false, PROXY_PORT);
        httpsTestClient = JdkTestClient.createTestClient("localhost", SERVER_HTTPS_PORT, true, PROXY_PORT);

        testServer = UndertowTestServer.createTestServer();
        testServer.start(new UndertowTestServer.PortAndSll(SERVER_HTTP_PORT, false), new UndertowTestServer.PortAndSll(SERVER_HTTPS_PORT, true)).join();

        var factory = new AppFactory(new AppConfig(true, true));
        mid = factory.getServer();
        mid.bind(PROXY_PORT).start().join();
    }

    @AfterAll
    static void after_all() {
        testServer.stop().join();
        mid.stop().join();
    }

    @Nested
    @DisplayName("tls https acceptance test")
    class AcceptanceTestSecuredHttps extends AcceptanceTestTemplate {
        @Override
        JdkTestClient getTestClient() {
            return httpsTestClient;
        }
    }

    @Nested
    @DisplayName("plain http acceptance test")
    class AcceptanceTestPlainHttp extends AcceptanceTestTemplate {
        @Override
        JdkTestClient getTestClient() {
            return httpTestClient;
        }
    }

    abstract static class AcceptanceTestTemplate {
        abstract JdkTestClient getTestClient();

        @Test
        void when_call_greet_should_extract_query_param() {
            var resp = getTestClient().get("/greet?name=Bob").join();
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).isEqualTo("Hello, Bob!");
        }

        @Test
        void when_call_status_should_return_503() {
            var resp = getTestClient().get("/status?code=503").join();
            assertThat(resp.statusCode()).isEqualTo(503);
            assertThat(resp.body()).isEmpty();
        }

        @Test
        void when_POST_echo_should_return_info_about_request() {
            var resp = getTestClient().post("/echo", "payload").join();
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).isEqualTo("POST,HTTP/1.1,7,text/plain,CONTENT_LENGTH,7");
        }

        @Test
        void when_GET_echo_should_return_info_about_request() {
            var resp = getTestClient().get("/echo").join();
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,0,null,CONTENT_LENGTH");
        }

        @Test
        void when_GET_echo_TWICE_should_handle_keep_alive() {
            var resp = getTestClient().get("/echo").join();
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,0,null,CONTENT_LENGTH");

            resp = getTestClient().get("/echo").join();
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,0,null,CONTENT_LENGTH");
        }
    }
}
