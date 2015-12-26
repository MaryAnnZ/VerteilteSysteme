package chatserver.listener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.crypto.Cipher;

import chatserver.UserMap;
import chatserver.handler.ClientHandler;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TcpListener extends Thread {

	private ServerSocket serverSocket;
	private UserMap users;
	private final ExecutorService threadPool;
	
	private int id;
	private static ConcurrentHashMap<Integer, ClientHandler> connections;
	
	private boolean stopped = false;
	
	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;
	private Cipher cipherAESencode;
	private Cipher cipherAESdecode;

	public TcpListener(ServerSocket serverSocket, UserMap users, ExecutorService threadPool, Cipher cipherRSApublic, Cipher cipherRSAprivate, Cipher cipherAESencode, Cipher cipherAESdecode) {
		this.serverSocket = serverSocket;
		this.users = users;
		this.threadPool = threadPool;
		this.connections = new ConcurrentHashMap<Integer, ClientHandler>();
		
		this.cipherRSApublic = cipherRSApublic;
		this.cipherRSAprivate = cipherRSAprivate;
		this.cipherAESencode = cipherAESencode;
		this.cipherAESdecode = cipherAESdecode;
		
		id = 0;
	}
	
	public void run() {
		while (!stopped) {
			try {
				
				ClientHandler client = new ClientHandler(serverSocket.accept(), users, connections, id,cipherRSApublic, cipherRSAprivate, cipherAESencode, cipherAESdecode);
				connections.put(id, client);
				id++;
				
				threadPool.execute(client);

			} catch (IOException e) {
				stopped = true;
				/*System.err.println("Error occurred while waiting for/communicating with client: "
						+ e.getMessage());*/
			}
		}

		if (stopped) {
			try {
				serverSocket.close();
				connections.clear();
			} catch (IOException e) {
				// Ignored because we cannot handle it
			}
			threadPool.shutdownNow();
		}
	}
	
	/**
	 * sends public message to all clients
	 */
	public static void sendMessageToClients(String message) {
		for (ClientHandler c : connections.values()) {
			c.sendMessage(message);
		}
	}
}
