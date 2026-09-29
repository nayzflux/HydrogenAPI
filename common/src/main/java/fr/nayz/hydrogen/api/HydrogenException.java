package fr.nayz.hydrogen.api;

public final class HydrogenException extends RuntimeException {
    private final HydrogenErrorReason reason;

    public HydrogenException(HydrogenErrorReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public HydrogenException(HydrogenErrorReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public HydrogenErrorReason reason() {
        return reason;
    }
}
