package chatserver.listener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import chatserver.UserMap;
import chatserver.handler.ClientHandler;
import nameserver.INameserver;
import util.Config;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TcpListener extends Thread {

	private ServerSocket serverSocket;
	private UserMap users;
	private INameserver nameserver;

	private final ExecutorService threadPool;
	private Config config;

	private int id;
	private static ConcurrentHashMap<Integer, ClientHandler> connections;

	public boolean stopped = false;

	private Cipher cipherRSApublic;
	private Cipher cipherRSAprivate;

	public TcpListener(ServerSocket serverSocket, UserMap users, INameserver nameserver, ExecutorService threadPool,
			Config config, Cipher cipherRSApublic, Cipher cipherRSAprivate) {
		this.serverSocket = serverSocket;
		this.users = users;
		this.nameserver = nameserver;
		this.threadPool = threadPool;
		this.connections = new ConcurrentHashMap<Integer, ClientHandler>();

		this.config = config;
		this.cipherRSApublic = cipherRSApublic;
		this.cipherRSAprivate = cipherRSAprivate;

		id = 0;
	}

	public void run() {
		while (!stopped) {
			try {
				
				ClientHandler client = new ClientHandler(serverSocket.accept(), users, nameserver, connections, id, config, cipherRSApublic, cipherRSAprivate, Cipher.getInstance("AES/CTR/NoPadding"), Cipher.getInstance("AES/CTR/NoPadding"));
				connections.put(id, client);
				id++;
				
				threadPool.execute(client);

			} catch (IOException e) {
				stopped = true;
				/*System.err.println("Error occurred while waiting for/communicating with client: "
						+ e.getMessage());*/
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (stopped) {
			try {
				serverSocket.close();
				for(ClientHandler ch : connections.values()){
					if(ch!=null)
						ch.close();
				}
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
	public static synchronized void sendMessageToClients(String message) {
		for (ClientHandler c : connections.values()) {
			c.sendMessage(message);
		}
	}
}
