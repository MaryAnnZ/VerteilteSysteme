package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
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
		while (true) {
			try {
				if(responseReader.ready()) {
					String response;
					while ((response = responseReader.readLine()) != null) {
						//System.out.println(response);
						String[] parts = response.split("___");
						String command = parts[0];

						switch (command) {
						case "!ok":
							responseMap.put("authenticate", response);
							break;
						default: break;
						}
						client.handleServerResponseAut();
						//System.out.println(command + " - " + response.substring(parts[0].length() + 1));
					}
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
