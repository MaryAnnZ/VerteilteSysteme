package channel;

import java.io.PrintWriter;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.bouncycastle.util.encoders.Base64;

public class RSAwriter {

	private PrintWriter printWriter;
	private Cipher cipherRSA;

	public RSAwriter(PrintWriter printWriter, Cipher cipherRSA) {
		this.printWriter = printWriter;
		this.cipherRSA = cipherRSA;
	}

	public void println(String msg) throws IllegalBlockSizeException, BadPaddingException {
		String[] splitted = msg.split(" ");
		String response = splitted[0] + " ";
		for (int i = 1; i < splitted.length; i++) {
			byte[] encoded = Base64.encode(splitted[i].getBytes());
			response += encoded + " ";
		}
		byte[] rsaMsg = cipherRSA.doFinal(response.getBytes());
		byte[] encodedMsg = Base64.encode(rsaMsg);
		printWriter.println(encodedMsg);	
	}

	public void close() {
		printWriter.close();
	}

}
