package dev.leandromqrs.winrmj.exceptions;

public class WinRMAuthorizationError extends Exception {
    /**
     * Authorization Error
     */
    public WinRMAuthorizationError() {
        super();
    }

    public WinRMAuthorizationError(String message) {
        super(message);
    }

    public WinRMAuthorizationError(String message, Throwable cause) {
        super(message, cause);
    }
}
