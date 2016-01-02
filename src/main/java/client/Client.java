package client;

import java.io.BufferedReader;
import java.io.File;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.util.encoders.Base64;

import channel.RSAreader;
import channel.RSAwriter;
import chatserver.listener.TcpListener;
import cli.Command;
import cli.Shell;
import util.Config;
import util.Keys;

import java.security.Key;
import java.security.MessageDigest;

import javax.crypto.Mac;

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
	private RSAwriter RSAwriter;
	private RSAreader RSAreader;
	private DatagramPacket packet;
	
	private ServerSocket serverSocket;
	private Socket privateSocket;
	
	private Shell shell;
	private ServerResponseListenerAES aesListener;
	private ServerResponseListenerRSA rsaListener;
	
	private String lastMessage = null;
	
	private SecureRandom srand;
	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;

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
			
			cipherRSApublic = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipherRSApublic.init(Cipher.ENCRYPT_MODE, Keys.readPrivatePEM(new File(config.getString("chatserver.key"))));
			cipherRSAprivate = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipherRSAprivate.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(config.getString("keys.dir"))));
			
			serverResponse = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			clientRequest = new  PrintWriter(socket.getOutputStream(), true);
			RSAwriter = new RSAwriter(new PrintWriter(socket.getOutputStream(), true), cipherRSApublic) ;
			RSAreader = new RSAreader( new BufferedReader(new InputStreamReader(socket.getInputStream())), cipherRSAprivate);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// thread pool creates new threads as needed and reuse previous threads
		threadPool = Executors.newCachedThreadPool();
		
		// shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		
		// server response listener
		aesListener = new ServerResponseListenerAES(this, serverResponse);
		rsaListener = new ServerResponseListenerRSA(this, RSAreader);
		
	}

	@Override
	public void run() {
		threadPool.execute(new Thread(shell));
		threadPool.execute(new Thread(aesListener));
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
		
		// create and encode hmac for message
		byte [] hash = Base64.encode(createHMAC(message));
		writer.println(hash + "_!msg_" + message);
		
		// get response and vertify message wasnt changed	
		parts = reader.readLine().split("_");
		
		byte [] receivedHash = Base64.decode(parts[0].getBytes());
		String receivedCommand = parts[1];
		String receivedMessage = parts[2];
		
		// create new hmac to vertify
		byte[] computedHash = createHMAC(receivedMessage);
		
		boolean validHash = MessageDigest.isEqual(computedHash, receivedHash);
		
		
		if(validHash && receivedCommand.equals("!ack")) {
			return username + " replied with " + receivedCommand;
		}
		else if(validHash && receivedCommand.equals("!tampered")) {
			return username + " recieved tampered message!";
		}
		else if(!validHash && receivedCommand.equals("!ack")) {
			return "response from " + username + "was tampered!";
		}
		else {
			return "entire conversation with " + username + " was tampered!";
		}
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
			threadPool.execute(new PrivateMessageListener(this, serverSocket, Keys.readSecretKey(new File(config.getString("hmac.key")))));
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
		RSAwriter.close();
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
        
		return aesListener.getResponse(command);
    }
	
	private String handleServerResponseAut() {
		try { Thread.sleep(100); } 
        catch (InterruptedException ex) { }
		String serverResponse = rsaListener.getResponse("authenticate");
		// check challenge
		String[] splitted = serverResponse.split(" ");
		if (!splitted[1].equals(srand)) {
			//TODO error msg
			return null;
		}
		// TODO implement AES communication
		return null;
	}
	
	private byte[] createHMAC(String message) throws IOException {
		
		// read the shared secret key
		Key secretKey = Keys.readSecretKey(new File(config.getString("hmac.key")));
		
		// create HMAC
		Mac hMac;
		byte[] hash = null;
		
		try {
			hMac = Mac.getInstance("HmacSHA256");
			hMac.init(secretKey);
			hMac.update(message.getBytes());
			hash = hMac.doFinal();
			
		} catch (NoSuchAlgorithmException e) { e.printStackTrace();
		} catch (InvalidKeyException e) { e.printStackTrace(); }
		
		return hash;
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
	@Command
	public String authenticate(String username) throws IOException {
		//generate challenge

		srand = new SecureRandom();
		final byte[] challenge = new byte[32];
		srand.nextBytes(challenge);
		
		try {
			RSAwriter.println("authenticate " + username + " " + challenge);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return handleServerResponseAut();
	}

}
