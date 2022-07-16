package mezlogo.mid.netty.test;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;

import java.util.concurrent.Flow;
import java.util.function.Function;

public class SyncEmbeddedClient {

    private final EmbeddedChannel client;

    public SyncEmbeddedClient(EmbeddedChannel client) {
        this.client = client;
    }

    public static SyncEmbeddedClient createClientWithEmbeddedHttpServer(Function<FullHttpRequest, FullHttpResponse> serverHandler) {
        var serverChannel = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(1024), new NettySyncHttpHandler(serverHandler));
        var result = new SyncEmbeddedClient(new EmbeddedChannel(new NettyOutboundAdapter(serverChannel), new HttpClientCodec()));
        return result;
    }

    public static SyncEmbeddedClient createClientWithRawChannel(EmbeddedChannel serverChannel) {
        var result = new SyncEmbeddedClient(new EmbeddedChannel(new NettyOutboundAdapter(serverChannel), new HttpClientCodec()));
        return result;
    }

    public FullHttpResponse request(FullHttpRequest req) {
        client.pipeline().addLast("http-aggregator", new HttpObjectAggregator(1024));
        client.writeOutbound(req);
        FullHttpResponse response = client.readInbound();
        return response;
    }

    public Flow.Publisher<HttpObject> openStream() {
        var result = new BufferedPublisher<HttpObject>();
        client.pipeline().addLast("adapter", new HttpProxyHandlerToPublisher(result));
        return result;
    }

    public EmbeddedChannel getClient() {
        return client;
    }
}