package channel;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

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

	public synchronized void println(String msg) throws IllegalBlockSizeException, BadPaddingException {
//		String[] splitted = msg.split("___");
		String response = "";
//		for (int i = 0; i < splitted.length; i++) {
//			byte[] encoded = Base64.encode(splitted[i].getBytes());
//			response += new String(encoded) + "___";
//		}
		System.out.println("RSA writer beforeRSA: " + response);
		byte[] rsaMsg = cipherRSA.doFinal(msg.getBytes());
		byte[] encodedMsg = Base64.encode(rsaMsg);
		System.out.println("RSA writer afterRSA: " + new String(encodedMsg) + " " + new String(encodedMsg).length());
		printWriter.println(new String(encodedMsg));
	}

	public void close() {
		printWriter.close();
	}

}
