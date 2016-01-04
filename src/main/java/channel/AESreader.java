package channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

	public synchronized String readLine() throws IOException, IllegalBlockSizeException, BadPaddingException {
		String response = "";
		String msg = bufferedReader.readLine();
		if (msg == null) return null;
		byte[] decB4msg = Base64.decode(msg.getBytes());
		byte[] decAESmsg = cipherAES.doFinal(decB4msg);
//		String stringMsg = new String(decAESmsg, StandardCharsets.UTF_8);
//		String[] splitted2 = stringMsg.split("___");
//		for (int i = 0; i < splitted2.length; i++) {
//			byte[] bytes = splitted2[i].getBytes();
//			byte[] decoded = Base64.decode(bytes);
//			response += new String(decoded) + "___";
//		}
		System.out.println("The AES reader: " + new String(decAESmsg));
		//		System.out.println("AES msg read: " + response);
		return new String(decAESmsg);
	}

	public void close() throws IOException {
		bufferedReader.close();
	}

}
