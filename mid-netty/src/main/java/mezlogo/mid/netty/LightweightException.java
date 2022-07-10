package mezlogo.mid.netty;

public class LightweightException extends RuntimeException {
    public LightweightException(String message) {
        super(message);
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return null;
    }
}