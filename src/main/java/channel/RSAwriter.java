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
		//encode challenge
		if (splitted.length == 3) {
			byte[] encodedChallange = Base64.encode(splitted[2].getBytes());
			String msgToEncode = splitted[1] + " " + encodedChallange;
			byte[] rsaMsg = cipherRSA.doFinal(msg.getBytes());
			byte[] encodedMsg = Base64.decode(rsaMsg);
			printWriter.println(splitted[0] + " " + encodedMsg);
		} else if (splitted.length == 5) {
			
		}		
	}
	
	public void close() {
		printWriter.close();
	}

}
