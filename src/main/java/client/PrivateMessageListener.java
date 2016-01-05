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
import org.bouncycastle.util.encoders.Base64;

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
				
				// create new hmac to vertify
				byte[] computedHash = createHMAC(command + message);
				
				// print response
				byte[] hash;
				if(MessageDigest.isEqual(computedHash, receivedHash)) {
					hash = Base64.encode(createHMAC("!ack" + message));
					writer.println(new String(hash) + "_!ack_" + message);
				} else {
					hash = Base64.encode(createHMAC("!tampered" + message));
					writer.println(new String(hash) + "_!tampered_" + message);
				}
				
				client.writePrivateMessage(message);
				break;

			} catch (IOException e) {
				/*System.err
						.println("Error occurred while waiting for/communicating with client: "
								+ e.getMessage());*/
				break;
			} finally {
				if (socket != null && !socket.isClosed()) {
					try {
						socket.close();
					} catch (IOException e) {
						// Ignored because we cannot handle it
					}
				}
			}
		}
	}
	
	private byte[] createHMAC(String message) throws IOException {
		
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

}
