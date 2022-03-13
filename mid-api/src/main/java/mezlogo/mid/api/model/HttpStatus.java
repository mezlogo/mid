package mezlogo.mid.api.model;

import java.util.Arrays;

public enum HttpStatus {
    SWITCHING_PROTOCOL(101), OK(200), NOT_FOUND(404), INTERNAL_ERROR(500),
    ;

    public final int code;

    HttpStatus(int code) {
        this.code = code;
    }

    public static HttpStatus byCode(int code) {
        return Arrays.stream(HttpStatus.values()).filter(it -> it.code == code)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Not found code: " + code));
    }
}
