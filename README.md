# mid

## Development:

Source script for direct access to mid: `source source_for_dev.sh`

### MVP:

#### by-hand
- `curl -x $MID -v https://httpbin.org/anything`
- `curl -x $MID -v -d hello https://httpbin.org/anything`

#### acceptance-test
- `jdk-client` calls `undertow-server` with `mid-proxy` and `mid` passes bi-directional messages
  - `GET`
  - `POST`

#### integration-test
`EmbeddedChannel` receives incoming http request and try to connect to given url:
- when url malformed returns 404 with same message
- when given url is unreachable returns 503 with same message
- when client connected:
  - client sends source message with modified url
  - server writes source response as is

#### unit-test
- `api.flow.FlowPublisher`
- `api.flow.BufferedPublisher`
- `api.flow.SubscriberToCallbacks`
- `netty.handler.HttpTunnelHandler(uriParser, configuration, client)`
  - `client.openConnection(host, port, requestBody): future publisher` connects to target
  - `uriParser.parse` extracts host, port, uri and query params
  - modify request uri
  - bind response publisher to channel
  - 404 on malformed uri
  - 503 on target unreached
  - `configuration.createPublisher`
  - `configuration.createHttpObjectPublisherHandler`
  - replace handler
  - fire request
- `netty.handler.HttpObjectPublisherHandler(publisher)`
  - recreate request and response
  - extract and duplicate byteBuf for content and last content
- `cli.command.HttpTunnelCommand`

### Side notes:

1. for testing websocket in browser console

```js
async function test(port = 8080) {
    let done;
    const waitForIt = new Promise(resolve => done = resolve);
    const ws = new WebSocket("ws://localhost:" + port + "/websocket_echo");
    ws.onopen = () => done();
    ws.onmessage = msg => console.log(`Received: (${msg.data})`);
    await waitForIt;
    ws.send('SEND_DATA');
    setTimeout(_ => ws.close(), 2000);
}
```
