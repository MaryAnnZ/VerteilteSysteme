package client;

import chatserver.Chatserver;
import cli.Command;
import cli.Shell;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.Config;

public class Client implements IClientCli, Runnable {

//    private final int LOGINSUCCESS = 1;
//    private final int LOGINSUCCESS = 1;
//    private final int LOGINSUCCESS = 1;
//    private final int LOGINSUCCESS = 1;
    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;
    private Shell shell;
    private Socket tcpsocket;
    private DatagramSocket dgsocket;
    private PrintWriter out;
    private BufferedReader in, stdIn;
    private String hostName;
    private int tcpPortNumber;
    private int udpPortNumber;
    private ExecutorService threadPool;
    private boolean running, loggedIn;
    private TCPListener tcpl;
    private UDPListener udpl;
    private String lastMsg;
    private TCPServerSocket tcpss;
    private String tmpMsgLookUpResult;
    private String tmpAck;
    private ConcurrentHashMap<Socket, String> lookupConnections;
    private boolean debug;

    /**
     * @param componentName the name of the component - represented in the
     * prompt
     * @param config the configuration to use
     * @param userRequestStream the input stream to read user input from
     * @param userResponseStream the output stream to write the console output
     * to
     */
    public Client(String componentName, Config config,
            InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;
        // TODO

        this.hostName = config.getString("chatserver.host");
        this.tcpPortNumber = config.getInt("chatserver.tcp.port");
        this.udpPortNumber = config.getInt("chatserver.udp.port");
        this.running = true;
        this.loggedIn = false;
        this.lastMsg = "No messages received yet.";
        this.tmpMsgLookUpResult = null;
        this.tmpAck = null;
        this.lookupConnections = new ConcurrentHashMap<>();
        this.debug = false;
    }

    public void printout(boolean debug, Object o, String msg) {
        if (this.debug) {
            System.out.println(debug ? "[DBG]" + format(o, msg) : format(o, msg));
        } else if (!debug) {
            System.out.println(msg);
        }
    }

    @Override
    public void run() {
        // TODO
        try (
                Socket tcpsocket = new Socket(hostName, tcpPortNumber);
                DatagramSocket dgsocket = new DatagramSocket();
                PrintWriter out = new PrintWriter(tcpsocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(tcpsocket.getInputStream()));
                Shell shell = new Shell(componentName, userRequestStream, userResponseStream)) {
            this.tcpsocket = tcpsocket;
            this.dgsocket = dgsocket;
            this.out = out;
            this.in = in;
            tcpl = new TCPListener();
            new Thread(tcpl).start();
            udpl = new UDPListener();
            new Thread(udpl).start();


            this.shell = shell;
            this.shell.register(this);
            shell.run();



        } catch (ConnectException e) {
            printout(true, this, "Could not connect to server.");
        } catch (UnknownHostException ex) {
            Logger.getLogger(Client.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Client.class
                    .getName()).log(Level.SEVERE, null, ex);
        } finally {
            running = false;
        }

    }

    @Command
    public String debug(boolean debug) {
        this.debug = debug;
        printout(false, this, "Debug " + (debug ? "enabled" : "disabled") + ".");
        return null;
    }

    @Override
    @Command
    public String login(String username, String password) throws IOException {
        // TODO Auto-generated method stub
        out.println("!login " + username + " " + password);
        return null;
    }

    @Override
    @Command
    public String logout() throws IOException {
        // TODO Auto-generated method stub
        out.println("!logout");
        return null;
    }

    @Override
    @Command
    public String send(String message) throws IOException {
        // TODO Auto-generated method stub
        out.println("!send " + message);
        return null;
    }

