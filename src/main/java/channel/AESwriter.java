package channel;

import java.io.PrintWriter;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.bouncycastle.util.encoders.Base64;

public class AESwriter {

	private PrintWriter printWriter;
	private Cipher cipherAES;
	
	public AESwriter(PrintWriter printWriter, Cipher cipherARS) {
		this.printWriter = printWriter;
		this.cipherAES = cipherARS;
	}
	
	public void println(String msg) throws IllegalBlockSizeException, BadPaddingException {
		String[] splitted = msg.split(" ");
		String encodedMsg = splitted[0] + " ";
		for (int i = 1; i < splitted.length; i++) {
			encodedMsg += Base64.encode(splitted[i].getBytes()) + " ";
		}
		byte[] aesMsg = cipherAES.doFinal(encodedMsg.getBytes());
		byte[] base64msg = Base64.encode(aesMsg);
		printWriter.println(base64msg);
	}
	
	public void close() {
		printWriter.close();
	}
}
