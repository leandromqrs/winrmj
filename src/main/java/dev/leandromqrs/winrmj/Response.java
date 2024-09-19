package dev.leandromqrs.winrmj;

public class Response {
    private String stdOut;
    private String stdErr;
    private int statusCode;

    public Response(String stdOut, String stdErr, int statusCode) {
        this.stdOut = stdOut;
        this.stdErr = stdErr;
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        // TODO: put three dots at the end if out/err was truncated
        return String.format("<Response code %d, out \"%s\", err \"%s\">",
                statusCode, stdOut.substring(0, Math.min(stdOut.length(), 20)),
                stdErr.substring(0, Math.min(stdErr.length(), 20)));
    }
}
