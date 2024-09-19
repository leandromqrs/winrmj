package dev.leandromqrs.winrmj.exceptions;

public class WinRMWebServiceError extends Exception {
    /**
     * Generic WinRM SOAP Error
     */
    public WinRMWebServiceError() {
        super();
    }

    public WinRMWebServiceError(String message) {
        super(message);
    }

    public WinRMWebServiceError(String message, Throwable cause) {
        super(message, cause);
    }
}
