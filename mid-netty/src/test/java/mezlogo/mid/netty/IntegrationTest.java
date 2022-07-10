package mezlogo.mid.netty;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import mezlogo.mid.netty.test.NettyTestHelpers;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationTest {
    @Test
    void on_GET_request_should_publish_two_entries() {
        template((writeRequest, readRequests) -> {
            writeRequest.accept(NettyUtils.createRequest("/greet?name=Bob", "localhost", HttpMethod.GET, Optional.empty()));

            var requests = readRequests.get();
            assertThat(requests).hasSize(2);

            HttpObject reqObj = requests.get(0);
            assertThat(reqObj).isInstanceOf(HttpRequest.class);
            HttpRequest req = (HttpRequest) reqObj;
            assertThat(req.uri()).isEqualTo("/greet?name=Bob");
            assertThat(req.method()).isEqualTo(HttpMethod.GET);

            HttpObject bodyObj = requests.get(1);
            assertThat(bodyObj).isInstanceOf(HttpContent.class);
            HttpContent body = (HttpContent) bodyObj;
            assertThat(body.content().toString(Charset.defaultCharset())).isEmpty();
        });
    }

    void template(FullHttpRequest request, Consumer<List<HttpObject>> callback) {
        var serverChannel = NettyTestHelpers.createEmbeddedHttpServer();
        serverChannel.pipeline()
                .addLast("http-tunnel-handler", new HttpTunnelHandler(MidUtils::uriParser, null));

        callback.consume(serverChannel::writeInbound, () -> {
            var msgs = serverChannel.inboundMessages();
            if (null == msgs) return Collections.emptyList();
            return msgs.stream().map(it -> (HttpObject) it).collect(Collectors.toList());
        });
    }

    @FunctionalInterface
    interface ConsumeTwo<L, R> {
        void consume(L l, R r);
    }
}
