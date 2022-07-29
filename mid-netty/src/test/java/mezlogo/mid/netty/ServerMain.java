package mezlogo.mid.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;

import static mezlogo.mid.netty.AppFactory.createServer;

public class ServerMain {
    public static void main(String[] args) {
        var ssl = NettyUtils.serverSsl();
        var s = createServer(ch -> ch.pipeline().addLast(ssl.newHandler(ch.alloc()), IntegrationTest.stringCodec(), new SimpleChannelInboundHandler<String>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                        System.out.println("SERVER: " + msg);
                        ctx.writeAndFlush("echo: [" + msg + "]")
                                .addListener(NettyUtils.twoCallbacks(ok -> System.out.println("WRITEEN: " + ok), System.err::println));
                    }
                })
                , new NioEventLoopGroup());
        s.bind(8080);
    }
}
