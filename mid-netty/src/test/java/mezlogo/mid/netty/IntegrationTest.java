package mezlogo.mid.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.test.SyncEmbeddedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {
    SyncEmbeddedClient client;

    static FullHttpResponse echoHandler(FullHttpRequest req) {
        return NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(req.method() + "," + req.content().readableBytes() + "," + req.uri() + "," + req.content().toString(StandardCharsets.UTF_8).trim()));
    }

    private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    boolean MADE_10_ms_delay = false;

    @BeforeEach
    void before_each() {
        //1 proxyClient to testServerGreet
        AppConfig config = new AppConfig(true, true);

        NettyNetworkClient networkClient = new NettyNetworkClientFunction((host, port) -> {
            var proxyClient = SyncEmbeddedClient.createClientWithEmbeddedHttpServer(IntegrationTest::echoHandler);
            var proxyClientChannel = proxyClient.getClient();
            proxyClientChannel.pipeline().remove(HttpClientCodec.class);
            if (MADE_10_ms_delay) {
                CompletableFuture<Channel> future = new CompletableFuture<>();
                executor.schedule(() -> future.complete(proxyClientChannel), 10, TimeUnit.MILLISECONDS);
                return future;
            } else {
                return CompletableFuture.completedFuture(proxyClientChannel);
            }
        }, config);

        //2 testClient to proxyServer
        AppFactory appFactory = new AppFactory(config);

        var serverChannel = new EmbeddedChannel();
        serverChannel.pipeline()
                .addLast("http-server-codec", new HttpServerCodec())
                .addLast("http-tunnel", new HttpTunnelHandler(MidUtils::uriParser, networkClient, appFactory));

        client = SyncEmbeddedClient.createClientWithRawChannel(serverChannel);
    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_GET_request_without_modifications() {
        var actual = client.request(NettyUtils.createRequest("http://realserver:80/echo?param=value", "realserver", HttpMethod.GET, Optional.empty()));
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("GET,0,/echo?param=value,");
    }

//    @Test
//    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_with_DELAY() {
//        MADE_10_ms_delay = true;
//        var actual = client.request(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!")));
//        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
//        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
//    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_without_modifications() {
        var actual = client.request(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!")));
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo,Hello, server!");
    }

    @Test
    void given_ECHO_server_when_client_calls_it_TWICE_should_handle_KEEP_ALIVE() {
        var actual = client.request(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("first")));
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,5,/echo,first");

        actual = client.request(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("second")));
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        assertThat(actual.content().toString(StandardCharsets.UTF_8)).isEqualTo("POST,6,/echo,second");
    }
}
