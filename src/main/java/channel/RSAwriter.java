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
		byte[] rsaMsg = cipherRSA.doFinal(msg.getBytes());
		byte[] encodedMsg = Base64.encode(rsaMsg);
		printWriter.println(new String(encodedMsg));
	}

	public void close() {
		printWriter.close();
	}

}
