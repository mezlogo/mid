package mezlogo.mid.api.model;

public class StringBuffer implements HttpBuffer {
    public final String data;

    public StringBuffer(String data) {
        this.data = data;
    }

    @Override
    public String asString() {
        return data;
    }
}
