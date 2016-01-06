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
		String msg = bufferedReader.readLine();
		if(msg == null) return null;
		byte[] decB64msg = Base64.decode(msg);		
		byte[] decRSAmsg = cipherRSA.doFinal(decB64msg);
		String stringMsg = new String(decRSAmsg);
		return stringMsg;
	}

	public void close() throws IOException {
		bufferedReader.close();
	}
}
