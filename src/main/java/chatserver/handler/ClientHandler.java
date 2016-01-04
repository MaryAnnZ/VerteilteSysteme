package chatserver.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import channel.AESreader;
import channel.AESwriter;
import channel.RSAreader;
import channel.RSAwriter;
import chatserver.UserMap;
import chatserver.listener.TcpListener;
import util.CompareKeys;
import util.Config;
import util.Keys;

public class ClientHandler implements Runnable {

	private Socket socket;
	private UserMap users;
	private RSAreader readerRSA;
	private RSAwriter writerRSA;
	private AESreader readerAES;
	private AESwriter writerAES;

	private int id;
	private ConcurrentHashMap<Integer, ClientHandler> connections;
	private BufferedReader myBufferedReaderRSA;
	private PrintWriter myPrintWriterRSA;

	private String username;
	private boolean isSender = false;

	private Config config;
	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;
	private Cipher cipherAESencode;
	private Cipher cipherAESdecode;

	private SecureRandom sRand;
	private boolean aut;

	public ClientHandler(Socket socket, UserMap users, ConcurrentHashMap<Integer, ClientHandler> connections, int id, Config config, Cipher cipherRSApublic, Cipher cipherRSAprivate, Cipher cipherAESencode, Cipher cipherAESdecode) throws IOException {
		this.socket = socket;
		this.users = users;
		this.connections = connections;
		this.id = id;
		

		this.myBufferedReaderRSA = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		this.myPrintWriterRSA = new PrintWriter(socket.getOutputStream(), true);

		this.config = config;
		this.cipherRSApublic = cipherRSApublic;
		this.cipherRSAprivate = cipherRSAprivate;
		this.cipherAESencode = cipherAESencode;
		this.cipherAESdecode = cipherAESdecode;

		aut = false;
	}

	@Override
	public void run() {
		try {

			readerRSA = new RSAreader(myBufferedReaderRSA, cipherRSAprivate);

			sRand = new SecureRandom();
			byte[] challenge = new byte[32];
			sRand.nextBytes(challenge);
			
			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256);
			SecretKey secretKey =generator.generateKey();

			byte[] iv = new byte[16];
			sRand.nextBytes(iv);

			String request;
			String usernameTemp = "";
			boolean readRSA = true;
			
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			SecretKey secretKeySpec  = new SecretKeySpec(secretKey.getEncoded(), 0,  new String(secretKey.getEncoded()).length(), "AES");
			// read client requests
			try {
				while (readRSA && ((request = readerRSA.readLine()) != null)) {
					String[] splitted = request.split("___");
					if (splitted.length != 3) {
						System.out.println("Communication failed");
						this.socket.close();
						connections.remove(id);
					} else {
						String response = "!ok___" + splitted[2] + "___" + new String(Base64.encode(challenge)) + "___" + new String(Base64.encode(secretKey.getEncoded()))  + "___" + new String(Base64.encode(iv));
						String fileDir = config.getString("keys.dir") + "/" + splitted[1] + ".pub.pem";
						cipherRSApublic.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(fileDir)));
						writerRSA = new RSAwriter(myPrintWriterRSA, cipherRSApublic);
						usernameTemp = splitted[1];
						cipherAESencode.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
						cipherAESdecode.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
						readerAES = new AESreader(myBufferedReaderRSA, cipherAESdecode);
						writerAES = new AESwriter(myPrintWriterRSA, cipherAESencode);
						writerRSA.println(response);
						readRSA = false;
						break;
					}
				}
				System.out.println("waiting for commands");
				readerAES = new AESreader(myBufferedReaderRSA, cipherAESdecode);
				writerAES = new AESwriter(myPrintWriterRSA, cipherAESencode);
				while ((request = readerAES.readLine()) != null) {
					readerAES = new AESreader(myBufferedReaderRSA, cipherAESdecode);
					writerAES = new AESwriter(myPrintWriterRSA, cipherAESencode);
					System.out.println("Client sent the following request: " + request);

					String[] parts = request.split("___");

					String response = "";

					String command = parts[0];

					if (aut) {
						// client requests
						if(command.equals("login")) {

							if(parts.length != 3) {
								response = "login invalid parameters";
							} else {
								response = users.login(parts[1], parts[2]);
								if(response.startsWith("Success"))
									this.username = parts[1];
								response = "login " + response;
							}

						}
						else if(command.equals("logout")) {

							if(parts.length != 1) {
								response = "logout invalid parameters";
							} else if(username == null) {
								response = "logout User must be logged in.";
							} else {
								response = users.logout(username);
								if(response.startsWith("Success"))
									this.username = null;
								response = "logout " + response;
							}

						}
						else if(command.equals("send")) {

							if(parts.length != 2) {
								response = "send invalid parameters";
							} else if(username == null) {
								response = "send User must be logged in.";
							} else {
								isSender = true;
								TcpListener.sendMessageToClients(parts[1]);
								response = "send Message was successfully send.";
							}

						}
						else if(command.equals("register")) {

							if(parts.length != 2) {
								response = "register invalid parameters";
							} else if(username == null) {
								response = "register User must be logged in.";
							} else {
								response = "register " + users.registerPort(username, parts[1]);
							}

						}
						else if(command.equals("lookup")) {

							if(parts.length != 2) {
								response = "lookup invalid parameters";
							} else if(username == null) {
								response = "lookup User must be logged in.";
							} else
								response = "lookup " + users.lookUpPort(parts[1]);					

						} else {
							response = "Unknown command.";
						}
					} else {
						if (parts.length == 1) {
							System.out.println("Errors: " + CompareKeys.error(challenge,Base64.decode(parts[0].getBytes())));
							if (CompareKeys.comapre(challenge, parts[0].getBytes())) {
								response = users.login(usernameTemp);
								System.out.println(response);
								if(response.startsWith("Succesfully")) {
									this.username = usernameTemp;
									response = "login " + response;
									aut = true;
									writerAES.println("Authentication successful");
								} else {
									//TODO error msg, feedback
									System.out.println("Communication error 1");
									this.socket.close();
									connections.remove(id);
								}
							} else {
								//TODO error msg
								System.out.println("Communication error 2");
								this.socket.close();
								connections.remove(id);
							}
						} else {
							//TODO error msg
							for (int i = 0; i < parts.length; i++) {
								System.out.println("Parts " + i + " " + parts[i]);
							}
							System.out.println("Communication error 3");
							this.socket.close();
							connections.remove(id);
						}
					} 

					// print request
					writerAES.println(response);
				}
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch(IOException e) {

		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			try {
				if (!this.socket.isClosed()) {
					this.socket.close();
					connections.remove(id);
				}
			} catch (IOException ex) { }
		}

	}

	/**
	 * sends public message to client
	 */
	public void sendMessage(String message) {

		if(isSender)
			try {
				writerAES.println("public " + message);
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		else
			isSender = false;
	}
}
