package mezlogo.mid.cli;

import mezlogo.mid.cli.commands.CurlCommand;
import mezlogo.mid.cli.commands.ProxyCommand;
import mezlogo.mid.cli.commands.SampleCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "mid",
        mixinStandardHelpOptions = true,
        subcommands = {CurlCommand.class, ProxyCommand.class, SampleCommand.class, CommandLine.HelpCommand.class},
        description = "http proxy")
public class Main {
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }
}