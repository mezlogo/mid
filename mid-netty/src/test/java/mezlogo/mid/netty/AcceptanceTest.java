package mezlogo.mid.netty;

import mezlogo.mid.netty.test.JdkTestClient;
import mezlogo.mid.netty.test.UndertowTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AcceptanceTest {
    public static final int SERVER_PORT = 54321;
    public static final int PROXY_PORT = 54322;
    static JdkTestClient testClient;
    static UndertowTestServer testServer;
    static NettyHttpTunnelServer mid;

    @BeforeAll
    static void before_all() {
        testClient = JdkTestClient.createTestClient("localhost", SERVER_PORT, false, PROXY_PORT);
        testServer = UndertowTestServer.createTestServer();
        testServer.start(SERVER_PORT).join();
        mid = NettyHttpTunnelServer.createHttpTunnelServer();
        mid.bind(PROXY_PORT).start().join();
    }

    @AfterAll
    static void after_all() {
        testServer.stop().join();
    }

    @Test
    void when_call_greet_should_extract_query_param() {
        var resp = testClient.get("/greet?name=Bob").join();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("Hello, Bob!");
    }

    @Test
    void when_call_status_should_return_503() {
        var resp = testClient.get("/status?code=503").join();
        assertThat(resp.statusCode()).isEqualTo(503);
        assertThat(resp.body()).isEmpty();
    }

    @Test
    void when_POST_echo_should_return_info_about_request() {
        var resp = testClient.post("/echo", "payload").join();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("POST,HTTP/1.1,false,7,null,CONTENT_LENGTH,7");
    }

    @Test
    void when_GET_echo_should_return_info_about_request() {
        var resp = testClient.get("/echo").join();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,false,0,null,CONTENT_LENGTH");
    }
}
