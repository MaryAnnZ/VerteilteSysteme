package channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
	
	public synchronized String readLine() throws IOException, IllegalBlockSizeException, BadPaddingException {
		String response = "";
		String msg = bufferedReader.readLine();
		if(msg == null) return null;
		System.out.println("RSA reader Before RSA: " + msg + " " + msg.length());
		byte[] decB64msg = Base64.decode(msg);		
		byte[] decRSAmsg = cipherRSA.doFinal(decB64msg);
		String stringMsg = new String(decRSAmsg);
		System.out.println("RSA reader after RSA: " + stringMsg);
//		String[] splitted2 = stringMsg.split("___");
//		response = "";
//		for (int i = 0; i < splitted2.length; i++) {
//			byte[] decoded = Base64.decode(splitted2[i]);
//			response += new String(decoded) + "___";
//		}
		return stringMsg;
	}

	public void close() throws IOException {
		bufferedReader.close();
	}
}
