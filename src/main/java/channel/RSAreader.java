package channel;

import java.io.BufferedReader;
import java.io.IOException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.bouncycastle.util.encoders.Base64;

public class RSAreader {
	
	private BufferedReader bufferedReader;
	private Cipher cipherRSA;
	
	public RSAreader(BufferedReader bufferedReader, Cipher cipherRSA) {
		this.bufferedReader = bufferedReader;
		this.cipherRSA = cipherRSA;
	}
	
	public boolean ready() throws IOException {
		return bufferedReader.ready();
	}
	
	public String readLine() throws IOException, IllegalBlockSizeException, BadPaddingException {
		String response = "";
		String msg = bufferedReader.readLine();
		System.out.println(msg);
		byte[] decB64msg = Base64.decode(msg.getBytes());
		System.out.println(decB64msg.toString());
		System.out.println(new String(decB64msg));
		byte[] decRSAmsg = cipherRSA.doFinal(decB64msg);
		String stringMsg = new String(decRSAmsg);
		String[] splitted = stringMsg.split(" ");
		response = splitted[0] + " ";
		for (int i = 1; i < splitted.length; i++) {
			byte[] decoded = Base64.decode(splitted[i]);
			response += new String(decoded) + " ";
		}
		return response;
	}

	public void close() throws IOException {
		bufferedReader.close();
	}
}
