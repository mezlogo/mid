package mezlogo.mid.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.LightweightException;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyUtils;
import mezlogo.mid.netty.test.EmbeddedAppFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HttpTunnelHandlerTest {
    private static final String HOST = "testserver";
    private static final int PORT = 9876;
    private static final String TESTURL = "http://" + HOST + ":" + PORT;
    public static final Answer<Void> DEFAULT_THROW_ANSWER = it -> {
        throw new UnsupportedOperationException(it.toString());
    };
    EmbeddedAppFactory.EmbeddedTestClient sut;
    Function<String, Optional<URI>> uriParser;
    Function<String, Optional<HostAndPort>> socketParser;
    Predicate<HostAndPort> isDecrypt;
    NettyNetworkClient client;
    AppFactory factory;
    FlowPublisher<HttpObject> requestHttpPublisher;
    Flow.Publisher<HttpObject> responseHttpPublisher;
    FlowPublisher<ByteBuf> requestBytesPublisher;
    Flow.Publisher<ByteBuf> responseBytesPublisher;

    private static FullHttpRequest get(String uri) {
        return NettyUtils.createRequest(TESTURL + uri, HOST, HttpMethod.GET, Optional.empty());
    }

    private static FullHttpRequest post(String uri, String body) {
        return NettyUtils.createRequest(TESTURL + uri, HOST, HttpMethod.POST, Optional.of(body));
    }

    private static FullHttpRequest connect() {
        return NettyUtils.createRequest(HOST + ":" + PORT, HOST, HttpMethod.CONNECT, Optional.empty());
    }

    private static void assertResponse(FullHttpResponse actual, HttpResponseStatus expectedStatus, String expectedBody) {
        assertThat(actual.status()).isEqualTo(expectedStatus);
        assertThat(actual.content().toString(Charset.defaultCharset())).isEqualTo(expectedBody);
    }

    public static void main(String[] args) {
        var m = mock(ArrayList.class);
        doAnswer(it -> {
            System.out.println(it);
            return 1;
        }).when(m).size();
        System.out.println(m.size());
    }

    @BeforeEach
    void before() {
        uriParser = mock(Function.class, DEFAULT_THROW_ANSWER);
        socketParser = mock(Function.class, DEFAULT_THROW_ANSWER);
        isDecrypt = mock(Predicate.class, DEFAULT_THROW_ANSWER);
        client = mock(NettyNetworkClient.class, DEFAULT_THROW_ANSWER);
        factory = mock(AppFactory.class, DEFAULT_THROW_ANSWER);

        requestHttpPublisher = mock(FlowPublisher.class);
        requestBytesPublisher = mock(FlowPublisher.class);
        responseBytesPublisher = mock(Flow.Publisher.class);
        responseHttpPublisher = mock(Flow.Publisher.class);

        doReturn(requestHttpPublisher).when(factory).createHttpPublisher();
        doReturn(requestBytesPublisher).when(factory).createBytebufPublisher();
        doReturn(new PublishHttpObjectHandler(requestHttpPublisher)).when(factory).createServerProxyHandler(any());
        doReturn(new PublishBytebufHandler(requestBytesPublisher)).when(factory).createServerProxyBytesHandler(any());
        doAnswer(it -> {
            var channel = (Channel) it.getArgument(0);
            channel.pipeline().replace(HttpTunnelHandler.class, "http-server-publisher-handler", new DirectPassMessage(reqObj -> {
                if (reqObj instanceof HttpObject httpObject) {
                    requestHttpPublisher.next(httpObject);
                }
            }));
            return null;
        }).when(factory).turnHttpServerToHttpProxy(any(), any());
        doAnswer(it -> {
            var channel = (Channel) it.getArgument(0);
            channel.pipeline().remove(HttpServerCodec.class);
            channel.pipeline().replace(HttpTunnelHandler.class, "bytes-server-publisher-handler", new DirectPassMessage(reqObj -> {
                if (reqObj instanceof ByteBuf buf) {
                    requestBytesPublisher.next(buf);
                }
            }));
            return null;
        }).when(factory).turnHttpServerToRawBytes(any(), anyBoolean(), any());

        sut = EmbeddedAppFactory.createHttpTestClientForUnitTesting(() -> new HttpTunnelHandler(uriParser, socketParser, isDecrypt, client, factory));
    }

    public static class DirectPassMessage extends ChannelInboundHandlerAdapter {
        private final Consumer<Object> callback;

        public DirectPassMessage(Consumer<Object> callback) {
            this.callback = callback;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            callback.accept(msg);
        }
    }

    @Nested
    @DisplayName("pass bytes to publisher on correct CONNECT method request")
    class CorrectCasesForCONNECTTest {
        @Test
        void when_encrypted_CONNECT_should_pass_everything_to_publisher() {
            doReturn(Optional.of(new HostAndPort(HOST, PORT))).when(socketParser).apply(anyString());
            doReturn(true).when(isDecrypt).test(any());
            doReturn(CompletableFuture.completedFuture(responseBytesPublisher)).when(client).openDecryptedStreamConnection(anyString(), anyInt(), any());

            var actual = sut.sendRequest(connect()).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);

            sut.turnToTcp();

            sut.sendString("Hello, server!");

            var bytesCapture = ArgumentCaptor.forClass(ByteBuf.class);
            verify(requestBytesPublisher, times(1)).next(bytesCapture.capture());
            var captured = bytesCapture.getAllValues();

            var buf = captured.get(0);
            assertThat(buf.toString(StandardCharsets.UTF_8)).isEqualTo("Hello, server!");
        }
        @Test
        void when_decrypted_CONNECT_should_pass_everything_to_publisher() {
            doReturn(Optional.of(new HostAndPort(HOST, PORT))).when(socketParser).apply(anyString());
            doReturn(false).when(isDecrypt).test(any());
            doReturn(CompletableFuture.completedFuture(responseBytesPublisher)).when(client).openStreamConnection(anyString(), anyInt(), any());

            var actual = sut.sendRequest(connect()).join();
            assertThat(actual.status()).isEqualByComparingTo(HttpResponseStatus.OK);

            sut.turnToTcp();

            sut.sendString("Hello, server!");

            var bytesCapture = ArgumentCaptor.forClass(ByteBuf.class);
            verify(requestBytesPublisher, times(1)).next(bytesCapture.capture());
            var captured = bytesCapture.getAllValues();

            var buf = captured.get(0);
            assertThat(buf.toString(StandardCharsets.UTF_8)).isEqualTo("Hello, server!");
        }
    }
    @Nested
    @DisplayName("pass http objects to publisher on correct plain HTTP request")
    class CorrectCasesForPlainHttpTest {
        @Test
        void when_plain_GET_should_pass_everything_to_publisher() {
            doReturn(Optional.of(URI.create(TESTURL + "/hello"))).when(uriParser).apply(anyString());
            doReturn(CompletableFuture.completedFuture(responseHttpPublisher)).when(client).openHttpConnection(anyString(), anyInt(), any());

            sut.sendRequest(get("/hello"));

            var httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
            verify(requestHttpPublisher, times(2)).next(httpObjectCaptor.capture());
            var captured = httpObjectCaptor.getAllValues();
            assertThat(captured.get(0)).isInstanceOf(HttpRequest.class);
            assertThat(captured.get(1)).isInstanceOf(LastHttpContent.class);

            HttpRequest req = (HttpRequest) captured.get(0);
            assertThat(req.method()).isEqualByComparingTo(HttpMethod.GET);
            assertThat(req.uri()).isEqualTo("/hello");

            LastHttpContent last = (LastHttpContent) captured.get(1);
            assertThat(last.content().readableBytes()).isZero();
        }

        @Test
        void when_plain_POST_should_pass_everything_to_publisher() {
            doReturn(Optional.of(URI.create(TESTURL + "/hello"))).when(uriParser).apply(anyString());
            doReturn(CompletableFuture.completedFuture(responseHttpPublisher)).when(client).openHttpConnection(anyString(), anyInt(), any());

            sut.sendRequest(post("/hello", "Hello, server!"));

            var httpObjectCaptor = ArgumentCaptor.forClass(HttpObject.class);
            verify(requestHttpPublisher, times(2)).next(httpObjectCaptor.capture());
            var captured = httpObjectCaptor.getAllValues();
            assertThat(captured.get(0)).isInstanceOf(HttpRequest.class);
            assertThat(captured.get(1)).isInstanceOf(LastHttpContent.class);

            HttpRequest req = (HttpRequest) captured.get(0);
            assertThat(req.method()).isEqualByComparingTo(HttpMethod.POST);
            assertThat(req.uri()).isEqualTo("/hello");

            LastHttpContent last = (LastHttpContent) captured.get(1);
            assertThat(last.content().toString(StandardCharsets.UTF_8)).isEqualTo("Hello, server!");
        }
    }

    @Nested
    @DisplayName("error cases")
    class ErrorHttpResponsesTest {
        @Test
        void when_bad_uri_for_GET_should_return_400() {
            doReturn(Optional.empty()).when(uriParser).apply(anyString());
            var actual = sut.sendRequest(get("/")).join();
            assertResponse(actual, HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: 'http://testserver:9876/'");
        }

        @Test
        void when_bad_uri_for_CONNECT_should_return_400() {
            doReturn(Optional.empty()).when(socketParser).apply(anyString());
            var actual = sut.sendRequest(connect()).join();
            assertResponse(actual, HttpResponseStatus.BAD_REQUEST, "Tunnel can not parse uri: 'testserver:9876'");
        }

        @Test
        void when_target_unreachable_for_ENCRYPTED_CONNECT_should_return_503() {
            doReturn(Optional.of(new HostAndPort(HOST, PORT))).when(socketParser).apply(anyString());
            doReturn(requestBytesPublisher).when(factory).createBytebufPublisher();
            doReturn(false).when(isDecrypt).test(any());
            doReturn(CompletableFuture.failedFuture(new LightweightException("can not connect to target"))).when(client).openStreamConnection(anyString(), anyInt(), any());

            var actual = sut.sendRequest(connect()).join();
            assertResponse(actual, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: 'testserver:9876' is unreachable");
        }

        @Test
        void when_target_unreachable_for_DECRYPTED_CONNECT_should_return_503() {
            doReturn(Optional.of(new HostAndPort(HOST, PORT))).when(socketParser).apply(anyString());
            doReturn(requestBytesPublisher).when(factory).createBytebufPublisher();
            doReturn(true).when(isDecrypt).test(any());
            doReturn(CompletableFuture.failedFuture(new LightweightException("can not connect to target"))).when(client).openDecryptedStreamConnection(anyString(), anyInt(), any());

            var actual = sut.sendRequest(connect()).join();
            assertResponse(actual, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: 'testserver:9876' is unreachable");
        }

        @Test
        void when_target_unreachable_for_GET_should_return_503() {
            doReturn(Optional.of(URI.create(TESTURL))).when(uriParser).apply(anyString());
            doReturn(requestHttpPublisher).when(factory).createHttpPublisher();
            doReturn(CompletableFuture.failedFuture(new LightweightException("can not connect to target"))).when(client).openHttpConnection(anyString(), anyInt(), any());

            var actual = sut.sendRequest(get("/hello")).join();
            assertResponse(actual, HttpResponseStatus.SERVICE_UNAVAILABLE, "socket: 'testserver:9876' is unreachable");
        }

    }
}