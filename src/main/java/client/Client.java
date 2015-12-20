package client;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chatserver.listener.TcpListener;
import cli.Command;
import cli.Shell;
import util.Config;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private final ExecutorService threadPool;
	private DatagramSocket datagramSocket;
	private Socket socket;
	private BufferedReader serverResponse;
	private PrintWriter clientRequest;
	private DatagramPacket packet;
	
	private ServerSocket serverSocket;
	private Socket privateSocket;
	
	private Shell shell;
	private ServerResponseListener responseListener;
	
	private String lastMessage = null;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		
		try {
			socket = new Socket(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			datagramSocket = new DatagramSocket();
			
			serverResponse = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			clientRequest = new PrintWriter(socket.getOutputStream(), true);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		// thread pool creates new threads as needed and reuse previous threads
		threadPool = Executors.newCachedThreadPool();
		
		// shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		
		// server response listener
		responseListener = new ServerResponseListener(this, serverResponse);
		
	}

	@Override
	public void run() {
		threadPool.execute(new Thread(shell));
		threadPool.execute(new Thread(responseListener));
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {		
		
		clientRequest.println("login_" + username + "_" + password);
		return getServerResponse("login");	
	}

	@Override
	@Command
	public String logout() throws IOException {
		
		clientRequest.println("logout_");
		return getServerResponse("logout");
	}

	@Override
	@Command
	public String send(String message) throws IOException {
			
		clientRequest.println("send_" + message);
		return getServerResponse("send");
	}

	@Override
	@Command
	public String list() throws IOException {

		byte[] buffer = "!list".getBytes();
		// create the datagram packet with all the necessary information
		// for sending the packet to the server
		packet = new DatagramPacket(buffer, buffer.length,
				InetAddress.getByName(config.getString("chatserver.host")),
				config.getInt("chatserver.udp.port"));

		// send request-packet to server
		datagramSocket.send(packet);

		buffer = new byte[1024];
		// create a fresh packet
		packet = new DatagramPacket(buffer, buffer.length);
		// wait for response-packet from server
		datagramSocket.receive(packet);
		
		return new String(packet.getData());
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		
		String lookup = lookup(username);		
		String[] parts = lookup.split(":");

		try{  Integer.parseInt(parts[1]); }
		catch(NumberFormatException e) {
			return lookup;
		}
		
		privateSocket = new Socket(config.getString("chatserver.host"), Integer.parseInt(parts[1]));
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(privateSocket.getInputStream()));
		PrintWriter writer = new PrintWriter(privateSocket.getOutputStream(), true);
		
		writer.println(message);
				
		return username + " replied with " + reader.readLine();
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		
		clientRequest.println("lookup_" + username);
		return getServerResponse("lookup");
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {

		clientRequest.println("register_" + privateAddress);
		
		String response = getServerResponse("register");
		if(response.startsWith("Success")) {
			String[] parts = privateAddress.split(":");
			
			serverSocket = new ServerSocket(Integer.parseInt(parts[1]));
			threadPool.execute(new PrivateMessageListener(this, serverSocket));
		}
			
		return response;
	}
	
	@Override
	@Command
	public String lastMsg() throws IOException {
		if(lastMessage == null)
			return "No message recived!";
		else
			return lastMessage;
	}

	@Override
	@Command
	public String exit() throws IOException {
		logout();
		socket.close();
		threadPool.shutdown();
		clientRequest.close();
		serverResponse.close();
		userRequestStream.close();
		userResponseStream.close();
		return "shutdown client";
	}
	
	/**
	 * prints private message from other client and closes private socket
	 */
	public void writePrivateMessage(String message) throws IOException {
		shell.writeLine(message);
		serverSocket.close();
	}
	
	public void writePublicMessage(String message) throws IOException {
		lastMessage = message;
		shell.writeLine(message);
	}
	
	private String getServerResponse(String command) {
        try { Thread.sleep(100); } 
        catch (InterruptedException ex) { }

		return responseListener.getResponse(command);
    }
	
	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
		// TODO: start the client
		new Thread(client).start();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
