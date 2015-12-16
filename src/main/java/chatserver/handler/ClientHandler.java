package chatserver.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import chatserver.UserMap;
import chatserver.listener.TcpListener;

public class ClientHandler implements Runnable {
	
	private Socket socket;
	private UserMap users;
	private BufferedReader reader;
	private PrintWriter writer;
	
	private int id;
	private ConcurrentHashMap<Integer, ClientHandler> connections;
	
	private boolean isSender = false;
	
	public ClientHandler(Socket socket, UserMap users, ConcurrentHashMap<Integer, ClientHandler> connections, int id) {
		this.socket = socket;
		this.users = users;
		this.connections = connections;
		this.id = id;
	}

	@Override
	public void run() {
		try {
			
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);
			
			
			String request;
			// read client requests
			while ((request = reader.readLine()) != null) {

				//System.out.println("Client sent the following request: " + request);

				String[] parts = request.split("_");

				String response = "";
				
				String command = parts[0];
				
				
				// client requests
				if(command.equals("login")) {
					
					if(parts.length != 3)
						response = "invalid parameters";
					else
						response = users.login(parts[1], parts[2]);
					
				}
				else if(command.equals("logout")) {
					
					if(parts.length != 2)
						response = "invalid parameters";
					else
						response = users.logout(parts[1]);
					
				}
				else if(command.equals("send")) {
					
					if(parts.length != 2)
						response = "invalid parameters";
					else {
						isSender = true;
						TcpListener.sendMessageToClients(parts[1]);
					}
					
				}
				else if(command.equals("register")) {
					
					if(parts.length != 3)
						response = "invalid parameters";
					else
						response = users.registerPort(parts[1], parts[2]);
					
				}
				else if(command.equals("lookup")) {
					
					if(parts.length != 2)
						response = "invalid parameters";
					else
						response = users.lookUpPort(parts[1]);					
					
				} else {
					response = "Unknown command.";
				}
				
				
				// print request
				writer.println(response);
			}
		} catch(IOException e) {
			connections.remove(id);
		}	
		
	}
	
	/**
	 * sends public message to client
	 */
	public void sendMessage(String message) {
		
		if(!isSender)
			writer.println(message);
		else
			isSender = false;
	}
}
