package chatserver.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import channel.AESreader;
import channel.AESwriter;
import channel.RSAreader;
import channel.RSAwriter;
import chatserver.UserMap;
import chatserver.listener.TcpListener;
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

	private String username;
	private boolean isSender = false;

	private Config config;
	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;
	private Cipher cipherAESencode;
	private Cipher cipherAESdecode;

	private SecureRandom sRand;
	private boolean aut;

	public ClientHandler(Socket socket, UserMap users, ConcurrentHashMap<Integer, ClientHandler> connections, int id, Config config, Cipher cipherRSApublic, Cipher cipherRSAprivate, Cipher cipherAESencode, Cipher cipherAESdecode) {
		this.socket = socket;
		this.users = users;
		this.connections = connections;
		this.id = id;

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

			readerRSA = new RSAreader(new BufferedReader(new InputStreamReader(socket.getInputStream())), cipherRSAprivate);
			//writerRSA = new RSAwriter(new PrintWriter(socket.getOutputStream(), true), cipherRSApublic);

			sRand = new SecureRandom();
			byte[] challenge = new byte[32];
			sRand.nextBytes(challenge);
			byte[] secretKey = new byte[32];
			sRand.nextBytes(secretKey);
			byte[] iv = new byte[16];
			sRand.nextBytes(iv);

			String request;
			String usernameTemp = "";
			// read client requests
			try {
				while ((request = readerRSA.readLine()) != null) {
					String[] splitted = request.split(" ");
					if (splitted.length != 3) {
						this.socket.close();
						connections.remove(id);
					} else {
						String response = "ok! " + splitted[2] + " " + challenge + " " + secretKey  + " " 
								+ iv;
						String fileDir = config.getString("keys.dir") + "/" + splitted[1] + ".pub.pem";
						cipherRSApublic.init(Cipher.ENCRYPT_MODE, Keys.readPublicPEM(new File(fileDir)));
						writerRSA = new RSAwriter(new PrintWriter(socket.getOutputStream(), true), cipherRSApublic);
						writerRSA.println(response);
						usernameTemp = splitted[1];
						readerAES = new AESreader(new BufferedReader(new InputStreamReader(socket.getInputStream())), cipherAESdecode);
						writerAES = new AESwriter(new PrintWriter(socket.getOutputStream(), true), cipherAESencode);
					}
				}

				while ((request = readerAES.readLine()) != null) {

					//System.out.println("Client sent the following request: " + request);

					String[] parts = request.split(" ");

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
							if (parts[0].equals(challenge)) {
								response = users.login(usernameTemp);
								if(response.startsWith("Success")) {
									this.username = usernameTemp;
									response = "login " + response;
									aut = true;
								} else {
									//TODO error msg, feedback
									System.out.println("Communication error");
									this.socket.close();
									connections.remove(id);
								}
							} else {
								//TODO error msg
								System.out.println("Communication error");
								this.socket.close();
								connections.remove(id);
							}
						} else {
							//TODO error msg
							System.out.println("Communication error");
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
			}
		} catch(IOException e) {

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

		if(!isSender)
			try {
				writerRSA.println("public " + message);
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
