package channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

public class AESreader {

	private BufferedReader bufferedReader;
	private SecretKey secretKey;
	private IvParameterSpec ivParameter;
	private Cipher cipherAES;

	public AESreader(BufferedReader bufferedReader, Cipher cipherAES, SecretKey secretKey, IvParameterSpec ivParameter) {
		this.bufferedReader = bufferedReader;
		this.secretKey = secretKey;
		this.ivParameter = ivParameter;
		this.cipherAES = cipherAES;
	}

	public boolean ready() throws IOException {
		return bufferedReader.ready();
	}

	public synchronized String readLine() throws IOException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		String response = "";
		cipherAES.init(Cipher.DECRYPT_MODE, secretKey, ivParameter);		
		String msg = bufferedReader.readLine();
		if (msg == null) return null;
		byte[] decB4msg = Base64.decode(msg.getBytes());
		byte[] decAESmsg = cipherAES.doFinal(decB4msg);
		System.out.println("AES reading " + new String(decAESmsg));
		return new String(decAESmsg);
	}

	public void close() throws IOException {
		bufferedReader.close();
	}

}
