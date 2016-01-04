package channel;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.bouncycastle.util.encoders.Base64;

public class AESwriter {

	private PrintWriter printWriter;
	private Cipher cipherAES;
	
	public AESwriter(PrintWriter printWriter, Cipher cipherAES) {
		this.printWriter = printWriter;
		this.cipherAES = cipherAES;
	}
	
	public synchronized void println(String msg) throws IllegalBlockSizeException, BadPaddingException {
		System.out.println("AES writer: " + msg);
//		String[] splitted = msg.split("___");
//		String encodedMsg = "";
//		for (int i = 0; i < splitted.length; i++) {
//			encodedMsg += new String(Base64.encode(splitted[i].getBytes())) + "___";
//		}
		byte[] aesMsg = cipherAES.doFinal(msg.getBytes());
		byte[] base64msg = Base64.encode(aesMsg);
		
		/**/
//		String test = new String(base64msg);
//		String response = "";
//		byte[] decB4msg = Base64.decode(test.getBytes());
//		byte[] decAESmsg = cipherAES.doFinal(decB4msg);
//		String stringMsg = new String(decAESmsg);
//		String[] splitted2 = stringMsg.split(" ");
//		for (int i = 0; i < splitted2.length; i++) {
//			byte[] decoded = Base64.decode(splitted2[i].getBytes());
//			response += new String(decoded) + " ";
//		}
//		System.out.println("The encoded msg is: " + response);
		/**/
		System.out.println("AES writer after AES: " + new String(base64msg));
		printWriter.println(new String(base64msg));
	}
	
	public void close() {
		printWriter.close();
	}
}
