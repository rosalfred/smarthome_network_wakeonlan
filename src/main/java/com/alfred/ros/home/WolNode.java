/**
 * This file is part of the Alfred package.
 *
 * (c) Mickael Gaillard <mick.gaillard@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */
package com.alfred.ros.home;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;

/**
 *
 * @author Mickael Gaillard <mick.gaillard@gmail.com>
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 *
 */
public class WolNode extends AbstractNodeMain
						implements MessageListener<std_msgs.String> {

	private static final String SUB_CMD = "wol";
	private static final int PORT = 9;

	private transient Subscriber<std_msgs.String> subscriberWol;

	private transient String prefix;
	private transient ConnectedNode connectedNode;
	private transient String network;

	@Override
	public GraphName getDefaultNodeName() {
		return GraphName.of("/system_wol");
	}

	@Override
	public void onStart(ConnectedNode connectedNode) {
		super.onStart(connectedNode);
		this.connectedNode = connectedNode;

		this.loadParameters();

		this.subscriberWol = this.connectedNode.newSubscriber(
						SUB_CMD,
						std_msgs.String._TYPE);
		this.subscriberWol.addMessageListener(this);
	}

	@Override
	public void onNewMessage(std_msgs.String msg) {
		try {
			byte[] macBytes = getMacBytes(msg.getData());
			byte[] bytes = new byte[6 + 16 * macBytes.length];
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) 0xff;
			}
			for (int i = 6; i < bytes.length; i += macBytes.length) {
				System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
			}

			InetAddress address = InetAddress.getByName(this.network);
			DatagramPacket packet = new DatagramPacket(
					bytes,
					bytes.length,
					address,
					PORT);
			DatagramSocket socket = new DatagramSocket();
			socket.send(packet);
			socket.close();

			this.connectedNode.getLog().info("Wake-on-LAN packet sent. For : " + msg.getData());
		} catch (Exception e) {
			this.connectedNode.getLog().error("Failed to send Wake-on-LAN packet: " + e.getMessage() );
		}
	}

	/**
	 * Load parameters of node
	 */
	private void loadParameters() {
		this.prefix = this.connectedNode.getParameterTree()
				.getString("~tf_prefix", "");

		if (!this.prefix.equals("")) {
		    this.prefix = String.format("/%s/", this.prefix);
		}

		this.network = this.connectedNode.getParameterTree()
				.getString("~network", "255.255.255.255");
	}

	private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
		byte[] bytes = new byte[6];
		String[] hex = macStr.split("(\\:|\\-)");
		if (hex.length != 6) {
			throw new IllegalArgumentException("Invalid MAC address.");
		}
		try {
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) Integer.parseInt(hex[i], 16);
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Invalid hex digit in MAC address.");
		}
		return bytes;
	}

}
