package channel;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.bouncycastle.util.encoders.Base64;

public class AESwriter {

	private PrintWriter printWriter;
	private Cipher cipherAES;
	private SecretKey secretKey;
	private IvParameterSpec ivParameterSpec;
	
	public AESwriter(PrintWriter printWriter, Cipher cipherAES, SecretKey secretKey, IvParameterSpec ivParameter) {
		this.printWriter = printWriter;
		this.cipherAES = cipherAES;
		this.secretKey = secretKey;
		this.ivParameterSpec = ivParameter;
	}
	
	public synchronized void println(String msg) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		//System.out.println("Msg to send AES: " + msg);
		cipherAES.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
		byte[] aesMsg = cipherAES.doFinal(msg.getBytes());
		byte[] base64msg = Base64.encode(aesMsg);
		
		printWriter.println(new String(base64msg));
	}
	
	public void close() {
		printWriter.close();
	}
}
