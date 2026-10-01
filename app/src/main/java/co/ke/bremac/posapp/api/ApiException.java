package co.ke.bremac.posapp.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ApiException extends Exception {
    public final int httpStatus;
    public final String code;
    public final Map<String, List<String>> errors;

    public ApiException(int httpStatus, String code, String message, Map<String, List<String>> errors) {
        super(message == null || message.isEmpty() ? "Request failed" : message);
        this.httpStatus = httpStatus;
        this.code = code == null ? "" : code;
        this.errors = errors == null ? Collections.emptyMap() : errors;
    }

    public boolean isUnauthenticated() {
        return httpStatus == 401 || "unauthenticated".equals(code);
    }
}
