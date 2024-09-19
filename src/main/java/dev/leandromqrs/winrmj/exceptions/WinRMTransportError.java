package dev.leandromqrs.winrmj.exceptions;

public class WinRMTransportError extends Exception {
    /**
     * Transport-level error
     */
    public WinRMTransportError() {
        super();
    }

    public WinRMTransportError(String message) {
        super(message);
    }

    public WinRMTransportError(String message, Throwable cause) {
        super(message, cause);
    }
}
