package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import channel.AESreader;

public class ServerResponseListenerAES extends Thread{

	private Client client;
	private AESreader responseReader;
	private ConcurrentHashMap<String, String> responseMap;
	
	public ServerResponseListenerAES(Client client, AESreader responseReader) {
		this.client = client;
		this.responseReader = responseReader;
		responseMap = new ConcurrentHashMap<String, String>();
	}
	
	public void run() {
		while(true) {
			try {
				//if(responseReader.ready()) {
				if (true) {
					String response = responseReader.readLine();
					String[] parts = response.split("_");
					String command = parts[0];
					System.out.println("RESPONSE: " + response);
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
			} catch (IOException | IllegalBlockSizeException | BadPaddingException e) {
				// TODO
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	
	public void close() {
		try {
			responseReader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
