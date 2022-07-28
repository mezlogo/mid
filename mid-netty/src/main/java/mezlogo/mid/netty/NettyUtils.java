package mezlogo.mid.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import mezlogo.mid.utils.SslFactory;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface NettyUtils {
    static ChannelFutureListener twoCallbacks(Consumer<Channel> ok, Consumer<Throwable> error) {
        return new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone() && future.isSuccess()) {
                    ok.accept(future.channel());
                } else if (null != future.cause()) {
                    error.accept(future.cause());
                } else {
                    error.accept(new LightweightException("Unssoported state for future: " + future));
                }
            }
        };
    }

    static void resetHttpTunnel(ChannelHandlerContext ctx, AppFactory factory) {
        ctx.pipeline().replace("http-server-publisher-handler", "http-tunnel-handler", factory.createHttpTunnelHandler());
    }

    static CompletableFuture<Channel> openChannel(Bootstrap bootstrap, String host, int port) {
        CompletableFuture<Channel> future = new CompletableFuture<>();
        bootstrap.connect(host, port).addListener(NettyUtils.twoCallbacks(future::complete, future::completeExceptionally));
        return future;
    }

    static ChannelFutureListener toFuture(CompletableFuture<Void> future) {
        return twoCallbacks(ch -> future.complete(null), future::completeExceptionally);
    }

    static DefaultFullHttpRequest createRequest(String uri, String host, HttpMethod method, Optional<String> body) {
        var content = body.map(it -> Unpooled.copiedBuffer(it.getBytes(StandardCharsets.UTF_8))).orElseGet(() -> Unpooled.buffer(0));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content);
        request.headers().add(HttpHeaderNames.HOST, host);
        request.headers().add(HttpHeaderNames.USER_AGENT, "curl/7.84.0");

        if (0 < content.readableBytes()) {
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        return request;
    }

    static DefaultHttpRequest createPartialRequest(String uri, HttpMethod method) {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        return request;
    }

    static DefaultFullHttpResponse createResponse(HttpResponseStatus status, Optional<String> body) {
        var content = body.map(it -> Unpooled.copiedBuffer(it.getBytes(StandardCharsets.UTF_8))).orElseGet(() -> Unpooled.buffer(0));
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);

        if (0 < content.readableBytes()) {
            response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        } else {
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0);
        }

        return response;
    }

    static SslContext serverSsl() {
        try {
            var managers = SslFactory.buildManagers();
            return SslContextBuilder.forServer(managers[0])
                    .sslProvider(SslProvider.JDK)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    static SslContext clientSsl() {
        try {
            return SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }
}
