package mezlogo.mid.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import mezlogo.mid.api.model.FlowPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PublishBytebufHandlerTest {
    @Test
    void when_pass_butebuf_should_pass_it_as_is() {
        FlowPublisher<ByteBuf> publisher = mock(FlowPublisher.class);
        var ch = new EmbeddedChannel(new PublishBytebufHandler(publisher));
        ByteBuf byteBuf = Unpooled.copiedBuffer("hello".getBytes(StandardCharsets.UTF_8));
        ch.writeInbound(byteBuf);

        var capture = ArgumentCaptor.forClass(ByteBuf.class);
        verify(publisher, times(1)).next(capture.capture());

        var actual = capture.getValue();
        assertThat(actual).isNotSameAs(byteBuf);
        assertThat(actual.toString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }

}