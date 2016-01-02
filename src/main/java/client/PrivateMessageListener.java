package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

public class PrivateMessageListener extends Thread {
	
	private Client client;
	private ServerSocket serverSocket;
	private final Key secretKey;
	
	public PrivateMessageListener(Client client, ServerSocket serverSocket, Key secretKey) {
		this.client = client;
		this.serverSocket = serverSocket;
		this.secretKey = secretKey;
	}
	
	public void run() {
		
		while (true) {
			Socket socket = null;
			
			try {
				// wait for Client to connect
				socket = serverSocket.accept();
				
				// prepare the input reader for the socket
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream()));
				// prepare the writer for responding to clients requests
				PrintWriter writer = new PrintWriter(socket.getOutputStream(),
						true);

				// read client message				
				String[] parts = reader.readLine().split("_");
				
				byte [] receivedHash = Base64.decode(parts[0].getBytes());
				String command = parts[1];
				String message = parts[2];
				
				if(command.equals("!msg")) {
					
					// create new hmac to vertify
					Mac hMac;
					byte[] computedHash = null;
					
					try {
						hMac = Mac.getInstance("HmacSHA256");
						hMac.init(secretKey);
						hMac.update(message.getBytes());
						computedHash = hMac.doFinal();
						
					} catch (NoSuchAlgorithmException e) { e.printStackTrace(); 
					} catch (InvalidKeyException e) { e.printStackTrace(); }
					
					// print response
					if(MessageDigest.isEqual(computedHash, receivedHash)) {
						writer.println(Base64.encode(computedHash) + "_!ack_" + message);
					} else {
						writer.println(Base64.encode(computedHash) + "_!tampered_" + message);
					}
					
					client.writePrivateMessage(message);
					break;
				}

			} catch (IOException e) {
				/*System.err
						.println("Error occurred while waiting for/communicating with client: "
								+ e.getMessage());*/
				break;
			} catch (Base64DecodingException e1) {
				e1.printStackTrace();
			} finally {
				if (socket != null && !socket.isClosed())
					try {
						socket.close();
					} catch (IOException e) {
						// Ignored because we cannot handle it
					}

			}

		}
	}

}
