package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import chatserver.handler.ClientHandler;

public class PrivateMessageListener extends Thread {
	
	private Client client;
	private ServerSocket serverSocket;
	
	public PrivateMessageListener(Client client, ServerSocket serverSocket) {
		this.client = client;
		this.serverSocket = serverSocket;
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
				String message = reader.readLine();
					// print request
					writer.println("!ack");
					
					client.writePrivateMessage(message);
					break;

			} catch (IOException e) {
				/*System.err
						.println("Error occurred while waiting for/communicating with client: "
								+ e.getMessage());*/
				break;
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
