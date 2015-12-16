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
	
	private String user = null;
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

		// TODO
		
		
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
	}

	@Override
	public void run() {
		threadPool.execute(new Thread(shell));
		
		/*while(true) {
			try {
				if(serverResponse.ready()) {
					//clientRequest.println(serverResponse.readLine());
					String response = serverResponse.readLine();
					//System.out.println(response);
					shell.writeLine(response);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				System.out.println("error");
				break;
			}
		}*/
		
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {		
		
		clientRequest.println("login_" + username + "_" + password);
		
		String response = serverResponse.readLine();
		if (response.startsWith("Success")) {
			user = username;
		}
		return response;
	}

	@Override
	@Command
	public String logout() throws IOException {
		
		if(user == null)
			return "User must be logged in.";
		
		clientRequest.println("logout_" + user);
			
		String response = serverResponse.readLine();
		if (response.startsWith("Success")) {
			user = null;
		}
		return response;	
	}

	@Override
	@Command
	public String send(String message) throws IOException {
			
		clientRequest.println("send_" + message);
		//return serverResponse.readLine();
		return "";
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
		
		String receiver = lookup(username);  //todo
		String[] parts = receiver.split(":");
		
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
		return serverResponse.readLine();
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {

		clientRequest.println("register_" + user + "_" + privateAddress);
			
		String[] parts = privateAddress.split(":");
			
		serverSocket = new ServerSocket(Integer.parseInt(parts[1]));
		threadPool.execute(new PrivateMessageListener(this, serverSocket));
			
		return serverResponse.readLine();	
	}
	
	/**
	 * prints private message from other client and closes private socket
	 */
	public void receivePrivateMessage(String message) throws IOException {
		shell.writeLine(message);
		serverSocket.close();
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
		// TODO Auto-generated method stub
		logout();
		socket.close();
		//System.out.println(socket);
		threadPool.shutdown();
		//shell.close();
		clientRequest.close();
		serverResponse.close();
		userRequestStream.close();
		userResponseStream.close();
		return "shutdown client";
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
