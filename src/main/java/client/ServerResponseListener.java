package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ServerResponseListener extends Thread{

	private Client client;
	private BufferedReader responseReader;
	private ConcurrentHashMap<String, String> responseMap;
	
	public ServerResponseListener(Client client, BufferedReader responseReader) {
		this.client = client;
		this.responseReader = responseReader;
		responseMap = new ConcurrentHashMap<String, String>();
	}
	
	public void run() {
		while(true) {
			try {
				if(responseReader.ready()) {
					String response = responseReader.readLine();
					
					String[] parts = response.split("_");
					String command = parts[0];
					
					switch (command) {
					
						case "login":
							responseMap.put("login", response.substring(parts[0].length() + 1)); break;
							
						case "logout":
							responseMap.put("logout", response.substring(parts[0].length() + 1)); break;
							
						case "send":
							responseMap.put("send", response.substring(parts[0].length() + 1)); break;
							
						case "public":
							client.writePublicMessage(response.substring(parts[0].length() + 1)); break;
							
						case "register":
							responseMap.put("register", response.substring(parts[0].length() + 1)); break;
	
						case "lookup":
							responseMap.put("lookup", response.substring(parts[0].length() + 1)); break;	
							
						default: break;
						}
					
					//System.out.println(command + " - " + response.substring(parts[0].length() + 1));
				}
			} catch (IOException e) {
				// TODO
			}
		}
	}

	
	public synchronized String getResponse(String command) {
		String response = null;
		
		if(responseMap.containsKey(command)) {
			response = responseMap.get(command);
			responseMap.remove(command);
		}
		
		return response;
	}
}
