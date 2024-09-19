package dev.leandromqrs.winrmj;

import dev.leandromqrs.winrmj.protocol.Protocol;

import java.util.*;

public class Session {
    private final Protocol protocol;

    public Session(String url, String username, String password) {
        this.protocol = new Protocol(url, username, password);
    }

    public Response runCmd(String command, List<String> args) {
        String shellId = protocol.openShell();
        String commandId = protocol.runCommand(shellId, command, args);
        Response response = new Response(protocol.getCommandOutput(shellId, commandId));
        protocol.cleanupCommand(shellId, commandId);
        protocol.closeShell(shellId);
        return response;
    }
}
