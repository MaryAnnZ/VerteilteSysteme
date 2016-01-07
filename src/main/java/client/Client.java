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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
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
import javax.crypto.Mac;
import org.bouncycastle.util.encoders.Base64;

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
	final byte[] challenge = new byte[32];
	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;
	private Cipher cipherAESencode;
	private Cipher cipherAESdecode;

	private BufferedReader myBufferedReaderRSA;
	private PrintWriter myPrintWriterRSA;

	private boolean loggedInYet = false;
	
	public boolean running = true;

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

			myBufferedReaderRSA = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			myPrintWriterRSA = new  PrintWriter(socket.getOutputStream(), true);

			cipherRSApublic = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipherRSApublic.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(config.getString("chatserver.key"))));
			cipherRSAprivate = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipherAESencode = Cipher.getInstance("AES/CTR/NoPadding");
			cipherAESdecode = Cipher.getInstance("AES/CTR/NoPadding");
			writerRSA = new RSAwriter(myPrintWriterRSA, cipherRSApublic) ;
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
	}

	@Override
	public void run() {
		threadPool.execute(new Thread(shell));
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {		
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("login___" + username + "___" + password);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
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
				writerAES.println("logout___");
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
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
				writerAES.println("send___" + message);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
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
		System.out.println("LOOKU1P: " + lookup);
		String[] parts = lookup.split(":");

		try{  Integer.parseInt(parts[1]); }
		catch(NumberFormatException e) {
			return lookup;
		}

		privateSocket = new Socket(config.getString("chatserver.host"), Integer.parseInt(parts[1]));

		BufferedReader reader = new BufferedReader(new InputStreamReader(privateSocket.getInputStream()));
		PrintWriter writer = new PrintWriter(privateSocket.getOutputStream(), true);

		// create and encode hmac for message
		byte [] hash = Base64.encode(createHMAC("!msg" + message));		
		writer.println(new String(hash) + "_!msg_" + message);
		
		// get response and vertify message wasnt changed	
		parts = reader.readLine().split("_");
		
		byte [] receivedHash = Base64.decode(parts[0].getBytes());
		String receivedCommand = parts[1];
		String receivedMessage = parts[2];
		
		// create new hmac to vertify
		byte[] computedHash = createHMAC(receivedCommand + receivedMessage);
		
		boolean validHash = MessageDigest.isEqual(computedHash, receivedHash);
		
		
		if(validHash && receivedCommand.equals("!ack")) {
			return username + " replied with " + receivedCommand;
		}
		else if(validHash && receivedCommand.equals("!tampered")) {
			return username + " recieved tampered message!";
		}
		else if(!validHash && receivedCommand.equals("!ack")) {
			return "response from " + username + " was tampered!";
		}
		else {
			return "entire conversation with " + username + " was tampered!";
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("lookup___" + username);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String lookup = getServerResponse("lookup");
			System.out.println("LOOKU2P: " + lookup);
			//return getServerResponse("lookup");
			return lookup;
		}
		return "no AES channel";
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (readerAES != null && writerAES != null) {
			try {
				writerAES.println("register___" + privateAddress);
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			String response = getServerResponse("register");
			if(response.startsWith("Success")) {
				String[] parts = privateAddress.split(":");

				serverSocket = new ServerSocket(Integer.parseInt(parts[1]));
			threadPool.execute(new PrivateMessageListener(this, serverSocket, Keys.readSecretKey(new File(config.getString("hmac.key")))));
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
		running = false;
		try{
			logout();
		} catch(NullPointerException e){
			;
		}
		if (rsaListener != null)
			rsaListener.close();
		if (aesListener != null)
			aesListener.close();
		if (writerAES != null)
			writerAES.close();
		if (readerAES != null)
			readerAES.close();
		if (writerRSA != null)
			writerRSA.close();
		if (readerRSA != null)
			readerRSA.close();
		if (myBufferedReaderRSA != null)
			myBufferedReaderRSA.close();
		if (myPrintWriterRSA != null)
			myPrintWriterRSA.close();
		if (socket != null)
			socket.close();
		if (threadPool != null)
			threadPool.shutdownNow();
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

	public String handleServerResponseAut() throws InvalidKeyException, InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException, BadPaddingException {
		String serverResponse;
		if ((serverResponse = rsaListener.getResponse("authenticate")) != null) {
			// check challenge
			String[] splitted = serverResponse.split("___");
			final String cal =  new String(challenge);
			if (splitted.length != 5 ||  !Arrays.equals(challenge, Base64.decode(splitted[1].getBytes()))) {
				System.out.println(splitted.length);
				System.out.println("Errors: " );//+ CompareKeys.error(challenge, splitted[0].getBytes()));
				System.out.println(new String(Base64.encode(challenge)));
				System.out.println(new String(splitted[1].getBytes()));
				return "Connection failed";
			}
			String stringKey = splitted[3];
			String stringIV = splitted[4];
			SecretKey secretKey = new SecretKeySpec(Base64.decode(stringKey), 0, Base64.decode(stringKey).length, "AES");			
			IvParameterSpec iv = new IvParameterSpec(Base64.decode(stringIV));
			cipherAESencode.init(Cipher.ENCRYPT_MODE, secretKey, iv);
			cipherAESdecode.init(Cipher.DECRYPT_MODE, secretKey, iv);
			readerAES = new AESreader(myBufferedReaderRSA, cipherAESdecode, secretKey, iv);
			writerAES = new AESwriter(myPrintWriterRSA, cipherAESencode, secretKey, iv);
			aesListener = new ServerResponseListenerAES(this, readerAES);
			threadPool.execute(new Thread(aesListener));
			writerAES.println(splitted[2]);
			loggedInYet = true;
			return "Authentication successfull";
		}
		return "Connection failed";
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

	@Command
	@Override
	public String authenticate(String username) throws IOException {
		//generate challenge

		srand = new SecureRandom();
		srand.nextBytes(challenge);

		try {
			if (!loggedInYet) {
			String fileDir = config.getString("keys.dir") + "/" + username + ".pem";
			cipherRSAprivate.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(fileDir)));
			readerRSA = new RSAreader( myBufferedReaderRSA, cipherRSAprivate);
			rsaListener = new ServerResponseListenerRSA(this, readerRSA);
			threadPool.execute(rsaListener);
			}
			writerRSA.println("authenticate___" + username + "___" + new String(Base64.encode(challenge)));
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
