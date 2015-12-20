package chatserver.listener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;

import chatserver.UserMap;
import chatserver.handler.UdpConnection;

/**
 * Thread to listen for incoming data packets on the given socket.
 */
public class UdpListener extends Thread {

	private DatagramSocket datagramSocket;
	private UserMap users;
	private final ExecutorService threadPool;

	public UdpListener(DatagramSocket datagramSocket, UserMap users, ExecutorService threadPool) {
		this.datagramSocket = datagramSocket;
		this.users = users;
		this.threadPool = threadPool;
	}
	
	@Override
	public void run() {

		byte[] buffer;
		DatagramPacket packet;
		try {
			while (true) {
				buffer = new byte[1024];
				
				packet = new DatagramPacket(buffer, buffer.length);

				// wait for incoming packets from client
				datagramSocket.receive(packet);
				
				// new UdpConnection
				UdpConnection connection = new UdpConnection(packet, users);
				
				threadPool.execute(connection);
				
			}

		} catch (IOException e) {
			//System.err.println("Error occurred while waiting for/handling packets: "
				//			+ e.getMessage());
		} finally {
			if (datagramSocket != null && !datagramSocket.isClosed())
				datagramSocket.close();
			threadPool.shutdown();
		}

	}
}
