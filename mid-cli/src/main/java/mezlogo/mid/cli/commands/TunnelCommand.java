package mezlogo.mid.cli.commands;

import mezlogo.mid.api.HttpTunnelServer;
import mezlogo.mid.netty.NettyHttpTunnelServer;
import picocli.CommandLine;

import java.util.concurrent.CompletableFuture;

@CommandLine.Command(name = "tunnel", mixinStandardHelpOptions = true)
public class TunnelCommand implements Runnable {
    @CommandLine.Option(names = {"-p", "--port"}, defaultValue = "8080")
    int port;

    @Override
    public void run() {
        HttpTunnelServer httpTunnelServer = new NettyHttpTunnelServer(NettyHttpTunnelServer.createServer(NettyHttpTunnelServer.tunnelInitializer()));
        CompletableFuture<Void> future = httpTunnelServer.bind(port).start();
        try {
            future.join();
            System.out.println("Listen " + port + " port");
        } catch (RuntimeException e) {
            System.err.println(e);
        }
    }
}
