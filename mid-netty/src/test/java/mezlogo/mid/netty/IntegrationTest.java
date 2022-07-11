package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import mezlogo.mid.api.model.BufferedPublisher;
import mezlogo.mid.api.model.FlowPublisher;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.HttpProxyHandlerToPublisher;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.test.NettyTestHelpers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IntegrationTest {
    @Test
    void on_GET_request_should_publish_two_entries() {
        template(testCtx -> {
            var req = NettyUtils.createRequest("http://realsite:80/greet?name=Bob", "realsite", HttpMethod.GET, Optional.empty());
            List<HttpObject> passesObjects  = testCtx.sendRequest(req);

            assertThat(passesObjects).hasSize(2);
            assertThat(passesObjects.get(0)).isInstanceOf(HttpRequest.class);
            assertThat(passesObjects.get(1)).isInstanceOf(LastHttpContent.class);

//            var requests = readRequests.get();
//            assertThat(requests).hasSize(2);
//
//            HttpObject reqObj = requests.get(0);
//            assertThat(reqObj).isInstanceOf(HttpRequest.class);
//            HttpRequest req = (HttpRequest) reqObj;
//            assertThat(req.uri()).isEqualTo("/greet?name=Bob");
//            assertThat(req.method()).isEqualTo(HttpMethod.GET);
//
//            HttpObject bodyObj = requests.get(1);
//            assertThat(bodyObj).isInstanceOf(HttpContent.class);
//            HttpContent body = (HttpContent) bodyObj;
//            assertThat(body.content().toString(Charset.defaultCharset())).isEmpty();
        });
    }

    /*
    - clientChannel: rewire(http-server) | http-client | http-publisher
    - serverChannel: rewire(http-client) | http-server | http-tunnel
      - vol2: rewire(http-client) | http-server | http-publisher
     */
    void template(Consumer<EmbeddedServerAndClient> callback) {
        EmbeddedChannel embeddedHttpClient = NettyTestHelpers.createEmbeddedHttpClient();
        BufferedPublisher<HttpObject> requestPublisher = new BufferedPublisher<>();
        embeddedHttpClient.pipeline().addLast(new HttpProxyHandlerToPublisher(requestPublisher));

        NettyNetworkClient client = mock(NettyNetworkClient.class);

        EmbeddedChannel embeddedHttpServer = NettyTestHelpers.createEmbeddedHttpServer();
        embeddedHttpServer.pipeline().addLast(new HttpTunnelHandler(MidUtils::uriParser, client, new AppFactory()));

        BufferedPublisher<HttpObject> responsePublisher = new BufferedPublisher<>();
        when(client.openHttpConnection(anyString(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture(responsePublisher));
        var sut = new EmbeddedServerAndClient(embeddedHttpServer, embeddedHttpClient, requestPublisher, responsePublisher);
        callback.accept(sut);
//        NettyNetworkClient client = mock(NettyNetworkClient.class);
//        var clientChannel = NettyTestHelpers.createEmbeddedHttpClient();
//        var serverChannel = NettyTestHelpers.createEmbeddedHttpServer();
//        serverChannel.pipeline()
//                .addLast("http-tunnel-handler", new HttpTunnelHandler(MidUtils::uriParser, client));
//
//        callback.consume(serverChannel::writeInbound, () -> {
//            var msgs = serverChannel.inboundMessages();
//            if (null == msgs) return Collections.emptyList();
//            return msgs.stream().map(it -> (HttpObject) it).collect(Collectors.toList());
//        });
    }

    static class EmbeddedServerAndClient {
        final EmbeddedChannel server;
        final EmbeddedChannel client;
        final BufferedPublisher<HttpObject> requestPublisher;
        final BufferedPublisher<HttpObject> responsePublisher;

        EmbeddedServerAndClient(EmbeddedChannel server, EmbeddedChannel client, BufferedPublisher<HttpObject> requestPublisher, BufferedPublisher<HttpObject> responsePublisher) {
            this.server = server;
            this.client = client;
            this.requestPublisher = requestPublisher;
            this.responsePublisher = responsePublisher;
        }

        public List<HttpObject> sendRequest(FullHttpRequest req) {
            server.writeInbound(req);
            var passes = client.inboundMessages().stream().map(it -> (HttpObject) it).collect(Collectors.toList());
            return passes;
        }
    }

    public static void main(String[] args) {
        var ch = NettyTestHelpers.createEmbeddedHttpServer();
        ch.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                ctx.writeAndFlush(NettyUtils.createResponse(HttpResponseStatus.OK, Optional.of("hello")));
            }
        });
        ch.writeInbound(NettyUtils.createRequest("/hey", "", HttpMethod.GET, Optional.empty()));
        HttpResponse resp = ch.readOutbound();
        System.out.println(resp);
    }
}
