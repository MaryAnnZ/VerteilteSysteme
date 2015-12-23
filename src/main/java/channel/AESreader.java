package channel;

import java.io.BufferedReader;
import java.io.IOException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.bouncycastle.util.encoders.Base64;

public class AESreader {
	
	private BufferedReader bufferedReader;
	private Cipher cipherAES;
	
	public AESreader(BufferedReader bufferedReader, Cipher cipherAES) {
		this.bufferedReader = bufferedReader;
		this.cipherAES = cipherAES;
	}
	
	public boolean ready() throws IOException {
		return bufferedReader.ready();
	}
	
	public String readLine() throws IOException, IllegalBlockSizeException, BadPaddingException {
		String response = "";
		String msg = bufferedReader.readLine();
		byte[] decB4msg = Base64.decode(msg.getBytes());
		byte[] decAESmsg = cipherAES.doFinal(decB4msg);
		String stringMsg = new String(decAESmsg);
		String[] splitted = stringMsg.split(" ");
		response = splitted[0] + " ";
		for (int i = 0; i < splitted.length; i++) {
			byte[] decoded = Base64.decode(splitted[i].getBytes());
			response += new String(decoded) + " ";
		}
		return response;
	}
	
	public void close() throws IOException {
		bufferedReader.close();
	}

}
