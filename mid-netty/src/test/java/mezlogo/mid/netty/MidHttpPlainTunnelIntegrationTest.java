package mezlogo.mid.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import mezlogo.mid.api.model.SubscriberToCallback;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.test.SyncEmbeddedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MidHttpPlainTunnelIntegrationTest {
    SyncEmbeddedClient client;

    @BeforeEach
    void before_each() {
        //1 proxyClient to testServerGreet
        var proxyClient = SyncEmbeddedClient.createClientWithEmbeddedHttpServer(req -> NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of(req.method() + "," + req.content().readableBytes() + "," + req.uri())));
        var proxyClientChannel = proxyClient.getClient();
        var requestSubscriber = new SubscriberToCallback<HttpObject>(proxyClientChannel::writeOutbound, () -> {
        });

        //2 testClient to proxyServer
        NettyNetworkClient networkClient = mock(NettyNetworkClient.class);
        when(networkClient.openHttpConnection(anyString(), anyInt(), any())).thenAnswer(AdditionalAnswers.answer(((String host, Integer port, Flow.Publisher<HttpObject> requestPublisher) -> {
            requestPublisher.subscribe(requestSubscriber);
            return CompletableFuture.completedFuture(proxyClient.openStream());
        })));
        AppFactory appFactory = new AppFactory();

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
        ByteBuf content = actual.content();
        assertThat(content.toString(StandardCharsets.UTF_8)).isEqualTo("GET,0,/echo?param=value");
    }

    @Test
    void given_ECHO_server_when_client_calls_it_PROXY_should_pass_POST_request_without_modifications() {
        var actual = client.request(NettyUtils.createRequest("http://realserver:80/echo", "realserver", HttpMethod.POST, Optional.of("Hello, server!")));
        assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);
        ByteBuf content = actual.content();
        assertThat(content.toString(StandardCharsets.UTF_8)).isEqualTo("POST,14,/echo");
    }
}
