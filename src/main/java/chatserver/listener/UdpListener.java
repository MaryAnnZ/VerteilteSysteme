package chatserver.listener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import chatserver.UserMap;

/**
 * Thread to listen for incoming data packets on the given socket.
 */
public class UdpListener extends Thread {

	private DatagramSocket datagramSocket;
	private UserMap users;

	public UdpListener(DatagramSocket datagramSocket, UserMap users) {
		this.datagramSocket = datagramSocket;
		this.users = users;
	}
	
	@Override
	public void run() {

		byte[] buffer;
		DatagramPacket packet;
		try {
			while (true) {
				buffer = new byte[1024];
				// create a datagram packet of specified length (buffer.length)
				/*
				 * Keep in mind that: in UDP, packet delivery is not
				 * guaranteed,and the order of the delivery/processing is not
				 * guaranteed
				 */
				packet = new DatagramPacket(buffer, buffer.length);

				// wait for incoming packets from client
				datagramSocket.receive(packet);
				// get the data from the packet
				String request = new String(packet.getData());

				//System.out.println("Received request-packet from client: " + request);

				// check if request has the correct format:
				// !ping <client-name>
				//String[] parts = request.split("\\s");

				String response = "!error provided message does not fit the expected format: !list";

				if (request.startsWith("!list")) {
					response = users.listOnlineUsers();
				}
				
				// get the address of the sender (client) from the received
				// packet
				InetAddress address = packet.getAddress();
				// get the port of the sender from the received packet
				int port = packet.getPort();
				buffer = response.getBytes();
				/*
				 * create a new datagram packet, and write the response bytes,
				 * at specified address and port. the packet contains all the
				 * needed information for routing.
				 */
				packet = new DatagramPacket(buffer, buffer.length, address,
						port);
				// finally send the packet
				datagramSocket.send(packet);
			}

		} catch (IOException e) {
			//System.err.println("Error occurred while waiting for/handling packets: "
				//			+ e.getMessage());
		} finally {
			if (datagramSocket != null && !datagramSocket.isClosed())
				datagramSocket.close();
		}

	}
}
