package mezlogo.mid.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NettyUtils {
    public static ChannelFutureListener toCallback(Consumer<ChannelFuture> callback) {
        return new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                callback.accept(future);
            }
        };
    }

    public static ChannelFutureListener twoCallbacks(Consumer<Channel> ok, Consumer<Throwable> error) {
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

    public static ChannelFutureListener toFuture(CompletableFuture<Void> future) {
        return twoCallbacks(ch -> future.complete(null), future::completeExceptionally);
    }

    public static DefaultFullHttpRequest createRequest(String uri, String host, HttpMethod method, Optional<String> body) {
        var content = body.map(it -> Unpooled.copiedBuffer(it.getBytes(StandardCharsets.UTF_8))).orElseGet(() -> Unpooled.buffer(0));
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content);

        if (0 < content.readableBytes()) {
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

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

}
