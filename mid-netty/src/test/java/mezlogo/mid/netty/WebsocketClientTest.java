package mezlogo.mid.netty;

import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.BodySubscriber;
import mezlogo.mid.api.model.HttpBuffer;
import mezlogo.mid.api.model.HttpServer;
import mezlogo.mid.api.utils.Publishers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static mezlogo.mid.api.utils.Tuple.of;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Tag("integration")
class WebsocketClientTest {
    public static final int PORT_HTTP = 32115;
    public static final int PORT_HTTPS = 32116;
    static NettyHttpClient client;
    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        server = HttpClientTestUtils.simpleServer(true);
        server.start(Arrays.asList(of(PORT_HTTP, false), of(PORT_HTTPS, true))).join();
        client = new NettyHttpClient();
        client.start().join();
    }

    @AfterAll
    static void afterAll() {
        server.stop(1);
        client.stop().join();
    }

    static Stream<Arguments> testdata() {
        return Stream.of(
                arguments("ws://localhost:32115/websocket_greet", emptyList(), singletonList("hello")),
                arguments("wss://localhost:32116/websocket_greet", emptyList(), singletonList("hello")),
                arguments("ws://localhost:32115/websocket_echo", singletonList("hello"), singletonList("echo: [hello]")),
                arguments("wss://localhost:32116/websocket_echo", singletonList("hello"), singletonList("echo: [hello]"))
        );
    }

    @ParameterizedTest
    @Timeout(1)
    @MethodSource("mezlogo.mid.core.WebsocketClientTest#testdata")
    void should_send_messages_by_websocket(String uriAsString, List<String> publish, List<String> expected) {
        var uri = URI.create(uriAsString);
        BodyPublisher body = Publishers.fromList(publish);
        var incomingWebsocket = client.openWebsocket(uri.getHost(), uri.getPort(), uri.getPath(), body, "wss".equals(uri.getScheme()))
                .join();
        var subscriber = new GetAllMessageOnCloseSubscriber();
        incomingWebsocket.subscribe(subscriber);
        List<String> actuals = subscriber.future.join();
        assertLinesMatch(expected, actuals);
    }

    public static class GetAllMessageOnCloseSubscriber implements BodySubscriber {
        public final CompletableFuture<List<String>> future = new CompletableFuture<>();
        private final List<String> buffer = new ArrayList<>();

        @Override
        public void onNext(HttpBuffer item) {
            buffer.add(item.asString());
        }

        @Override
        public void onComplete() {
            future.complete(buffer);
        }
    }

}