package mezlogo.mid.netty;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static mezlogo.mid.api.utils.Tuple.of;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Tag("integration")
@Disabled
class WebsocketServerTest {
    public static final int PORT_HTTP = 32125;
    public static final int PORT_HTTPS = 32126;
    static NettyHttpServer server;

    @BeforeAll
    static void beforeAll() {
        server = HttpServerTestUtils.simpleServer(true);
        server.start(Arrays.asList(of(PORT_HTTP, false), of(PORT_HTTPS, true))).join();
    }

    @AfterAll
    static void afterAll() {
        server.stop(1);
    }

    static Stream<Arguments> testdata() {
        return Stream.of(
                arguments("ws://localhost:32125/websocket_greet", emptyList(), singletonList("hello")),
                arguments("wss://localhost:32126/websocket_greet", emptyList(), singletonList("hello")),
                arguments("ws://localhost:32125/websocket_echo", singletonList("hello"), singletonList("echo: [hello]")),
                arguments("wss://localhost:32126/websocket_echo", singletonList("hello"), singletonList("echo: [hello]"))
        );
    }

    @ParameterizedTest
    @Timeout(1)
    @MethodSource("mezlogo.mid.core.WebsocketClientTest#testdata")
    void should_send_messages_by_websocket(String uriAsString, List<String> publish, List<String> expected) {
        var uri = URI.create(uriAsString);
        var res = HttpServerTestUtils.callWebsocket(uri, publish);
        res.getKey().sendClose(1000, "OK");
        List<String> actuals = res.getValue().futureClose.join();
        assertLinesMatch(expected, actuals);
    }
}