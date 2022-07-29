package mezlogo.mid.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import mezlogo.mid.netty.handler.ChannelInitializerCallback;
import mezlogo.mid.utils.SslFactory;

import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClientMain {
    public static void main(String[] args) {
        var ssl = NettyUtils.clientSsl();
        Function<Channel, SslHandler> sslFactory = ch -> ssl.newHandler(ch.alloc());
        var c = AppFactory.createClient(new NioEventLoopGroup());
        c.connect("localhost", 8080).addListener(NettyUtils.twoCallbacks(ok ->
                        ok.writeAndFlush("hello").addListener(NettyUtils.twoCallbacks(written -> System.out.println("WRITEEN: " + written), System.err::println))
                , System.err::println));
    }

    static void sslChat(Channel channel, Function<Channel, SslHandler> sslFactory) {
        ChannelInboundHandler beforeIn = wrap(new ChannelInboundHandlerAdapter(), ChannelInboundHandler.class, "before");
        ChannelInboundHandler afterIn = wrap(new ChannelInboundHandlerAdapter(), ChannelInboundHandler.class, "after");
        ChannelOutboundHandler beforeOut = wrap(new ChannelOutboundHandlerAdapter(), ChannelOutboundHandler.class, "before");
        ChannelOutboundHandler afterOut = wrap(new ChannelOutboundHandlerAdapter(), ChannelOutboundHandler.class, "after");
        c.handler(new ChannelInitializerCallback(ch -> ch.pipeline().addLast(new LoggingHandler("before_ssl", LogLevel.INFO), beforeIn, beforeOut, sslFactory.apply(ch), afterIn, afterOut, new LoggingHandler("after_ssl", LogLevel.INFO), IntegrationTest.stringCodec(), new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                System.out.println("RESPONSE: " + msg);
                c.config().group().shutdownGracefully();
            }
        })));
    }

    static void sslAfterResponse(Channel channel, Function<Channel, SslHandler> sslFactory) {
        channel.pipeline().addLast(IntegrationTest.stringCodec(), new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                System.out.println("RESPONSE: " + msg + "\nGoing to phase2");
                if (null != ctx.pipeline().get(SslHandler.class)) {
                    ctx.pipeline().addFirst(sslFactory.apply(ctx.channel()));
                }
            }
        });
    }

    public static <T> T wrap(T t, Class<T> clazz, String name) {
        return (T) Proxy.newProxyInstance(ClientMain.class.getClassLoader(), new Class[]{clazz}, (p, method, args) -> {
            System.out.println("TRACE(" + name + "): " + method + " args: " + args);
            if ("userEventTriggered".equals(method.getName())) {
                var obj = args[1];
                System.out.println("EVENT: "  + obj.getClass());
            }
            return method.invoke(t, args);
        });
    }
}
