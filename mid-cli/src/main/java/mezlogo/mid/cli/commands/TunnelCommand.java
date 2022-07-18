package mezlogo.mid.cli.commands;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import mezlogo.mid.api.HttpTunnelServer;
import mezlogo.mid.api.utils.MidUtils;
import mezlogo.mid.netty.AppConfig;
import mezlogo.mid.netty.AppFactory;
import mezlogo.mid.netty.NettyHttpTunnelServer;
import mezlogo.mid.netty.NettyNetworkClient;
import mezlogo.mid.netty.NettyNetworkClientFunction;
import mezlogo.mid.netty.NettyUtils;
import mezlogo.mid.netty.handler.HttpTunnelHandler;
import picocli.CommandLine;

import java.util.concurrent.CompletableFuture;

@CommandLine.Command(name = "tunnel", mixinStandardHelpOptions = true)
public class TunnelCommand implements Runnable {
    @CommandLine.Option(names = {"-p", "--port"}, defaultValue = "8080")
    int port;

    @Override
    public void run() {
        AppConfig config = new AppConfig(true, true);

        NioEventLoopGroup group = new NioEventLoopGroup();
        var clientBootsrap = new Bootstrap().group(group).channel(NioSocketChannel.class);
        NettyNetworkClient client = new NettyNetworkClientFunction((host, port) -> NettyUtils.openChannel(clientBootsrap, host, port), config);
        AppFactory factory = new AppFactory(config);
        HttpTunnelServer httpTunnelServer = new NettyHttpTunnelServer(NettyHttpTunnelServer.createServer(NettyHttpTunnelServer.tunnelInitializer(() -> new HttpTunnelHandler(MidUtils::uriParser, client, factory), factory), group));
        CompletableFuture<Void> future = httpTunnelServer.bind(port).start();
        try {
            future.join();
            System.out.println("Listen " + port + " port");
        } catch (RuntimeException e) {
            System.err.println(e);
        }
    }
}
