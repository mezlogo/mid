package mezlogo.mid.netty.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class NettyTestHelpers {
    public static final String CURL_USER_AGENT_HEADER_VALUE = "curl/7.83.1";
    public static final String HTTP_HEADER_PROXY_CONNECTION = "Proxy-Connection";

    public static CombinedChannelDuplexHandler<RequestToBytesInboundHandler, BytesToResponseOutboundHandler> createRewire(EmbeddedChannel client) {
        return new CombinedChannelDuplexHandler<>(new RequestToBytesInboundHandler(client), new BytesToResponseOutboundHandler(client));
    }

    public static DefaultFullHttpRequest createConnectRequest(String url) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        request.headers().add(HTTP_HEADER_PROXY_CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        request.headers().add(HttpHeaderNames.USER_AGENT, CURL_USER_AGENT_HEADER_VALUE);
        request.headers().add(HttpHeaderNames.HOST, url);
        return request;
    }

    public static DefaultFullHttpResponse createResponse(HttpResponseStatus status, Optional<String> body) {
        var content = body.map(it -> Unpooled.copiedBuffer(it.getBytes(StandardCharsets.UTF_8))).orElseGet(() -> Unpooled.buffer(0));
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);

        if (0 < content.readableBytes()) {
            response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        return response;
    }

    public static DefaultFullHttpRequest createRequestForProxy(String uri, String host, HttpMethod method, Optional<String> body) {
        var content = body.map(it -> Unpooled.copiedBuffer(it.getBytes(StandardCharsets.UTF_8))).orElseGet(() -> Unpooled.buffer(0));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content);

        request.headers().add(HTTP_HEADER_PROXY_CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .add(HttpHeaderNames.USER_AGENT, CURL_USER_AGENT_HEADER_VALUE)
                .add(HttpHeaderNames.HOST, host);

        if (0 < content.readableBytes()) {
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        return request;
    }

    public static EmbeddedChannel createEmbeddedHttpClient() {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        var rewire = createRewire(new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(1024)));
        embeddedChannel.pipeline()
                .addLast("rewire-http-server", rewire)
                .addLast("http-client-codec", new HttpClientCodec());
        return embeddedChannel;
    }

    public static EmbeddedChannel createEmbeddedHttpServer() {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        var rewire = createRewire(new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024)));
        embeddedChannel.pipeline()
                .addLast("rewire-http-client", rewire)
                .addLast("http-server-codec", new HttpServerCodec());
        return embeddedChannel;
    }

    public static class RequestToBytesInboundHandler extends ChannelInboundHandlerAdapter {
        private final EmbeddedChannel channel;

        public RequestToBytesInboundHandler(EmbeddedChannel channel) {
            this.channel = channel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpObject httpObject) {
                channel.writeOutbound(httpObject);
                channel.outboundMessages().forEach(ctx::fireChannelRead);
            } else {
                super.channelRead(ctx, msg);
            }
        }
    }

    public static class BytesToResponseOutboundHandler extends ChannelOutboundHandlerAdapter {
        private final EmbeddedChannel channel;

        public BytesToResponseOutboundHandler(EmbeddedChannel channel) {
            this.channel = channel;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf buf) {
                channel.writeInbound(buf);
                channel.inboundMessages().forEach(it -> ctx.write(it, promise));
            } else {
                super.write(ctx, msg, promise);
            }
        }
    }
}
