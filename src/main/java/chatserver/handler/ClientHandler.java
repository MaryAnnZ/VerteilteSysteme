package chatserver.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.DomainCombiner;
import java.util.concurrent.ConcurrentHashMap;

import chatserver.UserMap;
import chatserver.listener.TcpListener;
import nameserver.INameserver;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class ClientHandler implements Runnable {
	
	private Socket socket;
	private UserMap users;
	private BufferedReader reader;
	private PrintWriter writer;
	private INameserver nameserver;
	
	private int id;
	private ConcurrentHashMap<Integer, ClientHandler> connections;
	
	private String username;
	private boolean isSender = false;
	
	public ClientHandler(Socket socket, UserMap users, INameserver nameserver, ConcurrentHashMap<Integer, ClientHandler> connections, int id) {
		this.socket = socket;
		this.users = users;
		this.nameserver = nameserver;
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
					
					if(parts.length != 3) {
						response = "login_invalid parameters";
					} else {
						response = users.login(parts[1], parts[2]);
						if(response.startsWith("Success"))
							this.username = parts[1];
						response = "login_" + response;
					}
					
				}
				else if(command.equals("logout")) {
					
					if(parts.length != 1) {
						response = "logout_invalid parameters";
					} else if(username == null) {
						response = "logout_User must be logged in.";
					} else {
						response = users.logout(username);
						if(response.startsWith("Success"))
							this.username = null;
						response = "logout_" + response;
					}
					
				}
				else if(command.equals("send")) {
					
					if(parts.length != 2) {
						response = "send_invalid parameters";
					} else if(username == null) {
						response = "send_User must be logged in.";
					} else {
						isSender = true;
						TcpListener.sendMessageToClients(parts[1]);
						response = "send_Message was successfully send.";
					}
					
				}
				else if(command.equals("register")) {
					
					if(parts.length != 2) {
						response = "register_invalid parameters";
					} else if(username == null) {
						response = "register_User must be logged in.";
					} else {
						//response = "register_" + users.registerPort(username, parts[1]);
						
						nameserver.registerUser(username, parts[1]);
						response = "Successfully registerd address for " + username;						
					}
					
				}
				else if(command.equals("lookup")) {
					
					if(parts.length != 2) {
						response = "lookup_invalid parameters";
					} else if(username == null) {
						response = "lookup_User must be logged in.";
					} else {
						//response = "lookup_" + users.lookUpPort(parts[1]);
						
						String[] domain = parts[1].split(".");
						
						INameserverForChatserver server = nameserver.getNameserver(domain[domain.length-1]);
						for(int i = domain.length-2; i > 0; i--) {
							server = server.getNameserver(domain[i]);
						}
						
						response = server.lookup(domain[0]);
					}
				} else {
					response = "Unknown command.";
				}
				
				
				// print request
				writer.println(response);
			}
		} catch(IOException e) {
			
		} catch (AlreadyRegisteredException e) {
			e.printStackTrace();
		} catch (InvalidDomainException e) {
			e.printStackTrace();
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
			writer.println("public_" + message);
		else
			isSender = false;
	}
}
