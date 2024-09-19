package dev.leandromqrs.winrmj.exceptions;

public class WinRMWSManFault extends Exception {
    /**
     * A Fault returned in the SOAP response. The XML node is a WSManFault
     */
    public WinRMWSManFault() {
        super();
    }

    public WinRMWSManFault(String message) {
        super(message);
    }

    public WinRMWSManFault(String message, Throwable cause) {
        super(message, cause);
    }
}
