package mezlogo.mid.core.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class TraceHttpResponseInboundChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final static AtomicInteger globalCounter = new AtomicInteger();
    private final static Logger logger = LoggerFactory.getLogger(TraceHttpResponseInboundChannelHandler.class);

    private final int tcpCount = globalCounter.incrementAndGet();
    protected String url = "";
    private int respCount = 0;
    private int code = 0;

    protected void trace(String msg) {
        logger.trace("tcp: {}, resp: {}, url: {}, code: {}, msg: {}", tcpCount, respCount, url, code, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        trace("active");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        trace("inactive");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            var resp = (HttpResponse) msg;
            code = resp.status().code();
            respCount++;
            trace("response");
        } else if (msg instanceof LastHttpContent) {
            trace("last content: " + ((LastHttpContent) msg).content().readableBytes());
        } else if (msg instanceof HttpContent) {
            trace("content: " + ((HttpContent) msg).content().readableBytes());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exceptionCaught: ", cause);
    }
}
