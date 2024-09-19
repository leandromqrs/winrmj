package dev.leandromqrs.winrmj;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        Session session = new Session("windows-host.example.com", "username", "password");
        Object result = session.runCmd("ipconfig", List.of("/all"));

    }
}