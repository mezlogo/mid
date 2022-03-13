package mezlogo.mid.cli.commands;

import mezlogo.mid.api.model.HttpHandlerToWebsocketAdapter;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpServer;
import mezlogo.mid.api.utils.HttpHandlers;
import mezlogo.mid.api.utils.Matchers;
import mezlogo.mid.api.utils.Tuple;
import mezlogo.mid.core.SslFactory;
import mezlogo.mid.core.netty.NettyHttpServer;
import picocli.CommandLine;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static mezlogo.mid.api.model.WebsocketHandlers.onEchoMsg;
import static mezlogo.mid.api.utils.Tuple.of;

@CommandLine.Command(name = "sample")
public class SampleCommand implements Runnable {
    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<PortTlsPair> ports;

    public static String reqToString(HttpRequest request) {
        var headers = request.headers.stream()
                .map(it -> it.getKey() + ": " + it.getValue())
                .collect(Collectors.joining(",", "[", "]"));
        var result = "url: " + request.url + ", method: " + request.method.name() + ", headers: " + headers;
        return result;
    }

    @Override
    public void run() {
        var pairs = ports.stream().map(it -> of(it.port, it.tls)).collect(Collectors.toList());
        var useSsl = pairs.stream().filter(Tuple::getValue).map(Tuple::getValue).findAny().orElse(false);
        var ssl = useSsl ? SslFactory.nettySsl() : null;
        HttpServer server = new NettyHttpServer(ssl);

        server.addHandler(Matchers.onExactMatch("time"), HttpHandlers.syncString(req -> new Date().toString()));
        server.addHandler(Matchers.onExactMatch("echoStream"), HttpHandlers.asyncStreamByEachBody(SampleCommand::reqToString,
                body -> "Request body: [" + body + "]"));
        server.addHandler(Matchers.onExactMatch("echo"), HttpHandlers.syncRequestBodyToString(
                (req, body) -> reqToString(req) + "\nBody length: " + body.length()));
        server.addHandler(Matchers.onExactMatch("websocket_echo"), new HttpHandlerToWebsocketAdapter(onEchoMsg(msg -> "echo: [" + msg + "]")));
        server.start(pairs).thenAccept(it -> System.out.println("Started sample server"));
    }

    static class PortTlsPair {
        @CommandLine.Option(names = "--port", defaultValue = "8080")
        int port = 8080;

        @CommandLine.Option(names = "--tls", defaultValue = "false")
        boolean tls = false;
    }

}
