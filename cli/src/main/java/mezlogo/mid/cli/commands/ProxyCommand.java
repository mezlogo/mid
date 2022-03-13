package mezlogo.mid.cli.commands;

import picocli.CommandLine;

import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(name = "proxy")
public class ProxyCommand implements Runnable {
    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<ProxyArgConfig> configs;

    @CommandLine.Option(names = "--default", required = true)
    String defaultTarget;

    @Override
    public void run() {
        var result = configs.stream().map(it ->
                        it.target + "("
                                + String.join(", ", it.proxyConfigMatcher.regexp)
                                + "|"
                                + String.join(", ", it.proxyConfigMatcher.contains)
                                + ")")
                .collect(Collectors.joining());
        System.out.println(result);
    }

    static class ProxyArgConfig {
        @CommandLine.Option(names = "--target", required = true)
        String target;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
        ProxyConfigMatcher proxyConfigMatcher;
    }

    static class ProxyConfigMatcher {
        @CommandLine.Option(names = "--regexp", arity = "0..*")
        List<String> regexp;

        @CommandLine.Option(names = "--contains", arity = "0..*")
        List<String> contains;
    }
}
