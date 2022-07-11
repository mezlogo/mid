package mezlogo.mid.netty.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.LightweightException;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.test.NettyTestHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpTunnelHandlerTest {
    EmbeddedChannel channel;
    Function<String, Optional<URI>> uriParser;
    NettyNetworkClient client;

    static FullHttpRequest createProxyGet() {
        DefaultFullHttpRequest result = NettyTestHelpers.createRequestForProxy("http://localhost:8080/greet?name=Bob", "localhost", HttpMethod.GET, Optional.empty());
        return result;
    }

    @BeforeEach
    void before() {
        uriParser = mock(Function.class);
        client = mock(NettyNetworkClient.class);
        channel = NettyTestHelpers.createEmbeddedHttpServer();
        channel.pipeline().addLast("http-tunnel-handler", new HttpTunnelHandler(uriParser, client, mock(AppFactory.class)));
    }

    @Test
    void when_uri_is_malformed_should_return_400() {
        when(uriParser.apply(anyString())).thenReturn(Optional.empty());

        channel.writeInbound(createProxyGet());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("Tunnel can not parse uri: 'http://localhost:8080/greet?name=Bob'");
    }

    @Test
    void when_target_unreachable_should_return_503() {
        when(uriParser.apply(anyString())).thenReturn(Optional.of(URI.create("http://localhost:8080/greet?name=Bob")));
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.failedFuture(new LightweightException("")));

        channel.writeInbound(createProxyGet());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("socket: 'localhost:8080' is unreachable");
    }

    @Test
    void when_request_is_ok_should_fire_request_up() {
        when(uriParser.apply(anyString())).thenReturn(Optional.of(URI.create("http://localhost:8080/greet?name=Bob")));
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(mock(Flow.Publisher.class)));

        channel.writeInbound(createProxyGet());
        HttpRequest req = channel.readInbound();

        assertThat(req.uri()).isEqualTo("/greet?name=Bob");
        assertThat(req.method()).isEqualTo(HttpMethod.GET);
    }
}