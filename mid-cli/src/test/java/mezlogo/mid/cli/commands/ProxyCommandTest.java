package mezlogo.mid.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyCommandTest {

    ProxyCommand testInit(String args) {
        var sut = new ProxyCommand();
        var cmd = new CommandLine(sut);
        cmd.parseArgs(args.split(" "));
        return sut;
    }

    @Test
    void should_parse_config_correctly_one_contains() {
        var sut = testInit("--target http://localhost:8080 --contains bootstrap.js --default http://localhost:8081");
        assertEquals(1, sut.configs.size());
        assertEquals("http://localhost:8081", sut.defaultTarget);

        var arg1 = sut.configs.get(0);
        assertLinesMatch(Arrays.asList("bootstrap.js"), arg1.proxyConfigMatcher.contains);
        assertNull(arg1.proxyConfigMatcher.regexp);
        assertEquals("http://localhost:8080", arg1.target);
    }

    @Test
    void should_parse_config_correctly_one_config_with_multiple_values() {
        var sut = testInit("--target http://localhost:8080 --contains bootstrap.js bootstrap.css --regexp (/js/|/css/) --target http://localhost:3000 --contains bundle.js --default http://localhost:8081");
        assertEquals(2, sut.configs.size());

        var arg2 = sut.configs.get(1);
        assertLinesMatch(Arrays.asList("bundle.js"), arg2.proxyConfigMatcher.contains);
        assertNull(arg2.proxyConfigMatcher.regexp);
        assertEquals("http://localhost:3000", arg2.target);
    }

    @Test
    void should_parse_config_correctly_two_configs_with_multiple_values() {
        var sut = testInit("--target http://localhost:8080 --contains bootstrap.js bootstrap.css --regexp (/js/|/css/) --default http://localhost:8081");
        assertEquals(1, sut.configs.size());

        var arg1 = sut.configs.get(0);
        assertLinesMatch(Arrays.asList("bootstrap.js", "bootstrap.css"), arg1.proxyConfigMatcher.contains);
        assertLinesMatch(Arrays.asList("(/js/|/css/)"), arg1.proxyConfigMatcher.regexp);
        assertEquals("http://localhost:8080", arg1.target);
    }

    @Test
    void should_fail_on_no_mathcer() {
        assertThrows(CommandLine.MissingParameterException.class, () -> testInit("--target http://localhost:8080 --default http://localhost:8081"));
    }

    @Test
    void should_fail_on_no_target() {
        assertThrows(CommandLine.MissingParameterException.class, () -> testInit("--contains bootstrap.js --default http://localhost:8081"));
    }

    @Test
    void should_fail_on_no_target_with_two() {
        assertThrows(CommandLine.MissingParameterException.class, () -> testInit("--contains bootstrap.js --regexp (info|error) --default http://localhost:8081"));
    }
}
