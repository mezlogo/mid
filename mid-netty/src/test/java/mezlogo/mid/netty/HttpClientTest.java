package mezlogo.mid.netty;

import mezlogo.mid.api.model.BodyType;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpServer;
import mezlogo.mid.api.model.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Stream;

import static mezlogo.mid.api.model.BodyType.CHUNK;
import static mezlogo.mid.api.model.BodyType.LENGTH;
import static mezlogo.mid.api.model.BodyType.NO_BODY;
import static mezlogo.mid.api.model.HttpMethod.GET;
import static mezlogo.mid.api.model.HttpMethod.POST;
import static mezlogo.mid.api.utils.Tuple.of;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Tag("integration")
class HttpClientTest {
    public static final int PORT_HTTP = 32110;
    public static final int PORT_HTTPS = 32111;

    static NettyHttpClient client;
    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        client = new NettyHttpClient();
        client.start().join();
        server = HttpClientTestUtils.simpleServer(true);
        server.start(Arrays.asList(of(PORT_HTTP, false), of(PORT_HTTPS, true))).join();
    }

    @AfterAll
    static void afterAll() {
        server.stop(1);
        client.stop().join();
    }

    static Stream<Arguments> testdata() {
        return Stream.of(
                arguments("http://localhost:32110/greet", GET, NO_BODY, null, "hello"),
                arguments("http://localhost:32110/echo", GET, NO_BODY, "reqbody", "GET,NO_BODY,0"),
                arguments("http://localhost:32110/echo", POST, LENGTH, "reqbody", "POST,LENGTH,7"),
                arguments("http://localhost:32110/echo", POST, CHUNK, "reqbody", "POST,CHUNK,7"),
                arguments("https://localhost:32111/greet", GET, NO_BODY, null, "hello"),
                arguments("https://localhost:32111/echo", GET, NO_BODY, "reqbody", "GET,NO_BODY,0"),
                arguments("https://localhost:32111/echo", POST, LENGTH, "reqbody", "POST,LENGTH,7"),
                arguments("https://localhost:32111/echo", POST, CHUNK, "reqbody", "POST,CHUNK,7")
        );
    }

    @ParameterizedTest
    @MethodSource("mezlogo.mid.netty.HttpClientTest#testdata")
    void should_return_body_as_LENGTH(String uriAsString, HttpMethod method, BodyType type, String requestBody, String expected) {
        var uri = URI.create(uriAsString);
        int length = LENGTH == type ? requestBody.length() : -1;
        var req = HttpRequest.buildFrom(uri.getPath(), method, "localhost", "text/plain", length, type);
        var result = client.request("localhost", uri.getPort(), req, requestBody, uri.getScheme().equals("https"));
        var response = result.getKey().join();
        assertAll("should return correct http response",
                () -> assertEquals(HttpStatus.OK, response.status),
                () -> assertEquals(BodyType.LENGTH, response.type)
        );
        assertEquals(expected, result.getValue().join());
    }
}