    @Override
    @Command
    public String list() throws IOException {
        // TODO Auto-generated method stub

        byte[] buf = "!list".getBytes();

        InetAddress address = InetAddress.getByName(this.hostName);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, this.udpPortNumber);
        dgsocket.send(packet);
        return null;
    }

    @Override
    @Command
    public String msg(String username, String message) throws IOException {
        // TODO Auto-generated method stub
        printout(true, this, "# Looking up address for " + username + ".....");
        out.println("!lookup " + username);

        if (lookupSuccess()) {
            printout(true, this, "# Succesful lookup for " + username + ": " + tmpMsgLookUpResult);
            String result = tmpMsgLookUpResult;
            //lookup successful
            String address = result.split(":")[0];
            int port = Integer.parseInt(result.split(":")[1]);

            Socket socket = new Socket(address, port);
            lookupConnections.put(socket, result);
            printout(false, this, "# Sent message: " + username + " => " + result);
            new Thread(new TCPPeerToPeerConnection(socket, username + ": " + message)).start();

            return null;
        }
        printout(false, this, "# Could not send message: Lookup failed");

        return null;
    }

    private boolean lookupSuccess() {
        tmpMsgLookUpResult = null;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
        }
        printout(true, this, "- Waiting for look up result.....");
        for (int i = 0; i < 5; i++) {
            if (tmpMsgLookUpResult != null) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
        }
        if (tmpMsgLookUpResult == null || !isValidAddress(tmpMsgLookUpResult)) {
            printout(true, this, "# Lookup failed.");
            return false;
        }
        return true;
    }

    @Override
    @Command
    public String lookup(String username) throws IOException {
        // TODO Auto-generated method stub
        printout(true, this, "- Looking up address for " + username + ".....");
        out.println("!lookup " + username);
        if (lookupSuccess()) {
            printout(false, this, tmpMsgLookUpResult);
            return null;
        }
        printout(false, this, "# Lookup failed.");
        return null;
    }

    @Override
    @Command
    public String register(String privateAddress) throws IOException {
        // TODO Auto-generated method stub
        if (isValidAddress(privateAddress)) {
            out.println("!register " + privateAddress);
            if (tcpss != null) {
                tcpss.exit();
            }
            tcpss = new TCPServerSocket(Integer.parseInt(privateAddress.split(":")[1]));
            new Thread(tcpss).start();
            return null;
        }
        printout(false, this, "# Invalid address: " + privateAddress);
        return null;
    }

    @Override
    @Command
    public String lastMsg() throws IOException {
        // TODO Auto-generated method stub
        printout(false, this, lastMsg);
        return null;
    }

    @Override
    @Command
    public String exit() throws IOException {
        // TODO Auto-generated method stub
        printout(true, this, "- Closing streams.....");
        this.running = false;
        printout(true, this, "- 'running' set to false.....");
        printout(true, this, "- Closing lookup connections.....");
        for (Socket s : this.lookupConnections.keySet()) {
            if (!s.isClosed()) {
                printout(true, this, "- Closing socket: " + s);
                s.close();
            }
        }
        printout(true, this, "- Closing private server socket.....");
        this.tcpss.exit();
        if (!this.tcpsocket.isClosed()) {
            printout(true, this, "- Closing tcp socket.....");
            this.tcpsocket.close();
        }
        if (!this.dgsocket.isClosed()) {
            printout(true, this, "- Closing udp socket.....");
            this.dgsocket.close();
        }
        printout(true, this, "- Closing shell.....");
        this.shell.close();

        printout(true, this, "- Shutting down.....");
        return "Bye.";
    }

    private void stop() {
        if (running) {
            try {
                this.exit();
            } catch (IOException ex) {
                printout(true, this, "# Exception caught when trying to exit.");
                printout(true, this, ex.getMessage());
            }
        }
    }

    private class TCPServerSocket implements Runnable {

        private ExecutorService TCPExecutorService;
        private ServerSocket serverSocket;
        private int port;

        private TCPServerSocket(int port) {
            printout(true, this, "# tcpss constructor.....");
            this.TCPExecutorService = Executors.newCachedThreadPool();
            printout(true, this, "# Thread pool generated .....");
            this.port = port;
        }

        @Override
        public void run() {
            printout(true, this, "- TCPServerSocket run.....");
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                printout(true, this, "- ServerSocket started.....");
                this.serverSocket = serverSocket;

                while (!Thread.currentThread().isInterrupted() && running) {
                    printout(true, this, "- Listening for incoming TCP connection.....");
                    this.TCPExecutorService.submit(new Client.TCPPeerToPeerConnection(serverSocket.accept()));
                    printout(true, this, "# Accepted connection.");

                }
                this.TCPExecutorService.shutdown();
                printout(true, this, "# Stopped listening.");
            } catch (IOException ex) {
                printout(true, this, "# Exception caught when trying to submit new client socket");
                printout(true, this, ex.getMessage());
            }
        }

        public void exit() throws IOException {
            System.out.println("TCP exit");
            this.serverSocket.close();
            this.TCPExecutorService.shutdown();
        }
    }

    private class TCPPeerToPeerConnection implements Runnable {

        private Socket socket;
        private String message;
        private final boolean printMessage;

        private TCPPeerToPeerConnection(Socket socket) throws IOException {
            printout(true, this, "# New P2P socket generated.");
            this.socket = socket;
            this.printMessage = false;
        }

        private TCPPeerToPeerConnection(Socket socket, String message) {
            printout(true, this, "# New P2P socket generated.");
            this.socket = socket;
            this.message = message;
            this.printMessage = true;
        }

        @Override
        public void run() {
            printout(true, this, "- P2P Socket started.....");
            try (
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                if (printMessage && message != null) {
                    out.println(message);
                }
                String inputLine;
                printout(true, this, "- Start listening.....");
                if ((inputLine = in.readLine()) != null) {
                    printout(true, this, "# Received response: " + inputLine);
                    if (inputLine.equals("!ack")) {
                        printout(false, this, inputLine);
                    } else {
                        printout(false, this, inputLine);
                        out.println("!ack");
                    }
                }
                printout(true, this, "# Stopped listening.");

            } catch (IOException e) {
                printout(true, this, "# Exception caught when trying to listen (P2P).");
                printout(true, this, e.getMessage());
            } finally {
                try {
                    if (!this.socket.isClosed()) {
                        this.socket.close();
                    }
                } catch (IOException ex) {
                    printout(true, this, ": Couldn't close P2P socket.");
                }
            }
        }
    }

    private class TCPListener implements Runnable {

        @Override
        public void run() {
            String res;
            try {
                while (!Thread.currentThread().isInterrupted() && running && (res = in.readLine()) != null) {
                    try {
                        printout(false, this, Chatserver.Responses.valueOf(res).getResponse());
                    } catch (IllegalArgumentException ex) {
                        if (res.startsWith("!send")) {
                            res = res.substring(5);
                            lastMsg = res;
                        }
                        if (res.startsWith("!lookup")) {
                            res = res.substring(7);
                            tmpMsgLookUpResult = res;
                            continue;
                        }
                        printout(false, this, res);
                    }
                }
            } catch (IOException ex) {
                printout(true, this, "# Exception caught while listening.");
                printout(true, this, ex.getMessage());
                Client.this.stop();
            }
        }
    }

    private class UDPListener implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && running) {
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    dgsocket.receive(packet);
                    String res = new String(packet.getData(), 0, packet.getLength());

                    printout(false, this, res);

                } catch (IOException ex) {
                    printout(true, this, "# Exception caught while listening.");
                    printout(true, this, ex.getMessage());
                    Client.this.stop();
                }
            }
        }
    }

    private static String format(Object o, String msg) {
        String[] splited = o.toString().split("@");
        return "[" + o.getClass().getSimpleName().replaceAll("[^A-Z]", "") + "@" + splited[splited.length - 1] + "]: " + msg;
    }

    private static boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }
        String[] split = address.split(":");
        if (split.length == 2) {
            String ip = split[0];
            String port = split[1];
            try {
                if (ip.isEmpty()) {
                    return false;
                }

                String[] parts = ip.split("\\.");
                if (parts.length != 4) {
                    return false;
                }

                for (String s : parts) {
                    int i = Integer.parseInt(s);
                    if ((i < 0) || (i > 255)) {
                        return false;
                    }
                }
                if (ip.endsWith(".")) {
                    return false;
                }

                int p = Integer.parseInt(port);
                if (p < 0 || p > 65535) {
                    return false;
                }

                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param args the first argument is the name of the {@link Client}
     * component
     */
    public static void main(String[] args) {
        Client client = new Client(args[0], new Config("client"), System.in,
                System.out);
        // TODO: start the client
        client.run();
    }

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---
    @Override
    public String authenticate(String username) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
