package chatserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import chatserver.listener.TcpListener;
import chatserver.listener.UdpListener;
import cli.Command;
import cli.Shell;
import util.Config;
import util.Keys;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	
	private UserMap users;
	
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private final ExecutorService threadPool;
	
	private Shell shell;
	
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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		
		// read user data and store them in userList
		Config userData = new Config("user");
		users = new UserMap();
		
		for(String s : userData.listKeys()) {
			User user = new User();
			
			String [] split = s.split("\\.");
			String userName = split[0];
			
			for(int i = 1; i < split.length; i++) {
				if(!split[i].equals("password"))
					userName += "." + split[i];
			}
			user.setName(userName);
			user.setPassword(userData.getString(s));
			user.setOnline(false);
			users.put(userName, user);
			
			try {
				cipherRSApublic = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
				//cipherRSApublic.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(config.getString("keys.dir"))));
				cipherRSAprivate = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
				cipherRSAprivate.init(Cipher.DECRYPT_MODE, Keys.readPrivatePEM(new File(config.getString("key"))));
				cipherAESencode = Cipher.getInstance("AES/CTR/NoPadding");
				cipherAESdecode = Cipher.getInstance("AES/CTR/NoPadding");
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		// creates new TCP ServerSocket and new UDP DatagramSocket
		try {
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
			datagramSocket = new DatagramSocket(config.getInt("udp.port"));
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on TCP/UDP port.", e);
		}
		
		// thread pool creates new threads as needed and reuse previous threads
		threadPool = Executors.newCachedThreadPool();
		
		// shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		
	}

	@Override
	public void run() {
		
		// start threads
		threadPool.execute(new TcpListener(serverSocket, users, threadPool, config, cipherRSApublic, cipherRSAprivate, cipherAESencode, cipherAESdecode));
		threadPool.execute(new UdpListener(datagramSocket, users, threadPool));
		
		threadPool.execute(new Thread(shell));
	}

	@Override
	@Command
	public String users() throws IOException {
		return users.listUsers();
	}

	@Override
	@Command
	public synchronized String exit() throws IOException {
		
		serverSocket.close();
		datagramSocket.close();
		
		userRequestStream.close();
		userResponseStream.close();
		
		threadPool.shutdown();
		
		return "shutdown " + componentName;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
			
		// start the chatserver
		new Thread((Runnable) chatserver).start();
	}

}
