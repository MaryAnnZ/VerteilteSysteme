package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import channel.RSAreader;

public class ServerResponseListenerRSA extends Thread{

	private Client client;
	private RSAreader responseReader;
	private ConcurrentHashMap<String, String> responseMap;
	
	public ServerResponseListenerRSA(Client client, RSAreader responseReader) {
		this.client = client;
		this.responseReader = responseReader;
		responseMap = new ConcurrentHashMap<String, String>();
	}
	
	public void run() {
		while(true) {
			try {
				if(responseReader.ready()) {
					System.out.println("Im here");
					String response = responseReader.readLine();
					String[] parts = response.split(" ");
					String command = parts[0];
					
					switch (command) {
						case "authenticate":
							responseMap.put("authenticate", response.substring(parts[0].length() + 1)); break;
						default: break;
						}
					
					//System.out.println(command + " - " + response.substring(parts[0].length() + 1));
				}
			} catch (IOException | IllegalBlockSizeException | BadPaddingException e) {
				// TODO
			}
		}
	}

	
	public synchronized String getResponse(String command) {
		String response = null;
		System.out.println("The size is: " + responseMap.size());
		for (String k : responseMap.keySet()) {
			System.out.println("The keys are:");
			System.out.println(k);
		}
		if(responseMap.containsKey(command)) {
			response = responseMap.get(command);
			responseMap.remove(command);
		}
		
		return response;
	}
}
