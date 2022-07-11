package mezlogo.mid.netty.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import mezlogo.mid.netty.AppFactory;

import java.util.concurrent.Flow;

public class HttpClientProxyChannelInit extends ChannelInitializer<SocketChannel> {
    private final AppFactory factory;
    private final Flow.Publisher<HttpObject> responsePublisher;

    public HttpClientProxyChannelInit(AppFactory factory, Flow.Publisher<HttpObject> responsePublisher) {
        this.factory = factory;
        this.responsePublisher = responsePublisher;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast("http-client-codec", new HttpClientCodec())
                .addLast("proxy", factory.createProxyHandler(responsePublisher));
    }
}
