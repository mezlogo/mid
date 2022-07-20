package mezlogo.mid.cli.commands;

import mezlogo.mid.netty.AppConfig;
import mezlogo.mid.netty.AppFactory;
import picocli.CommandLine;

import java.util.concurrent.CompletableFuture;

@CommandLine.Command(name = "tunnel", mixinStandardHelpOptions = true)
public class TunnelCommand implements Runnable {
    @CommandLine.Option(names = {"-p", "--port"}, defaultValue = "8080")
    int port;

    @CommandLine.Option(names = {"-v"})
    boolean verbose;


    @Override
    public void run() {
        var config = new AppConfig(verbose, verbose);
        var factory = AppFactory.createProduction(config);
        var server = factory.getServer();
        CompletableFuture<Void> future = server.bind(port).start();
        try {
            future.join();
            System.out.println("Listen " + port + " port");
        } catch (RuntimeException e) {
            System.err.println(e);
        }
    }
}
