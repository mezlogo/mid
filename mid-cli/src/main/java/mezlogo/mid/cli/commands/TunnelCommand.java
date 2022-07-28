package mezlogo.mid.cli.commands;

import mezlogo.mid.api.model.HostAndPort;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.cli.HostAndPortConvertor;
import mezlogo.mid.netty.AppConfig;
import mezlogo.mid.netty.AppFactory;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandLine.Command(name = "tunnel", mixinStandardHelpOptions = true)
public class TunnelCommand implements Runnable {
    @CommandLine.Option(names = {"-p", "--port"}, defaultValue = "8080")
    int port;

    @CommandLine.Option(names = {"-v"})
    boolean verbose;

    @CommandLine.Option(names = "--decrypt", arity = "0..*", description = "host:port for decrypt", converter = HostAndPortConvertor.class)
    List<HostAndPort> socketsToDecrypt;

    @Override
    public void run() {
        var config = new AppConfig(verbose, verbose);
        var factory = AppFactory.createProduction(config);
        factory.setSocketsToDecrypt(MidUtils.isDecrypt(socketsToDecrypt));
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
