package mezlogo.mid.api.model;

public interface WebsocketClientConnection {
    void close();

    void onMsg(String msg);

    void send(String msg);
}
