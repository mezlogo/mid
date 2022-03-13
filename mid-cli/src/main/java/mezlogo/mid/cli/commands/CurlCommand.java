package mezlogo.mid.cli.commands;

import mezlogo.mid.api.model.BodyPublisher;
import mezlogo.mid.api.model.HttpMethod;
import mezlogo.mid.api.model.HttpRequest;
import mezlogo.mid.api.model.HttpResponse;
import mezlogo.mid.api.utils.Publishers;
import mezlogo.mid.api.utils.Subscribers;
import mezlogo.mid.netty.NettyHttpClient;
import picocli.CommandLine;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@CommandLine.Command(name = "curl", mixinStandardHelpOptions = true)
public class CurlCommand implements Runnable {
    @CommandLine.Parameters
    URI uri;

    @CommandLine.Option(names = "-m", defaultValue = "GET")
    HttpMethod method;

    @CommandLine.Option(names = "-H")
    List<String> headers = Collections.emptyList();

    @CommandLine.Option(names = "-d")
    String data;

    public int port() {
        return 0 <= uri.getPort() ? uri.getPort() : 80;
    }

    public HttpRequest buildRequest() {
        var uriAndQuery = uri.getPath() + (uri.getQuery() == null ? "" : ("?" + uri.getQuery()));
        var request = new HttpRequest(uriAndQuery, method);
        headers.forEach(it -> {
            var split = it.split(":\\s*");
            if (2 != split.length) {
                throw new RuntimeException("Header should be separated by colon: " + it);
            }
            request.addHeader(split[0], split[1]);
        });
        request.addHeader("Host", uri.getHost() + ":" + port());
        if (null != data) {
            request.addHeader("Content-Type", "text/plain");
            request.addHeader("Content-Length", "" + data.length());
        }
        return request;
    }

    @Override
    public void run() {
        var host = uri.getHost();
        var isSsl = uri.getScheme().contains("https");

        var request = buildRequest();

        var client = new NettyHttpClient();
        client.start().join();
        BodyPublisher reqBody = Publishers.fromList(null != data ? List.of(data) : Collections.emptyList());
        var result = client.request(host, port(), request, reqBody, isSsl);
        HttpResponse response = result.getKey().join();
        Subscribers.AggregateSubscriber subscriber = new Subscribers.AggregateSubscriber();
        result.getValue().subscribe(subscriber);
        var body = String.join("", subscriber.future.join());
        System.out.println(body);
    }
}
