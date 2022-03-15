package mezlogo.mid.cli.commands;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class ClientTest {
    @Container
    public GenericContainer redis = new GenericContainer(DockerImageName.parse("mezlogo/http-ws-bin:0.0.1"))
            .withExposedPorts(8443);

    public static ExecutionData executeClient(String... args) {
        ClientCommand command = new ClientCommand();
        Charset charset = Charset.defaultCharset();
        ByteArrayOutputStream captureOutput = new ByteArrayOutputStream();
        command.out = new PrintStream(captureOutput, true, charset);
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute(args);
        return new ExecutionData(command, commandLine, exitCode, captureOutput.toString(charset));
    }

    @Test
    public void request_root_should_return_html() {
        ExecutionData executionData = executeSut();
        assertAll("Should print index.html",
                () -> assertEquals(0, executionData.exitCode),
                () -> assertTrue(executionData.response.contains("Sample site"))
        );
    }

    public ExecutionData executeSut(String... args) {
        String host = redis.getHost();
        Integer port = redis.getFirstMappedPort();
        return executeClient("https://" + host + ":" + port);
    }

    public static class ExecutionData {
        public final ClientCommand command;
        public final CommandLine commandLine;
        public final int exitCode;
        public final String response;

        public ExecutionData(ClientCommand command, CommandLine commandLine, int exitCode, String response) {
            this.command = command;
            this.commandLine = commandLine;
            this.exitCode = exitCode;
            this.response = response;
        }
    }
}
