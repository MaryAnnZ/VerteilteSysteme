package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

    public static enum Responses {

        LOGIN_SUCCESS("Successfully logged in."),
        LOGIN_FAIL("Wrong username or password."),
        LOGIN_REPEAT("User already logged in."),
        LOGIN_REQUIRED("User must login first."),
        LOGOUT_SUCCESS("Successfully logged out.");
        private String response;

        private Responses(String response) {
            this.response = response;
        }

        public String getResponse() {
            return response;
        }
    }
    private String componentName;
    private Config config;
    private Config users;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;
    private int tcpPort;
    private int udpPort;
    private boolean listening;
    private ConcurrentHashMap<Socket, String> loggedInUsers;
    private ConcurrentHashMap<String, String> registeredAddresses;
    private ConcurrentHashMap<Socket, String> connections;
    private TCPListener tcpl;
    private UDPListener udpl;
    private InputHandler ih;

    /**
     * @param componentName the name of the component - represented in the
     * prompt
     * @param config the configuration to use
     * @param userRequestStream the input stream to read user input from
     * @param userResponseStream the output stream to write the console output
     * to
     */
    public Chatserver(String componentName, Config config,
            InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;
        this.loggedInUsers = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.registeredAddresses = new ConcurrentHashMap<>();
        // TODO

        this.tcpPort = config.getInt("tcp.port");
        this.udpPort = config.getInt("udp.port");
        this.listening = true;
    }

    @Override
    public void run() {
        // TODO

        users = new Config("user");
        tcpl = new TCPListener();
        new Thread(tcpl).start();
        udpl = new UDPListener();
        new Thread(udpl).start();
        ih = new InputHandler(tcpl, udpl);
        new Thread(ih).start();


    }

    @Override
    public String users() throws IOException {
        // TODO Auto-generated method stub

        StringBuilder strb = new StringBuilder();
        int count = 0;
        List<String> sortedKeys = new LinkedList<>(users.listKeys());
        Collections.sort(sortedKeys);
        for (String str : sortedKeys) {
            String trimmed = str.replace(".password", "");
            strb.append("\n")
                    .append(++count)
                    .append(". ")
                    .append(trimmed)
                    .append(" ")
                    .append(loggedInUsers.containsValue(trimmed) ? "online" : "offline");
        }
        return strb.toString();
    }

    @Override
    public String exit() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param args the first argument is the name of the {@link Chatserver}
     * component
     */
    public static void main(String[] args) {
        Chatserver chatserver = new Chatserver(args[0],
                new Config("chatserver"), System.in, System.out);
        // TODO: start the chatserver
        chatserver.run();
    }

    private static String format(Object o, String msg) {
        String[] splited = o.toString().split("@");
        return "[" + o.getClass().getSimpleName().replaceAll("[^A-Z]", "") + "@" + splited[splited.length - 1] + "]: " + msg;
    }

    private class TCPListener implements Runnable {

        private ExecutorService TCPExecutorService;
        private ServerSocket serverSocket;

        private TCPListener() {
            this.TCPExecutorService = Executors.newCachedThreadPool();
        }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(Chatserver.this.tcpPort)) {
                this.serverSocket = serverSocket;

                while (!Thread.currentThread().isInterrupted() && listening) {
                    System.out.println(format(this, "- Listening for incoming TCP connection....."));
                    this.TCPExecutorService.submit(new TCPConnection(serverSocket.accept()));
                    System.out.println(format(this, "# Accepted connection."));

                }
                this.TCPExecutorService.shutdown();
                System.out.println(format(this, "# Stopped listening."));
            } catch (IOException ex) {
                System.out.println(format(this, "# Exception caught when trying to submit new client socket"));
                System.out.println(format(this, ex.getMessage()));
            }
        }

        public void exit() throws IOException {
            System.out.println("TCP exit");
            this.TCPExecutorService.shutdown();
            this.serverSocket.close();
        }
    }

    private class TCPConnection implements Runnable {

        private Socket socket;

        private TCPConnection(Socket socket) throws IOException {
            System.out.println(format(this, "# New Clientsocket generated."));
            this.socket = socket;
            connections.put(socket, Thread.currentThread().getName());
        }

        @Override
        public void run() {
            System.out.println(format(this, "- Socket started....."));
            try (
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String inputLine;
                System.out.println(format(this, "- Start listening....."));
                while (!Thread.currentThread().isInterrupted() && listening && (inputLine = in.readLine()) != null) {
                    System.out.println(format(this, "# Received: '" + inputLine + "'"));

                    //parse input
                    String[] input = inputLine.split(" ");
                    switch (input[0]) {
                        case "!login":
                            System.out.println(format(this, "# Login attempt."));
                            if (input.length > 2) {
                                String username = input[1];
                                String passwd = inputLine.substring(input[0].length() + input[1].length() + 2);

                                if (loggedInUsers.containsValue(username)) {
                                    System.out.println(format(this, "# Login rejected: Already logged in. (" + username + ")"));
                                    out.println(Responses.LOGIN_REPEAT);
                                    break;
                                }
                                boolean hit = false;
                                for (String str : users.listKeys()) {
                                    if (str.equals(username + ".password")) {
                                        hit = true;
                                    }
                                }
                                // hit: users.getString blockt wenn der username ungültig ist.... keine ahnung woran das liegt.........
                                if (hit && users.getString(username + ".password").equals(passwd)) {
                                    loggedInUsers.put(socket, username);

                                    System.out.println(format(this, "# Login successful. (" + username + ")"));
                                    out.println(Responses.LOGIN_SUCCESS);
                                    break;
                                }
                            }
                            System.out.println(format(this, "# Login rejected. (" + input + ")"));
                            out.println(Responses.LOGIN_FAIL);
                            break;
                        case "!logout":
                            if (loggedInUsers.containsKey(socket)) {
                                String username = loggedInUsers.remove(socket);
                                if (registeredAddresses.containsKey(username)) {
                                    System.out.println(format(this, "# Removing registered address. (" + username + ")"));
                                    registeredAddresses.remove(username);
                                }
                                System.out.println(format(this, "# Logout successful. (" + username + ")"));
                                out.println(Responses.LOGOUT_SUCCESS);
                            } else {
                                System.out.println(format(this, "# Logout rejected."));
                                out.println(Responses.LOGIN_REQUIRED);
                            }
                            break;
                        case "!send":
                            if (loggedInUsers.containsKey(socket)) {
                                String username = loggedInUsers.get(socket);
                                for (Socket s : loggedInUsers.keySet()) {
                                    if (!s.equals(socket)) {
                                        new PrintWriter(s.getOutputStream(), true)
                                                .println("!send" + username + ": " + inputLine.substring(input[0].length() + 1));
                                    }
                                }
                                System.out.println(format(this, "# Messege distributed. (" + username + ")"));
                            } else {
                                System.out.println(format(this, "# Send request rejected."));
                                out.println(Responses.LOGIN_REQUIRED);
                            }
                            break;
                        case "!register":
                            if (loggedInUsers.containsKey(socket)) {
                                String username = loggedInUsers.get(socket);
                                String address = input[1];
                                registeredAddresses.put(username, address);
                                System.out.println(format(this, "# Successfully registered address. (" + username + ")"));
                                out.println("Successfully registered address for " + username + ".");
                            } else {
                                System.out.println(format(this, "# Send request rejected."));
                                out.println(Responses.LOGIN_REQUIRED);
                            }
                            break;
                        case "!lookup":
                            String username = input[1];
                            if (loggedInUsers.containsKey(socket) && registeredAddresses.containsKey(username)) {
                                String address = registeredAddresses.get(username);
                                System.out.println(format(this, "# Successful lookup. (" + username + ": " + address + ")"));
                                out.println("!lookup" + address);
                            } else {
                                System.out.println(format(this, "# Send request rejected."));
                                out.println(Responses.LOGIN_REQUIRED);
                            }
                            break;
                        default:
                            out.println("Invalid command.");
                    }
                    System.out.println(format(this, "- Waiting for input....."));
                }
                System.out.println(format(this, "# Stopped listening."));

            } catch (IOException e) {
                System.out.println(format(this, "# Exception caught when trying to listen."));
                System.out.println(format(this, e.getMessage()));
            } finally {
                try {
                    if (!this.socket.isClosed()) {
                        this.socket.close();
                    }
                } catch (IOException ex) {
                    System.out.println("[ERR]" + format(this, ": Couldn't Close Socket."));
                }
            }
        }
    }

    private class UDPListener implements Runnable {

        private ExecutorService UDPExecutorService;
        private DatagramSocket socket;

        private UDPListener() {
            this.UDPExecutorService = Executors.newCachedThreadPool();
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(Chatserver.this.udpPort)) {
                this.socket = socket;
                while (!Thread.currentThread().isInterrupted() && listening) {
                    System.out.println(format(this, "- Listening for incoming UDP packet....."));

                    byte[] buf = new byte[256];

                    // receive request
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    System.out.println(format(this, "# Received packet."));
                    this.UDPExecutorService.submit(new UDPConnection(packet));
                }
                this.UDPExecutorService.shutdown();
                System.out.println(format(this, "# Stopped listening"));
            } catch (IOException ex) {
                System.out.println(format(this, "# Exception caught when trying to receive UDP packet."));
                System.out.println(format(this, ex.getMessage()));
            }
        }

        public void exit() throws IOException {
            System.out.println("UDP exit");
            this.UDPExecutorService.shutdown();
            this.socket.close();
        }
    }

    private class UDPConnection implements Runnable {

        private DatagramPacket packet;

        private UDPConnection(DatagramPacket packet) throws IOException {
            System.out.println(format(this, "# New Clientsocket generated."));
            this.packet = packet;
        }

        @Override
        public void run() {
            try (
                    DatagramSocket socket = new DatagramSocket()) {
                System.out.println(format(this, "# Socket started."));
                String received = new String(packet.getData(), 0, packet.getLength());

                System.out.println(format(this, "# Received: " + received));

                String answer = "Invalid command.";

                if (received.equals("!list")) {

                    StringBuilder strb = new StringBuilder("Users currently online:");
                    for (String str : loggedInUsers.values()) {
                        strb.append("\n* ").append(str);
                    }

                    answer = strb.toString();
                }
                byte[] buf = answer.getBytes();
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                packet = new DatagramPacket(buf, buf.length, address, port);
                socket.send(packet);
            } catch (IOException e) {
                System.out.println(format(this, "# Exception caught when trying respond to UDP."));
                System.out.println(format(this, e.getMessage()));
            }
        }
    }

    private class InputHandler implements Runnable {

        private TCPListener tcpl;
        private UDPListener udpl;

        private InputHandler(TCPListener tcpl, UDPListener udpl) {
            this.tcpl = tcpl;
            this.udpl = udpl;
        }

        @Override
        public void run() {

            try (
                    PrintWriter out = new PrintWriter(userResponseStream, true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(userRequestStream))) {
                String inputLine;
                while (listening && (inputLine = in.readLine()) != null) {

                    //parse input
                    String[] input = inputLine.split(" ");
                    switch (input[0]) {
                        case "!users":
                            out.println(Chatserver.this.users());
                            break;
                        case "!exit":
                            listening = false;
                            in.close();
                            tcpl.exit();
                            udpl.exit();

                            for (Socket s : connections.keySet()) {
                                if (!s.isClosed()) {
                                    s.close();
                                }
                            }
//                            Chatserver.this.exit();
                            break;
                        default:
                            out.println("Invalid command.");
                    }
                }

            } catch (IOException ex) {
                System.out.println(format(this, "# Exception caught when trying to read from or write to the system console."));
                System.out.println(format(this, ex.getMessage()));
            }

        }
    }
}
