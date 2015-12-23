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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import channel.AESreader;
import channel.AESwriter;
import channel.RSAreader;
import channel.RSAwriter;
import chatserver.listener.TcpListener;
import cli.Command;
import cli.Shell;
import util.Config;
import util.Keys;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private final ExecutorService threadPool;
	private DatagramSocket datagramSocket;
	private Socket socket;
	private AESreader readerAES;
	private AESwriter writerAES;
	private RSAwriter writerRSA;
	private RSAreader readerRSA;
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
	private Cipher cipherAESencode;
	private Cipher cipherAESdecode;

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
			cipherAESencode = Cipher.getInstance("AES/CTR/NoPadding");
			cipherAESdecode = Cipher.getInstance("AES/CTR/NoPadding");
			//serverResponse = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			//clientRequest = new  PrintWriter(socket.getOutputStream(), true);
			writerRSA = new RSAwriter(new PrintWriter(socket.getOutputStream(), true), cipherRSApublic) ;
			readerRSA = new RSAreader( new BufferedReader(new InputStreamReader(socket.getInputStream())), cipherRSAprivate);
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
		aesListener = new ServerResponseListenerAES(this, readerAES);
		rsaListener = new ServerResponseListenerRSA(this, readerRSA);

	}

	@Override
	public void run() {
		threadPool.execute(new Thread(shell));
		threadPool.execute(new Thread(aesListener));
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {		
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("login " + username + " " + password);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return getServerResponse("login");	
		}
		return "no AES channel";
	}

	@Override
	@Command
	public String logout() throws IOException {
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("logout ");
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return getServerResponse("logout");
		}
		return "no AES channel";
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if (readerAES != null && writerAES != null) {	
			try {
				writerAES.println("send " + message);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return getServerResponse("send");
		}
		return "no AES channel";
	}

	@Override
	@Command
	public String list() throws IOException {
		if (readerAES != null && writerAES != null) {
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
		return "no AES channel";
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
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("lookup " + username);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return getServerResponse("lookup");
		}
		return "no AES channel";
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("register " + privateAddress);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			String response = getServerResponse("register");
			if(response.startsWith("Success")) {
				String[] parts = privateAddress.split(":");

				serverSocket = new ServerSocket(Integer.parseInt(parts[1]));
				threadPool.execute(new PrivateMessageListener(this, serverSocket));
			}

			return response;
		}
		return "no AES channel";
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
		writerAES.close();
		readerAES.close();
		writerRSA.close();
		readerRSA.close();
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

	private String handleServerResponseAut() throws InvalidKeyException, InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException, BadPaddingException {
		try { Thread.sleep(100); } 
		catch (InterruptedException ex) { }
		String serverResponse = rsaListener.getResponse("authenticate");
		// check challenge
		String[] splitted = serverResponse.split(" ");
		if (splitted.length != 5 ||  !splitted[1].equals(srand.toString())) {
			return "Connection failed";
		}
		String stringKey = splitted[3];
		String stringIV = splitted[4];
		byte[] encodedKey = Base64.decode(stringKey);
		SecretKey secretKey = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
		IvParameterSpec iv = new IvParameterSpec(stringIV.getBytes());
		cipherAESencode.init(Cipher.ENCRYPT_MODE, secretKey, iv);
		cipherAESencode.init(Cipher.DECRYPT_MODE, secretKey, iv);
		readerAES = new AESreader(new BufferedReader(new InputStreamReader(socket.getInputStream())), cipherAESdecode);
		writerAES = new AESwriter( new  PrintWriter(socket.getOutputStream(), true), cipherAESencode);
		writerAES.println(splitted[2]);
		return "Authentication successfull";
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
		//generate challenge

		srand = new SecureRandom();
		final byte[] challenge = new byte[32];
		srand.nextBytes(challenge);

		try {
			writerRSA.println("authenticate " + username + " " + challenge);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			return handleServerResponseAut();
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "no AES channel";
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "no AES channel";
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "no AES channel";
		}
	}

}
