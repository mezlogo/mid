package mezlogo.mid.netty;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

public class HttpTunnelServerIntegrationTest {

    public static final int TARGET_PORT = 54321;
    public static final int PROXY_PORT = 54322;

    static UndertowHttpServerUtils.UndertowTestHttpServer testServer;
    static JdkHttpClientUtils.JdkTestHttpClient testClient;
    static NettyHttpTunnelServer sut;

    @BeforeAll
    static void before_all() {
        testServer = UndertowHttpServerUtils.createDefaultTestServer();
        testServer.start(TARGET_PORT).join();

        sut = new NettyHttpTunnelServer(NettyHttpTunnelServer.createServer(NettyHttpTunnelServer.tunnelInitializer()));
        sut.bind(PROXY_PORT).start().join();

        testClient = JdkHttpClientUtils.createDefaultTestClient(PROXY_PORT);
    }

    public static String testUrl(String uri) {
        return "http://localhost:" + TARGET_PORT + uri;
    }

    @Test
    void should_return_empty_status_OK() {
        HttpResponse<String> response = testClient.get(testUrl("/not_found")).join();
        Assertions.assertEquals(404, response.statusCode());
        Assertions.assertEquals("NOT FOUND: /not_found", response.body());
    }

    @AfterAll
    static void after_all() {
        testServer.stop().join();
    }
}
