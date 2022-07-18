package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.ChannelInitializerCallback;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.test.JdkTestClient;
import mezlogo.mid.netty.test.UndertowTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class AcceptanceTest {
    public static final int SERVER_PORT = 54321;
    public static final int PROXY_PORT = 54322;
    static JdkTestClient testClient;
    static UndertowTestServer testServer;
    static NettyHttpTunnelServer mid;

    @BeforeAll
    static void before_all() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        System.setProperty("jdk.httpclient.HttpClient.log", "all");
        AppConfig config = new AppConfig(true, true);

        testClient = JdkTestClient.createTestClient("localhost", SERVER_PORT, false, PROXY_PORT);
        testServer = UndertowTestServer.createTestServer();
        testServer.start(SERVER_PORT).join();
        NioEventLoopGroup group = new NioEventLoopGroup();
        var clientBootsrap = new Bootstrap().handler(new ChannelInitializerCallback(ch -> {
        })).group(group).channel(NioSocketChannel.class);
        NettyNetworkClientFunction client = new NettyNetworkClientFunction((host, port) -> NettyUtils.openChannel(clientBootsrap, host, port), config);
        AppFactory factory = new AppFactory(config);
        mid = new NettyHttpTunnelServer(NettyHttpTunnelServer.createServer(NettyHttpTunnelServer.tunnelInitializer(() -> new HttpTunnelHandler(MidUtils::uriParser, client, factory), factory), group));
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

    @Test
    void when_GET_echo_TWICE_should_handle_keep_alive() {
        var resp = testClient.get("/echo").join();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,false,0,null,CONTENT_LENGTH");

        resp = testClient.get("/echo").join();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("GET,HTTP/1.1,false,0,null,CONTENT_LENGTH");
    }
}
