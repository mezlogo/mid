# mid

## Development:

Source script for direct access to mid: `source source_for_dev.sh`

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
