package chatserver.handler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import chatserver.UserMap;

public class UdpConnection implements Runnable {
	
	private DatagramPacket packet;
	private UserMap users;
	
	public UdpConnection(DatagramPacket packet, UserMap users) {
		this.packet = packet;
		this.users = users;
	}

	@Override
	public void run() {
		
		DatagramSocket socket = null;
		byte[] buffer = new byte[1024];
		
		try {
			socket = new DatagramSocket();
			
			// get the data from the packet
			String request = new String(packet.getData());

			String response = "!error provided message does not fit the expected format: !list";

			if (request.startsWith("!list")) {
				response = users.listOnlineUsers();
			}
			
			// get the address of the sender (client) from the received packet
			InetAddress address = packet.getAddress();
			
			// get the port of the sender from the received packet
			int port = packet.getPort();
			buffer = response.getBytes();
			
			packet = new DatagramPacket(buffer, buffer.length, address, port);
			
			// finally send the packet
			socket.send(packet);
			
        } catch (IOException e) {
        	 if (socket == null || !socket.isClosed()) {
                 socket.close();
             }
        }
    }

}
