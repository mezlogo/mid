package mezlogo.mid.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.netty.NettyUtils;
import mezlogo.mid.netty.test.NettyTestHelpers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HttpProxyHandlerToPublisherTest {
    public static void main(String[] args) {
        var ch = new EmbeddedChannel(new LoggingHandler(LogLevel.INFO), new HttpServerCodec());
        ch.writeOutbound(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of("hello")));
        ByteBuf w = ch.readOutbound();
        System.out.println(w.toString(Charset.defaultCharset()));
        ch.writeOutbound(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.empty()));
        w = ch.readOutbound();
        System.out.println(w.toString(Charset.defaultCharset()));
    }

    @Test
    void should_publish_GET_request_as_HttpRequest_and_LastHttpContent_without_content() {
        template_server(NettyUtils.createRequest("/get_example", "test_host", HttpMethod.GET, Optional.empty()), (req, last) -> {
            assertThat(req.method()).isEqualTo(HttpMethod.GET);
            assertThat(req.uri()).isEqualTo("/get_example");
            assertThat(req.headers().get("host")).isEqualTo("test_host");
            assertThat(last.content().toString(Charset.defaultCharset())).isEmpty();
        });
    }

    @Test
    void should_publish_POST_request_as_HttpRequest_and_LastHttpContent_with_content() {
        template_server(NettyUtils.createRequest("/post_example", "test_host", HttpMethod.POST, Optional.of("hello")), (req, last) -> {
            assertThat(req.method()).isEqualTo(HttpMethod.POST);
            assertThat(req.uri()).isEqualTo("/post_example");
            assertThat(req.headers().get("host")).isEqualTo("test_host");
            assertThat(last.content().toString(Charset.defaultCharset())).isEqualTo("hello");
        });
    }

    @Test
    void should_publish_200_response_as_HttpResponse_and_LastHttpContent_with_content() {
        template_client(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of("hello")), (req, last) -> {
            assertThat(req.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(last.content().toString(Charset.defaultCharset())).isEqualTo("hello");
        });
    }

    @Test
    void should_publish_503_response_as_HttpResponse_and_LastHttpContent_without_content() {
        template_client(NettyUtils.createResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, Optional.empty()), (req, last) -> {
            assertThat(req.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
            assertThat(last.content().readableBytes()).isZero();
        });
    }

    void template_client(HttpResponse response, BiConsumer<HttpResponse, LastHttpContent> callback) {
        var ch = NettyTestHelpers.createEmbeddedHttpClient();
        FlowPublisher publisher = mock(FlowPublisher.class);
        var captor = ArgumentCaptor.forClass(HttpObject.class);
        ch.pipeline().addLast(new HttpProxyHandlerToPublisher(publisher));

        ch.writeInbound(response);

        verify(publisher, atLeastOnce()).next(captor.capture());
        var args = captor.getAllValues();
        assertThat(args.get(0)).isInstanceOf(HttpResponse.class);
        assertThat(args.get(1)).isInstanceOf(LastHttpContent.class);
        callback.accept((HttpResponse) args.get(0), (LastHttpContent) args.get(1));
    }

    void template_server(HttpRequest request, BiConsumer<HttpRequest, LastHttpContent> callback) {
        var ch = NettyTestHelpers.createEmbeddedHttpServer();
        FlowPublisher<HttpObject> publisher = mock(FlowPublisher.class);
        var captor = ArgumentCaptor.forClass(HttpObject.class);
        Consumer<ChannelHandlerContext> onLast = mock(Consumer.class);
        ch.pipeline().addLast(new HttpProxyHandlerToPublisher(publisher, onLast));

        ch.writeInbound(request);

        verify(onLast, times(1)).accept(any());
        verify(publisher, atLeastOnce()).next(captor.capture());
        var args = captor.getAllValues();
        assertThat(args.get(0)).isInstanceOf(HttpRequest.class);
        assertThat(args.get(1)).isInstanceOf(LastHttpContent.class);
        callback.accept((HttpRequest) args.get(0), (LastHttpContent) args.get(1));
    }

}