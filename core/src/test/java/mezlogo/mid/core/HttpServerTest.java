package mezlogo.mid.core;

import mezlogo.mid.api.model.BodyType;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.core.netty.NettyHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static mezlogo.mid.api.model.BodyType.CHUNK;
import static mezlogo.mid.api.model.BodyType.LENGTH;
import static mezlogo.mid.api.model.BodyType.NO_BODY;
import static mezlogo.mid.api.model.HttpMethod.GET;
import static mezlogo.mid.api.model.HttpMethod.POST;
import static mezlogo.mid.api.utils.Tuple.of;
import static mezlogo.mid.core.HttpServerTestUtils.generateHugeString;
import static mezlogo.mid.core.HttpServerTestUtils.sendAsync;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Tag("integration")
class HttpServerTest {
    public static final int PORT_HTTP = 31120;
    public static final int PORT_HTTPS = 31121;
    static NettyHttpServer server;

    @BeforeAll
    static void beforeAll() {
        server = HttpServerTestUtils.simpleServer(true);
        server.start(Arrays.asList(of(PORT_HTTP, false), of(PORT_HTTPS, true))).join();
    }

    @AfterAll
    static void afterAll() {
        server.stop(1).join();
    }


    static Stream<Arguments> testData() {
        return Stream.of(
                arguments("http://localhost:31120/greet", GET, NO_BODY, null, "hello"),
                arguments("http://localhost:31120/echo", GET, NO_BODY, null, "method: GET, type: LENGTH, bodylength: 0"),
                arguments("http://localhost:31120/echo", POST, LENGTH, "reqbody", "method: POST, type: LENGTH, bodylength: 7"),
                arguments("http://localhost:31120/echo", POST, LENGTH, generateHugeString(3000), "method: POST, type: LENGTH, bodylength: 3000"),
                arguments("http://localhost:31120/echo", POST, CHUNK, "reqbody", "method: POST, type: CHUNK, bodylength: 7"),
                arguments("https://localhost:31121/greet", GET, NO_BODY, null, "hello"),
                arguments("https://localhost:31121/echo", GET, NO_BODY, null, "method: GET, type: LENGTH, bodylength: 0"),
                arguments("https://localhost:31121/echo", POST, LENGTH, "reqbody", "method: POST, type: LENGTH, bodylength: 7"),
                arguments("https://localhost:31121/echo", POST, LENGTH, generateHugeString(3000), "method: POST, type: LENGTH, bodylength: 3000"),
                arguments("https://localhost:31121/echo", POST, CHUNK, "reqbody", "method: POST, type: CHUNK, bodylength: 7")
        );
    }

    static Stream<Arguments> testDataChunkStream() {
        return Stream.of(
                arguments("http://localhost:31120/echostream", "hello", "world", "method: POST, type: CHUNKbodylength: 5bodylength: 5"),
                arguments("https://localhost:31121/echostream", "hello", "world", "method: POST, type: CHUNKbodylength: 5bodylength: 5")
        );
    }


    @ParameterizedTest
    @MethodSource("mezlogo.mid.core.HttpServerTest#testData")
    void should_return_body_as_LENGTH(String uriAsString, HttpMethod method, BodyType type, String requestBody, String expected) {
        var uri = URI.create(uriAsString);
        var response = sendAsync(uri.getScheme().equals("https"), uri.getPort(), uri.getPath(), type, method, requestBody).join();
        assertAll("should return correct http response",
                () -> assertEquals(200, response.statusCode()),
                () -> assertTrue(response.headers().firstValue("Content-Length").isPresent())
        );
        assertEquals(expected, response.body());
    }

    @ParameterizedTest
    @MethodSource("mezlogo.mid.core.HttpServerTest#testDataChunkStream")
    void should_return_chunk_strem(String uriAsString, String message1, String message2, String expected) {
        var uri = URI.create(uriAsString);
        var body1 = new ByteArrayInputStream(message1.getBytes(StandardCharsets.UTF_8));
        var body2 = new ByteArrayInputStream(message2.getBytes(StandardCharsets.UTF_8));
        var body = new SequenceInputStream(body1, body2);
        var response = sendAsync(uri.getScheme().equals("https"), uri.getPort(), uri.getPath(), POST, body).join();
        assertAll("should return correct http response",
                () -> assertEquals(200, response.statusCode()),
                () -> assertTrue(response.headers().firstValue("Transfer-Encoding").isPresent())
        );
        assertEquals(expected, response.body());
    }
}
