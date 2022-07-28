package mezlogo.mid.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.LightweightException;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.test.NettyTestHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpTunnelHandlerTest {
    EmbeddedChannel channel;
    Function<String, Optional<URI>> uriParser;
    Function<String, Optional<HostAndPort>> socketParser;
    NettyNetworkClient client;
    AppFactory factory;
    BufferedPublisher<HttpObject> requestHttpPublisher;
    BufferedPublisher<ByteBuf> requestBytesPublisher;

    static FullHttpRequest createProxyGet() {
        DefaultFullHttpRequest result = NettyTestHelpers.createRequestForProxy("http://localhost:8080/greet?name=Bob", "localhost", HttpMethod.GET, Optional.empty());
        return result;
    }

    static FullHttpRequest createProxyConnect() {
        DefaultFullHttpRequest result = NettyTestHelpers.createRequestForProxy("localhost:8080", "localhost", HttpMethod.CONNECT, Optional.empty());
        return result;
    }

    @BeforeEach
    void before() {
        uriParser = mock(Function.class);
        socketParser = mock(Function.class);
        client = mock(NettyNetworkClient.class);
        factory = mock(AppFactory.class);
        channel = NettyTestHelpers.createEmbeddedHttpServer();
        channel.pipeline().addLast("http-tunnel-handler", new HttpTunnelHandler(uriParser, socketParser, client, factory));
        requestHttpPublisher = mock(BufferedPublisher.class);
        requestBytesPublisher = mock(BufferedPublisher.class);

        when(factory.createHttpPublisher()).thenReturn(requestHttpPublisher);
        when(factory.createBytebufPublisher()).thenReturn(requestBytesPublisher);
        when(factory.createServerProxyHandler(any())).thenReturn(new PublishHttpObjectHandler(requestHttpPublisher));
        when(factory.createServerProxyBytesHandler(any())).thenReturn(new PublishBytebufHandler(requestBytesPublisher));
    }

    @Test
    void on_GET_when_uri_is_malformed_should_return_400() {
        when(uriParser.apply(anyString())).thenReturn(Optional.empty());

        channel.writeInbound(createProxyGet());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("Tunnel can not parse uri: 'http://localhost:8080/greet?name=Bob'");
    }

    @Test
    void on_CONNECT_when_uri_is_malformed_should_return_400() {
        when(socketParser.apply(anyString())).thenReturn(Optional.empty());

        channel.writeInbound(createProxyConnect());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("Tunnel can not parse uri: 'localhost:8080'");
    }

    @Test
    void on_CONNECT_when_target_unreachable_should_return_503() {
        when(socketParser.apply(anyString())).thenReturn(Optional.of(new HostAndPort("unreached", 80)));
        when(client.openStreamConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.failedFuture(new LightweightException("")));

        channel.writeInbound(createProxyConnect());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("socket: 'unreached:80' is unreachable");
    }

    @Test
    void on_GET_when_target_unreachable_should_return_503() {
        when(uriParser.apply(anyString())).thenReturn(Optional.of(URI.create("http://localhost:8080/greet?name=Bob")));
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.failedFuture(new LightweightException("")));

        channel.writeInbound(createProxyGet());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo("socket: 'localhost:8080' is unreachable");
    }

    @Test
    void on_CONNECT_when_request_is_ok_should_pass_to_client() {
        when(socketParser.apply(anyString())).thenReturn(Optional.of(new HostAndPort("reachable", 443)));
        Flow.Publisher<ByteBuf> responsePublisher = mock(Flow.Publisher.class);
        when(client.openStreamConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(responsePublisher));

        channel.writeInbound(createProxyConnect());
        FullHttpResponse actual = channel.readOutbound();

        assertThat(actual.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(actual.content().readableBytes()).isZero();

        ByteBuf originalSent = Unpooled.copiedBuffer("hello".getBytes(StandardCharsets.UTF_8));
        channel.writeInbound(originalSent);
        var captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(requestBytesPublisher, times(1)).next(captor.capture());
        var passedBuffer = captor.getValue();
        assertThat(passedBuffer).isNotSameAs(originalSent);
        assertThat(passedBuffer.toString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void on_GET_when_request_is_ok_should_fire_request_up() {
        when(uriParser.apply(anyString())).thenReturn(Optional.of(URI.create("http://localhost:8080/greet?name=Bob")));
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(mock(Flow.Publisher.class)));

        var captor = ArgumentCaptor.forClass(HttpObject.class);
        channel.writeInbound(createProxyGet());
        verify(requestHttpPublisher, atLeastOnce()).next(captor.capture());

        HttpObject value = captor.getAllValues().get(0);
        assertThat(value).isInstanceOf(HttpRequest.class);

        var req = (HttpRequest) value;
        assertThat(req.uri()).isEqualTo("/greet?name=Bob");
        assertThat(req.method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void when_request_does_not_contain_port_should_use_default() {
        when(uriParser.apply(anyString())).thenReturn(Optional.of(URI.create("http://localhost/greet?name=Bob")));
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(mock(Flow.Publisher.class)));

        channel.writeInbound(createProxyGet());

        verify(client, times(1)).openHttpConnection(eq("localhost"), eq(80), any());
    }
}