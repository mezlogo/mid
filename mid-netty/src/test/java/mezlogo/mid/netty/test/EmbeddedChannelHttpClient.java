package mezlogo.mid.netty.test;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class EmbeddedChannelHttpClient {

    private final EmbeddedChannel client;

    public EmbeddedChannelHttpClient(EmbeddedChannel client) {
        this.client = client;
    }

    public static EmbeddedChannelHttpClient createClientWithEmbeddedHttpServer(Function<FullHttpRequest, FullHttpResponse> serverHandler) {
        var serverChannel = new EmbeddedChannel(new LoggingHandler("mezlogo.mid.netty.test.server"), new HttpServerCodec(), new HttpObjectAggregator(1024), new NettySyncHttpHandler(serverHandler));
        var result = new EmbeddedChannelHttpClient(new EmbeddedChannel(new NettyOutboundAdapter(serverChannel), new LoggingHandler("mezlogo.mid.netty.test.client"), new HttpClientCodec()));
        return result;
    }

    public static EmbeddedChannelHttpClient createClientWithRawChannel(EmbeddedChannel serverChannel) {
        var result = new EmbeddedChannelHttpClient(new EmbeddedChannel(new NettyOutboundAdapter(serverChannel), new LoggingHandler("mezlogo.mid.netty.test.client"), new HttpClientCodec()));
        return result;
    }

    public FullHttpResponse request(FullHttpRequest req) {
        if (null == client.pipeline().get(HttpObjectAggregator.class)) {
            client.pipeline().addLast("http-aggregator", new HttpObjectAggregator(1024));
        }
        client.writeOutbound(req);
        FullHttpResponse response = client.readInbound();
        return response;
    }

    public CompletableFuture<FullHttpResponse> requestAsync(FullHttpRequest req) {
        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        if (null == client.pipeline().get(HttpObjectAggregator.class)) {
            client.pipeline().addLast("http-aggregator", new HttpObjectAggregator(1024));
        }
        if (null == client.pipeline().get(FullHttpResponseToCallback.class)) {
            client.pipeline().addLast("http-to-callback", new FullHttpResponseToCallback(future::complete));
        }
        client.writeOutbound(req);
        return future;
    }

    public EmbeddedChannel getClient() {
        return client;
    }
}