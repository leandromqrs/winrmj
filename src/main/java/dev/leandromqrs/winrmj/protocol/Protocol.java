package dev.leandromqrs.winrmj.protocol;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jakarta.xml.soap.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Protocol {
    public static final String DEFAULT_TIMEOUT = "PT60S";
    public static final int DEFAULT_MAX_ENV_SIZE = 153600;
    public static final String DEFAULT_LOCALE = "en-US";

    private String endpoint;
    private String timeout;
    private int maxEnvSize;
    private String locale;
    private HttpClient transport;
    private String username;
    private String password;
    private String service;
    private String keytab;
    private String caTrustPath;

    public Protocol(String endpoint, String transport, String username, String password, String service, String keytab, String caTrustPath) {
        this.endpoint = endpoint;
        this.timeout = DEFAULT_TIMEOUT;
        this.maxEnvSize = DEFAULT_MAX_ENV_SIZE;
        this.locale = DEFAULT_LOCALE;
        if (transport.equalsIgnoreCase("plaintext")) {
            this.transport = HttpClient.newBuilder().build();
        } else if (transport.equalsIgnoreCase("kerberos")) {
            // TODO: Implement kerberos transport
            throw new UnsupportedOperationException("Kerberos transport is not supported yet.");
        } else {
            throw new IllegalArgumentException("Invalid transport type: " + transport);
        }
        this.username = username;
        this.password = password;
        this.service = service;
        this.keytab = keytab;
        this.caTrustPath = caTrustPath;
    }

    public String setTimeout(int seconds) {
        // in original library there is an alias - op_timeout method
        return String.format("%dS", seconds);
    }

    public String openShell(String iStream, String oStream, String workingDirectory, Map<String, String> envVars, boolean noProfile, int codepage, Integer lifetime, Integer idleTimeout) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema");
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("env", "http://www.w3.org/2003/05/soap-envelope");
        envelope.addNamespaceDeclaration("a", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        envelope.addNamespaceDeclaration("b", "http://schemas.dmtf.org/wbem/wsman/1/cimbinding.xsd");
        envelope.addNamespaceDeclaration("n", "http://schemas.xmlsoap.org/ws/2004/09/enumeration");
        envelope.addNamespaceDeclaration("x", "http://schemas.xmlsoap.org/ws/2004/09/transfer");
        envelope.addNamespaceDeclaration("w", "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("p", "http://schemas.microsoft.com/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("rsp", "http://schemas.microsoft.com/wbem/wsman/1/windows/shell");
        envelope.addNamespaceDeclaration("cfg", "http://schemas.microsoft.com/wbem/wsman/1/config");

        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        _buildSoapHeader(header, message.getMessageID());
        _setResourceUriCmd(header);
        _setActionCreate(header);
        if (noProfile) {
            _setOption(header, "WINRS_NOPROFILE", true);
        }
        if (codepage != 437) {
            _setOption(header, "WINRS_CODEPAGE", codepage);
        }

        SOAPBody body = envelope.getBody();
        if (body == null) {
            body = envelope.addBody();
        }
        SOAPElement shell = body.addChildElement("Shell", "rsp");
        shell.addChildElement("InputStreams").addTextNode(iStream);
        shell.addChildElement("OutputStreams").addTextNode(oStream);
        if (workingDirectory != null) {
            shell.addChildElement("WorkingDirectory").addTextNode(workingDirectory);
        }
        if (lifetime != null) {
            // TODO: research Lifetime a bit more: http://msdn.microsoft.com/en-us/library/cc251546(v=PROT.13).aspx
        }
        if (idleTimeout != null) {
            shell.addChildElement("IdleTimeOut").addTextNode(String.valueOf(idleTimeout));
        }
        if (envVars != null) {
            SOAPElement environment = shell.addChildElement("Environment", "rsp");
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                SOAPElement variable = environment.addChildElement("Variable", "rsp");
                variable.addAttribute("Name", entry.getKey());
                variable.addTextNode(entry.getValue());
            }
        }

        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();
        String url = String.format("http://%s", endpoint);
        SOAPMessage response = connection.call(message, new URI(url));
        NodeList nodes = response.getSOAPBody().getElementsByTagNameNS("*", "ShellId");
        return nodes.item(0).getTextContent();
    }

    // Helper methods for building SOAP Headers

    private void _buildSoapHeader(SOAPHeader header, String messageId) throws Exception {
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        SOAPElement to = header.addChildElement("To", "a");
        to.addTextNode(endpoint);
        SOAPElement replyTo = header.addChildElement("ReplyTo", "a");
        SOAPElement address = replyTo.addChildElement("Address", "a");
        address.addAttribute("xml:lang", "en-US");
        address.addTextNode("http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous");
        SOAPElement maxEnvelopeSize = header.addChildElement("MaxEnvelopeSize", "w");
        maxEnvelopeSize.addTextNode(String.valueOf(maxEnvSize));
        SOAPElement messageID = header.addChildElement("MessageID", "a");
        messageID.addTextNode(String.format("uuid:%s", messageId));
        SOAPElement locale = header.addChildElement("Locale", "w");
        locale.addAttribute("xml:lang", locale);
        SOAPElement dataLocale = header.addChildElement("DataLocale", "p");
        dataLocale.addAttribute("xml:lang", locale);
        // TODO: research this a bit http://msdn.microsoft.com/en-us/library/cc251561(v=PROT.13).aspx
        // SOAPElement maxTimeoutms = header.addChildElement("MaxTimeoutms", "cfg");
        // maxTimeoutms.addTextNode("600");
        SOAPElement operationTimeout = header.addChildElement("OperationTimeout", "w");
        operationTimeout.addTextNode(timeout);
    }

    private void _setResourceUriCmd(SOAPHeader header) {
        SOAPElement resourceURI = header.addChildElement("ResourceURI", "w");
        resourceURI.addTextNode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd");
    }

    private void _setResourceUriWmi(SOAPHeader header, String namespace) {
        SOAPElement resourceURI = header.addChildElement("ResourceURI", "w");
        resourceURI.addTextNode(String.format("http://schemas.microsoft.com/wbem/wsman/1/wmi/%s", namespace));
    }

    private void _setActionCreate(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.xmlsoap.org/ws/2004/09/transfer/Create");
    }

    private void _setActionDelete(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.xmlsoap.org/ws/2004/09/transfer/Delete");
    }

    private void _setActionCommand(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Command");
    }

    private void _setActionReceive(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Receive");
    }

    private void _setActionSignal(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Signal");
    }

    private void _setActionEnumerate(SOAPHeader header) {
        SOAPElement action = header.addChildElement("Action", "a");
        action.addTextNode("http://schemas.xmlsoap.org/ws/2004/09/enumeration/Enumerate");
    }

    private void _setSelectorShellId(SOAPHeader header, String shellId) {
        SOAPElement selectorSet = header.addChildElement("SelectorSet", "w");
        SOAPElement selector = selectorSet.addChildElement("Selector", "w");
        selector.addAttribute("Name", "ShellId");
        selector.addTextNode(shellId);
    }

    public void closeShell(String shellId) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema");
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("env", "http://www.w3.org/2003/05/soap-envelope");
        envelope.addNamespaceDeclaration("a", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        envelope.addNamespaceDeclaration("b", "http://schemas.dmtf.org/wbem/wsman/1/cimbinding.xsd");
        envelope.addNamespaceDeclaration("n", "http://schemas.xmlsoap.org/ws/2004/09/enumeration");
        envelope.addNamespaceDeclaration("x", "http://schemas.xmlsoap.org/ws/2004/09/transfer");
        envelope.addNamespaceDeclaration("w", "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("p", "http://schemas.microsoft.com/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("rsp", "http://schemas.microsoft.com/wbem/wsman/1/windows/shell");
        envelope.addNamespaceDeclaration("cfg", "http://schemas.microsoft.com/wbem/wsman/1/config");

        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        _buildSoapHeader(header, message.getMessageID());
        _setResourceUriCmd(header);
        _setActionDelete(header);
        _setSelectorShellId(header, shellId);

        SOAPBody body = envelope.getBody();
        if (body == null) {
            body = envelope.addBody();
        }

        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();
        String url = String.format("http://%s", endpoint);
        SOAPMessage response = connection.call(message, new URI(url));
        NodeList nodes = response.getSOAPHeader().getElementsByTagNameNS("*", "RelatesTo");
        String relatesTo = nodes.item(0).getTextContent();
        // TODO: change assert into user-friendly exception
        assert relatesTo.startsWith("uuid:");
        UUID messageId = UUID.fromString(relatesTo.substring("uuid:".length()));
        assert messageId.equals(message.getMessageID());
    }

    public String runCommand(String shellId, String command, String[] arguments, boolean consoleModeStdin, boolean skipCmdShell) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema");
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("env", "http://www.w3.org/2003/05/soap-envelope");
        envelope.addNamespaceDeclaration("a", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        envelope.addNamespaceDeclaration("b", "http://schemas.dmtf.org/wbem/wsman/1/cimbinding.xsd");
        envelope.addNamespaceDeclaration("n", "http://schemas.xmlsoap.org/ws/2004/09/enumeration");
        envelope.addNamespaceDeclaration("x", "http://schemas.xmlsoap.org/ws/2004/09/transfer");
        envelope.addNamespaceDeclaration("w", "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("p", "http://schemas.microsoft.com/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("rsp", "http://schemas.microsoft.com/wbem/wsman/1/windows/shell");
        envelope.addNamespaceDeclaration("cfg", "http://schemas.microsoft.com/wbem/wsman/1/config");

        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        _buildSoapHeader(header, message.getMessageID());
        _setResourceUriCmd(header);
        _setActionCommand(header);
        _setSelectorShellId(header, shellId);
        _setOption(header, "WINRS_CONSOLEMODE_STDIN", consoleModeStdin);
        _setOption(header, "WINRS_SKIP_CMD_SHELL", skipCmdShell);

        SOAPBody body = envelope.getBody();
        if (body == null) {
            body = envelope.addBody();
        }
        SOAPElement commandLine = body.addChildElement("CommandLine", "rsp");
        SOAPElement command = commandLine.addChildElement("Command", "rsp");
        command.addTextNode(command);
        if (arguments != null && arguments.length > 0) {
            SOAPElement argumentsElement = commandLine.addChildElement("Arguments", "rsp");
            argumentsElement.addTextNode(String.join(" ", arguments));
        }

        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();
        String url = String.format("http://%s", endpoint);
        SOAPMessage response = connection.call(message, new URI(url));
        NodeList nodes = response.getSOAPBody().getElementsByTagNameNS("*", "CommandId");
        return nodes.item(0).getTextContent();
    }

    public void cleanupCommand(String shellId, String commandId) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema");
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("env", "http://www.w3.org/2003/05/soap-envelope");
        envelope.addNamespaceDeclaration("a", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        envelope.addNamespaceDeclaration("b", "http://schemas.dmtf.org/wbem/wsman/1/cimbinding.xsd");
        envelope.addNamespaceDeclaration("n", "http://schemas.xmlsoap.org/ws/2004/09/enumeration");
        envelope.addNamespaceDeclaration("x", "http://schemas.xmlsoap.org/ws/2004/09/transfer");
        envelope.addNamespaceDeclaration("w", "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("p", "http://schemas.microsoft.com/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("rsp", "http://schemas.microsoft.com/wbem/wsman/1/windows/shell");
        envelope.addNamespaceDeclaration("cfg", "http://schemas.microsoft.com/wbem/wsman/1/config");

        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        _buildSoapHeader(header, message.getMessageID());
        _setResourceUriCmd(header);
        _setActionSignal(header);
        _setSelectorShellId(header, shellId);

        SOAPBody body = envelope.getBody();
        if (body == null) {
            body = envelope.addBody();
        }
        SOAPElement signal = body.addChildElement("Signal", "rsp");
        signal.addAttribute("CommandId", commandId);
        SOAPElement code = signal.addChildElement("Code", "rsp");
        code.addTextNode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/signal/terminate");

        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();
        String url = String.format("http://%s", endpoint);
        SOAPMessage response = connection.call(message, new URI(url));
        NodeList nodes = response.getSOAPHeader().getElementsByTagNameNS("*", "RelatesTo");
        String relatesTo = nodes.item(0).getTextContent();
        // TODO: change assert into user-friendly exception
        assert relatesTo.startsWith("uuid:");
        UUID messageId = UUID.fromString(relatesTo.substring("uuid:".length()));
        assert messageId.equals(message.getMessageID());
    }

    public String[] getCommandOutput(String shellId, String commandId) throws Exception {
        String stdout = "";
        String stderr = "";
        int returnCode = -1;

        while (true) {
            stdout = stdout + _rawGetCommandOutput(shellId, commandId, "stdout")[0];
            stderr = stderr + _rawGetCommandOutput(shellId, commandId, "stderr")[0];
            if (_rawGetCommandOutput(shellId, commandId, "exitcode")[1] != -1) {
                returnCode = _rawGetCommandOutput(shellId, commandId, "exitcode")[1];
                break;
            }
        }

        return new String[] { stdout, stderr, String.valueOf(returnCode) };
    }

    private String[] _rawGetCommandOutput(String shellId, String commandId, String streamName) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("xsd", "http://www.w3.org/2001/XMLSchema");
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("env", "http://www.w3.org/2003/05/soap-envelope");
        envelope.addNamespaceDeclaration("a", "http://schemas.xmlsoap.org/ws/2004/08/addressing");
        envelope.addNamespaceDeclaration("b", "http://schemas.dmtf.org/wbem/wsman/1/cimbinding.xsd");
        envelope.addNamespaceDeclaration("n", "http://schemas.xmlsoap.org/ws/2004/09/enumeration");
        envelope.addNamespaceDeclaration("x", "http://schemas.xmlsoap.org/ws/2004/09/transfer");
        envelope.addNamespaceDeclaration("w", "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("p", "http://schemas.microsoft.com/wbem/wsman/1/wsman.xsd");
        envelope.addNamespaceDeclaration("rsp", "http://schemas.microsoft.com/wbem/wsman/1/windows/shell");
        envelope.addNamespaceDeclaration("cfg", "http://schemas.microsoft.com/wbem/wsman/1/config");

        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        _buildSoapHeader(header, message.getMessageID());
        _setResourceUriCmd(header);
        _setActionReceive(header);
        _setSelectorShellId(header, shellId);

        SOAPBody body = envelope.getBody();
        if (body == null) {
            body = envelope.addBody();
        }
        SOAPElement receive = body.addChildElement("Receive", "rsp");
        SOAPElement desiredStream = receive.addChildElement("DesiredStream", "rsp");
        desiredStream.addTextNode(streamName);
        desiredStream.addAttribute("CommandId", commandId);

        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();
        String url = String.format("http://%s", endpoint);
        SOAPMessage response = connection.call(message, new URI(url));

        NodeList nodes = response.getSOAPBody().getElementsByTagNameNS("*", streamName);
        String stream = "";
        if (nodes.getLength() > 0) {
            stream = new String(Base64.getDecoder().decode(nodes.item(0).getTextContent().getBytes("UTF-8")));
        }

        int returnCode = -1;
        nodes = response.getSOAPBody().getElementsByTagNameNS("*", "ExitCode");
        if (nodes.getLength() > 0) {
            returnCode = Integer.parseInt(nodes.item(0).getTextContent());
        }

        return new String[] { stream, String.valueOf(returnCode) };
    }

    private void _setOption(SOAPHeader header, String name, boolean value) {
        SOAPElement optionSet = header.addChildElement("OptionSet", "w");
        SOAPElement option = optionSet.addChildElement("Option", "w");
        option.addAttribute("Name", name);
        option.addTextNode(Boolean.toString(value).toUpperCase());
    }

    private void _setOption(SOAPHeader header, String name, int value) {
        SOAPElement optionSet = header.addChildElement("OptionSet", "w");
        SOAPElement option = optionSet.addChildElement("Option", "w");
        option.addAttribute("Name", name);
        option.addTextNode(Integer.toString(value));
    }
}


