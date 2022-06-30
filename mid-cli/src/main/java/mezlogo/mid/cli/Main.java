package mezlogo.mid.cli;

import mezlogo.mid.cli.commands.TunnelCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "mid", mixinStandardHelpOptions = true, description = "tool for http tunneling and proxying",
        subcommands = {TunnelCommand.class, CommandLine.HelpCommand.class})
public class Main {
    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }
}